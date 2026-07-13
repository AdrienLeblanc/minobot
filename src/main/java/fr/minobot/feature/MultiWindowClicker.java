package fr.minobot.feature;

import fr.minobot.app.Config;
import fr.minobot.core.FocusManager;
import fr.minobot.core.domain.GameWindow;
import fr.minobot.core.WindowManager;
import fr.minobot.win32.Point;
import fr.minobot.win32.Win32;
import fr.minobot.win32.WindowApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

/**
 * Replays one click on every game window at the same spot — the counterpart of
 * {@code multi_window_clicker.py}.
 *
 * <p>The click is <em>posted</em>, not simulated: {@code PostMessage} drops it straight into each
 * window's queue, so the other characters move without the windows ever taking the focus. Which is
 * the whole point — the player keeps playing their main character, uninterrupted.
 *
 * <p>The position is translated through the <em>client</em> area, not the screen: the same client
 * coordinates land on the same in-game spot in every window, whatever their position on the desktop.
 */
public final class MultiWindowClicker {

    private static final Logger log = LoggerFactory.getLogger(MultiWindowClicker.class);

    /**
     * A posted click can be refused by a window that is busy; one retry is enough in practice.
     */
    private static final int CLICK_ATTEMPTS = 2;
    private static final int RETRY_MILLIS = 5;

    /**
     * How far up the parent chain we look for the game window under the cursor.
     */
    private static final int MAX_PARENT_DEPTH = 10;

    /**
     * Time given to a window to settle after being focused, in the attention-reset sequence.
     */
    private static final int RESET_SETTLE_MILLIS = 50;

    private final WindowApi api;
    private final WindowManager windows;
    private final FocusManager focus;
    private final Config config;

    private final List<String> excluded;
    private final MouseButton button;

    public MultiWindowClicker(WindowApi api, WindowManager windows, FocusManager focus, Config config) {
        this.api = api;
        this.windows = windows;
        this.focus = focus;
        this.config = config;

        this.excluded = config.multiclickExclude().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
        this.button = MouseButton.of(config.multiclickButton());

        if (config.multiclickDryRun()) {
            log.warn("[MULTICLICK] Dry-run mode: no click will actually be sent.");
        }
    }

    /**
     * Clicks every game window at the position equivalent to where the player just clicked.
     *
     * @param cursor the screen position of the cursor when the hotkey went down
     */
    public void clickAllWindows(Point cursor) {

        // Read before anything else: the click must not leave the player's window behind.
        final var original = config.multiclickRestoreFocus() ? api.foregroundWindow() : Win32.NULL_HANDLE;

        final var targets = windows.orderedWindows();
        if (targets.isEmpty()) {
            log.warn("[MULTICLICK] No game window to click.");
            return;
        }

        final var sourceClient = clientPositionOf(cursor, targets);

        sourceClient.ifPresent(point -> targets.stream()
                .filter(not(this::isExcluded))
                .filter(not(window -> api.isIconic(window.hwnd())))
                .filter(not(window -> window.hwnd() == original))
                .forEach(window -> click(window, point)));

        if (original != Win32.NULL_HANDLE) {
            api.setForegroundWindow(original);
        }
    }

    /**
     * Focuses each window in turn to clear its taskbar attention state, then hands the focus back.
     *
     * <p>Posted clicks make a background window flash orange in the taskbar, and nothing clears that
     * but a genuine visit. Visually disruptive by nature — it is a manual hotkey, not automatic.
     */
    public void resetWindowsAttentionState() {
        log.info("Starting the window attention-reset sequence...");

        final var ordered = windows.orderedWindows();
        if (ordered.isEmpty()) {
            log.warn("No game window to reset.");
            return;
        }

        // Visited back to front, so the leader is reached last and is already on top by the time the
        // explicit restore below runs. This reverses the list itself rather than asking for the
        // reversed order: that sorts by rank, and windows absent from window_cycle_order all share
        // one rank — so on a default config it would hand back the same order and the "leader" would
        // be whichever window sorted last alphabetically.
        for (final var window : ordered.reversed()) {
            if (api.isIconic(window.hwnd())) {
                log.debug("Skipping the minimized window '{}'.", window.title());
                continue;
            }

            log.debug("Resetting the attention state of '{}' (HWND: {}).", window.title(), window.hwnd());
            focus.focus(window.hwnd());

            if (!sleep(RESET_SETTLE_MILLIS)) {
                return;
            }
        }

        final var leader = ordered.getFirst();
        log.debug("Restoring the focus to '{}'.", leader.title());
        focus.focus(leader.hwnd());

        log.info("Window attention-reset sequence complete.");
    }

    /**
     * The cursor's position in the client area of the game window it is over.
     *
     * <p>Empty when the player clicked outside the game — on the desktop, on a browser — and the
     * click is then replayed nowhere.
     */
    private Optional<Point> clientPositionOf(Point cursor, List<GameWindow> targets) {
        final var handles = targets.stream().map(GameWindow::hwnd).collect(Collectors.toSet());

        // The point may land on a child window (the game's render surface): walk up to the top-level.
        var window = api.windowFromPoint(cursor);
        for (var depth = 0; window != Win32.NULL_HANDLE && depth < MAX_PARENT_DEPTH; depth++) {
            if (handles.contains(window)) {
                return api.screenToClient(window, cursor);
            }
            window = api.parentWindow(window);
        }

        log.debug("[MULTICLICK] The click did not land on a game window; nothing to replay.");
        return Optional.empty();
    }

    private boolean isExcluded(GameWindow window) {
        final var title = window.title().toLowerCase(Locale.ROOT);
        return excluded.stream().anyMatch(title::contains);
    }

    private void click(GameWindow window, Point point) {
        if (!api.isWindow(window.hwnd())) {
            log.debug("[MULTICLICK] Skipping the stale window '{}'.", window.title());
            return;
        }

        if (config.multiclickDryRun()) {
            log.debug("[DRY RUN] Would click '{}' at client {}.", window.title(), point);
            return;
        }

        for (var attempt = 0; attempt < CLICK_ATTEMPTS; attempt++) {
            if (post(window.hwnd(), point)) {
                sleep((int) config.multiclickDelay().toMillis());
                return;
            }
            if (!sleep(RETRY_MILLIS)) {
                return;
            }
        }

        log.warn("[MULTICLICK] Could not click '{}' after {} attempts.", window.title(), CLICK_ATTEMPTS);
    }

    /**
     * Posts the down/up pair of the configured button.
     *
     * <p>Bracketed by {@code FlashWindowEx(FLASHW_STOP)}: the click itself is what makes an unfocused
     * window flash orange in the taskbar, so the flash is cancelled on both sides of it.
     */
    private boolean post(long hwnd, Point client) {
        final var lparam = Win32.makeLParam(client.x(), client.y());

        api.stopFlashing(hwnd);
        final var posted = api.postMessage(hwnd, button.down(), button.flag(), lparam)
                && api.postMessage(hwnd, button.up(), 0, lparam);
        api.stopFlashing(hwnd);

        return posted;
    }

    /**
     * @return whether the sleep completed; {@code false} means the thread was interrupted
     */
    private static boolean sleep(int millis) {
        if (millis <= 0) {
            return true;
        }
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * The messages of one mouse button: what to post down, up, and in the {@code wParam}.
     */
    private enum MouseButton {
        LEFT(Win32.WM_LBUTTONDOWN, Win32.WM_LBUTTONUP, Win32.MK_LBUTTON),
        RIGHT(Win32.WM_RBUTTONDOWN, Win32.WM_RBUTTONUP, Win32.MK_RBUTTON),
        MIDDLE(Win32.WM_MBUTTONDOWN, Win32.WM_MBUTTONUP, Win32.MK_MBUTTON);

        private final int down;
        private final int up;
        private final int flag;

        MouseButton(int down, int up, int flag) {
            this.down = down;
            this.up = up;
            this.flag = flag;
        }

        static MouseButton of(String name) {
            try {
                return valueOf(name.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException _) {
                log.warn("Unknown multiclick_button '{}', falling back to the left button.", name);
                return LEFT;
            }
        }

        int down() {
            return down;
        }

        int up() {
            return up;
        }

        int flag() {
            return flag;
        }
    }
}
