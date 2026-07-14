package fr.minobot.feature;

import fr.minobot.core.FocusManager;
import fr.minobot.core.domain.GameWindow;
import fr.minobot.core.WindowManager;
import fr.minobot.win32.Win32;
import fr.minobot.win32.WindowApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Rebuilds the taskbar order of the game windows — the counterpart of {@code window_reorder.py}.
 *
 * <p>Windows offers no way to reorder taskbar buttons; the only lever is the order in which the
 * windows appear. So the sequence hides them all, waits for the taskbar to catch up, then shows them
 * one by one in the configured order.
 *
 * <p>The windows are <em>hidden</em> in the middle of this: if the sequence dies there, they are gone
 * from the screen with no way back. Every failure path therefore ends in {@link #showEverythingAgain()}.
 */
public final class WindowReorder {

    private static final Logger log = LoggerFactory.getLogger(WindowReorder.class);

    /** Time the taskbar needs to notice the buttons are gone before we start adding them back. */
    private static final int TASKBAR_SETTLE_MILLIS = 500;

    /** Without this pause, Windows batches the windows and the order comes out arbitrary. */
    private static final int BETWEEN_WINDOWS_MILLIS = 100;

    private final WindowApi api;
    private final WindowManager windows;
    private final FocusManager focus;

    private final AtomicBoolean running = new AtomicBoolean();

    public WindowReorder(WindowApi api, WindowManager windows, FocusManager focus) {
        this.api = api;
        this.windows = windows;
        this.focus = focus;
    }

    /** Hides every game window, then shows them again in the configured order. */
    public void reorderTaskbar() {
        if (!running.compareAndSet(false, true)) {
            log.warn("The window reorder sequence is already running.");
            return;
        }

        try {
            windows.refresh();
            final var ordered = windows.orderedWindows();
            if (ordered.isEmpty()) {
                log.warn("No game window to reorder.");
                return;
            }

            log.info("Reordering {} window(s) to the configured order.", ordered.size());

            hide(ordered);
            Thread.sleep(TASKBAR_SETTLE_MILLIS);
            show(ordered);

            final var first = ordered.getFirst();
            if (api.isWindow(first.hwnd())) {
                log.info("Restoring the focus to the first window: '{}'.", first.title());
                focus.focus(first.hwnd());
            }

            log.info("Taskbar reorder complete.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("The reorder sequence was interrupted; bringing the windows back.");
            showEverythingAgain();
        } catch (RuntimeException e) {
            log.error("Error during the reorder sequence; bringing the windows back.", e);
            showEverythingAgain();
        } finally {
            running.set(false);
        }
    }

    private void hide(List<GameWindow> ordered) {
        for (final var window : ordered) {
            if (api.isWindow(window.hwnd())) {
                api.showWindow(window.hwnd(), Win32.SW_HIDE);
            }
        }
    }

    private void show(List<GameWindow> ordered) throws InterruptedException {
        for (final var window : ordered) {
            if (!api.isWindow(window.hwnd())) {
                continue;
            }

            api.showWindow(window.hwnd(), Win32.SW_SHOW);
            if (api.isIconic(window.hwnd())) {
                api.showWindow(window.hwnd(), Win32.SW_RESTORE);
            }

            Thread.sleep(BETWEEN_WINDOWS_MILLIS);
        }
    }

    /** Last resort: a window left hidden is a window the player cannot get back. */
    private void showEverythingAgain() {
        for (final var window : windows.windows()) {
            if (api.isWindow(window.hwnd())) {
                api.showWindow(window.hwnd(), Win32.SW_SHOW);
            }
        }
    }
}
