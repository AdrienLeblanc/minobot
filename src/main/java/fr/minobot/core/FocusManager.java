package fr.minobot.core;

import fr.minobot.core.input.Input;
import fr.minobot.win32.Win32;
import fr.minobot.win32.WindowApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.KeyEvent;
import java.time.Duration;

/**
 * Brings a game window to the foreground — the counterpart of {@code focus_manager.py}.
 *
 * <p>Windows refuses {@code SetForegroundWindow} to a process that does not already own the
 * foreground. The workaround, is to press ALT first: it makes the
 * OS treat the call as user-driven. It is timing- and policy-dependent, which is why it is the first
 * thing to check against the real game.
 *
 * <p>Every step sleeps, so this is meant to be called from a virtual thread (a hotkey callback),
 * never from the polling loop.
 */
public final class FocusManager {

    private static final Logger log = LoggerFactory.getLogger(FocusManager.class);

    private static final int ALT_SETTLE_MILLIS = 50;
    private static final int RESTORE_SETTLE_MILLIS = 100;
    private static final int FOREGROUND_SETTLE_MILLIS = 100;

    /** Two focus requests landing together — a burst of toasts — must not fight over the screen. */
    private static final Duration COOLDOWN = Duration.ofMillis(100);

    /** How recently the player must have typed for a smart focus to leave their window alone. */
    private static final Duration TYPING_THRESHOLD = Duration.ofSeconds(2);

    private final WindowApi api;
    private final Input input;
    private final WindowManager windows;
    private final KeyboardMonitor keyboard;

    private final Object cooldownLock = new Object();
    private long lastFocusNanos;
    private boolean everFocused;

    public FocusManager(WindowApi api, Input input, WindowManager windows, KeyboardMonitor keyboard) {
        this.api = api;
        this.input = input;
        this.windows = windows;
        this.keyboard = keyboard;
    }

    public void focus(long hwnd) {
        focus(hwnd, false, false);
    }

    /**
     * @param smart skip the focus if the user is currently typing, so we never steal the keystrokes
     *              they are aiming at another window
     * @param force bypass both the smart check and the cooldown
     */
    public void focus(long hwnd, boolean smart, boolean force) {
        if (!force && !claimFocus(hwnd, smart)) {
            return;
        }

        final var title = windows.extractCharacterName(api.windowText(hwnd));
        log.debug("Attempting to focus window: '{}' (HWND: {})", title, hwnd);

        try {
            // 1. Wake up the OS's focus logic; without this, step 4 is silently refused.
            input.pressKey(KeyEvent.VK_ALT);
            Thread.sleep(ALT_SETTLE_MILLIS);

            // 2. Un-minimize it, otherwise it comes back to the foreground still iconic.
            if (api.isIconic(hwnd)) {
                api.showWindow(hwnd, Win32.SW_RESTORE);
                Thread.sleep(RESTORE_SETTLE_MILLIS);
            }

            // 3. and 4. Raise it, then give it the focus.
            api.bringWindowToTop(hwnd);
            api.setForegroundWindow(hwnd);

            // 5. Verify — SetForegroundWindow reports success it did not always achieve.
            Thread.sleep(FOREGROUND_SETTLE_MILLIS);
            verify(hwnd, title);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Focus of '{}' interrupted.", title);
        } catch (RuntimeException e) {
            log.error("Unexpected error while focusing '{}'.", title, e);
        }
    }

    private void verify(long hwnd, String title) {
        if (api.foregroundWindow() == hwnd) {
            log.debug("Successfully focused '{}'.", title);
            return;
        }

        log.warn("Focus on '{}' failed on first attempt, retrying...", title);
        api.showWindow(hwnd, Win32.SW_SHOW);

        if (api.foregroundWindow() == hwnd) {
            log.debug("Successfully focused '{}' on second attempt.", title);
        } else {
            log.error("Could not focus '{}'.", title);
        }
    }

    /**
     * Decides whether this focus request goes ahead, and records it if so.
     *
     * <p>Synchronized because hotkey callbacks run concurrently on virtual threads: without it, two
     * requests arriving together would both see a stale timestamp and both pass the cooldown.
     */
    private boolean claimFocus(long hwnd, boolean smart) {
        if (smart && keyboard != null && keyboard.typedWithin(TYPING_THRESHOLD)) {
            log.debug("Smart focus for HWND {} skipped (recent user activity detected).", hwnd);
            return false;
        }

        synchronized (cooldownLock) {
            final var now = System.nanoTime();
            if (everFocused && now - lastFocusNanos < COOLDOWN.toNanos()) {
                log.debug("Focus attempt on HWND {} skipped due to cooldown.", hwnd);
                return false;
            }
            lastFocusNanos = now;
            everFocused = true;
            return true;
        }
    }
}
