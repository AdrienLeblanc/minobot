package fr.minobot.feature;

import fr.minobot.app.Config;
import fr.minobot.core.FlashSuppressor;
import fr.minobot.core.FocusManager;
import fr.minobot.core.WindowManager;
import fr.minobot.core.domain.GameWindow;
import fr.minobot.win32.Point;
import fr.minobot.win32.Win32;
import fr.minobot.win32.WindowApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

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
 *
 * <p><strong>A clicked character goes deaf, and there is no cure but {@code shift+x1}.</strong> The
 * game only raises its Windows toast for a character nobody is watching, and a posted click is — seen
 * from inside the game — a click like any other: it concludes the player is there and stops notifying.
 * The notification auto-focus, which lives on those toasts, dies with them. This was chased down to the
 * end: a single posted click deafens the window it hits and no other; the toast is never raised at all
 * (the Windows notification database stays empty, so there is nothing to miss); and the game cannot be
 * talked out of it. {@code WM_ACTIVATE}, {@code WM_NCACTIVATE}, {@code WM_KILLFOCUS},
 * {@code WM_ACTIVATEAPP}, a full activation/deactivation cycle, even a real {@code SetFocus} through
 * {@code AttachThreadInput} — all measured, all ignored. Only an activation that genuinely brings the
 * window up on screen re-arms it, which is precisely what {@code shift+x1} does and why it exists.
 * <strong>Do not add a "fix" here that posts messages at the game: that ground is burnt.</strong>
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

    /**
     * Breathing room between two windows: a posted click the game has not yet drained can otherwise
     * be dropped. The whole point of the feature is speed, so this is as small as it can be.
     */
    private static final int CLICK_DELAY_MILLIS = 10;

    private final WindowApi api;
    private final WindowManager windows;
    private final FocusManager focus;
    private final FlashSuppressor flash;

    private final List<String> excluded;

    public MultiWindowClicker(WindowApi api, WindowManager windows, FocusManager focus,
                              FlashSuppressor flash, Config config) {
        this.api = api;
        this.windows = windows;
        this.focus = focus;
        this.flash = flash;

        this.excluded = config.multiclickExclude().stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * Clicks every game window at the position equivalent to where the player just clicked.
     *
     * <p>The player's own window is clicked too: the trigger is a side button, not the click itself,
     * so nothing has landed there yet.
     *
     * @param cursor the screen position of the cursor when the hotkey went down
     */
    public void clickAllWindows(Point cursor) {
        final var targets = windows.orderedWindows();
        if (targets.isEmpty()) {
            log.warn("[MULTICLICK] No game window to click.");
            return;
        }

        final var sourceClient = clientPositionOf(cursor, targets);
        if (sourceClient.isEmpty()) {
            return;
        }

        final var clicked = new ArrayList<Long>();
        for (final var window : targets) {
            if (isExcluded(window) || api.isIconic(window.hwnd())) {
                continue;
            }
            if (click(window, sourceClient.get())) {
                clicked.add(window.hwnd());
            }
        }

        // Each of these windows is about to ask the game for the foreground, be refused, and turn
        // orange in the taskbar. The suppressor clears them once that has happened — on its own thread,
        // never on this one, which must stay as fast as it is.
        flash.watch(clicked);
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

        // The sequence walks the whole desktop: a toast focusing a window halfway through would leave
        // the player somewhere other than where they were playing.
        try (final var _ = focus.takeOver()) {
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
        }

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

    /** @return whether the click was posted, and the window is therefore about to turn orange */
    private boolean click(GameWindow window, Point point) {
        if (!api.isWindow(window.hwnd())) {
            log.debug("[MULTICLICK] Skipping the stale window '{}'.", window.title());
            return false;
        }

        for (var attempt = 0; attempt < CLICK_ATTEMPTS; attempt++) {
            if (post(window.hwnd(), point)) {
                sleep(CLICK_DELAY_MILLIS);
                return true;
            }
            if (!sleep(RETRY_MILLIS)) {
                return false;
            }
        }

        log.warn("[MULTICLICK] Could not click '{}' after {} attempts.", window.title(), CLICK_ATTEMPTS);
        return false;
    }

    /** Posts the down/up pair of a left click. */
    private boolean post(long hwnd, Point client) {
        final var lparam = Win32.makeLParam(client.x(), client.y());

        return api.postMessage(hwnd, Win32.WM_LBUTTONDOWN, Win32.MK_LBUTTON, lparam)
                && api.postMessage(hwnd, Win32.WM_LBUTTONUP, 0, lparam);
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
}
