package fr.minobot.core.input;

import fr.minobot.core.FocusManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Optional;

/**
 * Simulates the user's keyboard and mouse — the counterpart of {@code input_simulator.py}.
 *
 * <p>{@link Robot} goes through {@code SendInput}, which is what the
 * ALT-press trick in {@link FocusManager} relies on to unlock {@code SetForegroundWindow}. Keys are
 * named by their {@link KeyEvent} virtual key code rather than by a string, so a typo is a compile
 * error instead of a silent no-op.
 */
public final class InputSimulator implements Input {

    private static final Logger log = LoggerFactory.getLogger(InputSimulator.class);

    /** Matches {@code pyautogui.PAUSE = 0.01}: games drop inputs delivered faster than they poll. */
    private static final int AUTO_DELAY_MILLIS = 10;

    /** Time left for the target window to service its WM_PASTE before the clipboard is restored. */
    private static final int PASTE_SETTLE_MILLIS = 60;

    private static final int CLIPBOARD_ATTEMPTS = 5;
    private static final int CLIPBOARD_RETRY_MILLIS = 20;

    private final Robot robot;

    public InputSimulator() {
        try {
            robot = new Robot();
            robot.setAutoDelay(AUTO_DELAY_MILLIS);
        } catch (AWTException e) {
            throw new IllegalStateException("Cannot simulate input on this display", e);
        }
    }

    @Override
    public void pressKey(int keyCode) {
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
    }

    /**
     * Types a string one key at a time.
     *
     * <p>Slow, and bound to the active keyboard layout — a character the layout cannot reach without
     * a dead key or AltGr is skipped. It is the fallback for {@link #pasteString(String)}, never the
     * first choice.
     */
    @Override
    public void typeString(String text) {
        log.debug("Typing string: '{}'", text);
        for (final var character : text.toCharArray()) {
            final var keyCode = KeyEvent.getExtendedKeyCodeForChar(character);
            if (keyCode == KeyEvent.VK_UNDEFINED) {
                log.warn("Cannot type character '{}' on this keyboard layout, skipping it.", character);
                continue;
            }
            final var needsShift = Character.isUpperCase(character);
            try {
                if (needsShift) {
                    robot.keyPress(KeyEvent.VK_SHIFT);
                }
                robot.keyPress(keyCode);
                robot.keyRelease(keyCode);
            } catch (IllegalArgumentException e) {
                log.warn("Keyboard layout rejected character '{}', skipping it.", character);
            } finally {
                if (needsShift) {
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                }
            }
        }
    }

    /**
     * Pastes a string through the clipboard (Ctrl+V), restoring whatever was in it.
     *
     * <p>Far faster than typing, and layout-independent — which is what makes {@code /invite Name}
     * arrive intact on an AZERTY keyboard.
     */
    @Override
    public void pasteString(String text) {
        log.debug("Pasting string: '{}'", text);
        try {
            final var original = clipboardText();

            setClipboardText(text);

            robot.keyPress(KeyEvent.VK_CONTROL);
            pressKey(KeyEvent.VK_V);
            robot.keyRelease(KeyEvent.VK_CONTROL);

            // The paste is asynchronous: the game reads the clipboard when it handles the message,
            // which is after keyRelease returns. Restoring it immediately can hand it the old text.
            sleep(PASTE_SETTLE_MILLIS);

            original.ifPresent(this::setClipboardText);
        } catch (RuntimeException e) {
            log.error("Failed to paste text, falling back to typing it.", e);
            typeString(text);
        }
    }

    @Override
    public void click(int x, int y) {
        log.debug("Clicking at position: ({}, {})", x, y);
        robot.mouseMove(x, y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
    }

    private static Clipboard clipboard() {
        return Toolkit.getDefaultToolkit().getSystemClipboard();
    }

    /**
     * Reads the clipboard, retrying while another application holds it open.
     *
     * <p>Windows grants the clipboard to one process at a time; a browser or a password manager
     * polling it makes {@code getData} throw. Empty means "could not read it", in which case we
     * simply do not restore it.
     */
    private Optional<String> clipboardText() {
        for (var attempt = 0; attempt < CLIPBOARD_ATTEMPTS; attempt++) {
            try {
                final var clipboard = clipboard();
                if (!clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                    return Optional.empty(); // holds an image or nothing: not ours to restore
                }
                return Optional.of((String) clipboard.getData(DataFlavor.stringFlavor));
            } catch (IllegalStateException e) {
                sleep(CLIPBOARD_RETRY_MILLIS);
            } catch (Exception e) {
                log.debug("Could not read the clipboard: {}", e.getMessage());
                return Optional.empty();
            }
        }
        log.debug("Clipboard stayed locked by another application; its content will not be restored.");
        return Optional.empty();
    }

    private void setClipboardText(String text) {
        final var selection = new StringSelection(text);
        for (var attempt = 0; attempt < CLIPBOARD_ATTEMPTS; attempt++) {
            try {
                clipboard().setContents(selection, selection);
                return;
            } catch (IllegalStateException e) {
                sleep(CLIPBOARD_RETRY_MILLIS);
            }
        }
        throw new IllegalStateException("Clipboard is locked by another application");
    }

    private static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
