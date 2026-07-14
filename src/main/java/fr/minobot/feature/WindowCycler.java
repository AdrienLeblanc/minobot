package fr.minobot.feature;

import fr.minobot.core.FocusManager;
import fr.minobot.core.WindowManager;
import fr.minobot.core.domain.GameWindow;
import fr.minobot.win32.WindowApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Cycles the focus through the game windows in the configured order — the counterpart of
 * {@code window_cycler.py}.
 *
 * <p>Only the windows of the <em>current monitor</em> take part, so on a two-screen setup each screen
 * cycles within itself. That is what makes it worth having over Alt+Tab.
 */
public final class WindowCycler {

    private static final Logger log = LoggerFactory.getLogger(WindowCycler.class);

    private final WindowApi api;
    private final WindowManager windows;
    private final FocusManager focus;

    public WindowCycler(WindowApi api, WindowManager windows, FocusManager focus) {
        this.api = api;
        this.windows = windows;
        this.focus = focus;
    }

    public void cycleNext() {
        cycle(1);
    }

    public void cyclePrev() {
        cycle(-1);
    }

    /**
     * @param step {@code +1} to move forward in the configured order, {@code -1} to move back
     */
    private void cycle(int step) {
        final var candidates = windows.windowsOnCurrentMonitor();
        if (candidates.isEmpty()) {
            log.debug("No window to cycle through on this monitor.");
            return;
        }

        final var current = indexOf(candidates, api.foregroundWindow());
        final var target = current < 0
                // The focus is on something else entirely (a browser, the desktop): enter the cycle
                // at the end we are heading towards rather than skipping the first window.
                ? (step > 0 ? 0 : candidates.size() - 1)
                : Math.floorMod(current + step, candidates.size());

        final var window = candidates.get(target);
        log.debug("Cycling {} to '{}'.", step > 0 ? "next" : "previous", window.title());
        focus.focus(window.hwnd());
    }

    private static int indexOf(List<GameWindow> candidates, long hwnd) {
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).hwnd() == hwnd) {
                return i;
            }
        }
        return -1;
    }
}
