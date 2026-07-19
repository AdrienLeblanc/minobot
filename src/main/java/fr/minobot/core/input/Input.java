package fr.minobot.core.input;

/**
 * The keyboard and the mouse, behind one interface — the {@link fr.minobot.win32.WindowApi} of the
 * input side.
 *
 * <p>The real implementation, {@link InputSimulator}, needs a {@link java.awt.Robot}: constructing it
 * opens a display and its calls press real keys. Everything that drives input therefore depends on
 * this interface, so the focus sequence and the group invitation can be exercised in a test without
 * a desktop — and without stealing the developer's keyboard.
 */
public interface Input {

    /** Presses and releases a key. Use the {@code KeyEvent.VK_*} constants. */
    void pressKey(int keyCode);

    /** Types a string one key at a time; bound to the active keyboard layout. */
    void typeString(String text);

    /**
     * Pastes a string through the clipboard, restoring what was in it. Layout-independent.
     *
     * @return {@code true} if the text reached the clipboard intact and was pasted; {@code false} if
     *         another application changed the clipboard first, in which case nothing was pasted. A
     *         caller about to confirm with ENTER must stop on {@code false}, or it sends whatever
     *         replaced the text — the group relay's "/say &lt;clipboard&gt;" bug.
     */
    boolean pasteString(String text);

    /** Left-clicks at a screen position. */
    void click(int x, int y);
}
