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
import java.util.concurrent.atomic.AtomicLong;

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

    /**
     * How long the clipboard is left holding the pasted text before it is restored. The game reads it
     * when it services WM_PASTE, which on a machine running several clients lags well behind the
     * keystroke; the restore must land after that read, never before it (see {@link #pasteString}).
     */
    private static final int RESTORE_DELAY_MILLIS = 300;

    private static final int CLIPBOARD_ATTEMPTS = 5;
    private static final int CLIPBOARD_RETRY_MILLIS = 20;

    private final Robot robot;

    /** Bumped by every paste; a deferred restore fires only while it still owns this number. */
    private final AtomicLong pasteSequence = new AtomicLong();

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
     *
     * <p>Two hazards, both of which have sent the player's clipboard to the chat as a {@code /say}:
     * another application overwriting our text before the game reads it, and our own restore beating
     * that read. The first is caught before pasting ({@code false} is returned, nothing is pasted);
     * the second is avoided by deferring the restore past the game's read (see {@link #restoreLater}).
     */
    @Override
    public boolean pasteString(String text) {
        log.debug("Pasting string: '{}'", text);
        final long mine = pasteSequence.incrementAndGet();

        final Optional<String> original;
        try {
            original = clipboardText();
            setClipboardText(text);
        } catch (RuntimeException e) {
            // The clipboard could not be set. typeString never touches it, so it cannot paste stale
            // content — it is the safe fallback here, not a corruption risk.
            log.error("Could not set the clipboard, typing the text instead.", e);
            typeString(text);
            return true;
        }

        // Windows grants the clipboard to one process at a time, and a clipboard manager or a
        // password manager can overwrite our text between the set above and the game's read below.
        // If our text is no longer there, pasting would send whatever replaced it — refuse instead.
        if (!clipboardHolds(text)) {
            log.warn("The clipboard no longer holds the text to paste; another application changed it.");
            original.ifPresent(this::setClipboardText);
            return false;
        }

        robot.keyPress(KeyEvent.VK_CONTROL);
        pressKey(KeyEvent.VK_V);
        robot.keyRelease(KeyEvent.VK_CONTROL);

        original.ifPresent(previous -> restoreLater(previous, mine));
        return true;
    }

    /** Whether the clipboard currently holds exactly {@code text} — the guard before a paste. */
    private boolean clipboardHolds(String text) {
        return clipboardText().map(text::equals).orElse(false);
    }

    /**
     * Restores the clipboard on a background thread, once the game has had time to read the paste.
     *
     * <p>Restoring synchronously would race the game's WM_PASTE and could hand it the old text; the
     * caller then sends it. A newer paste supersedes this one — its restore, not ours, owns the
     * clipboard then, which is what the sequence number guards.
     */
    private void restoreLater(String previous, long sequence) {
        Thread.ofVirtual().start(() -> {
            sleep(RESTORE_DELAY_MILLIS);
            if (pasteSequence.get() != sequence) {
                return;
            }
            try {
                setClipboardText(previous);
            } catch (RuntimeException e) {
                log.debug("Could not restore the clipboard: {}", e.getMessage());
            }
        });
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
