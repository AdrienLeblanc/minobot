package fr.minobot.win32;

/**
 * A window's bounds in screen coordinates — Windows' {@code RECT}, whose right and bottom edges are
 * exclusive.
 *
 * <p>The overlay is placed with this: it is anchored to a character's window, and a window may sit on
 * any monitor, so its bounds are the only thing that says where to draw.
 */
public record Rect(int left, int top, int right, int bottom) {

    public int width() {
        return right - left;
    }

    public int height() {
        return bottom - top;
    }

    /** The middle of the window, which is where the overlay centres itself. */
    public Point center() {
        return new Point(left + width() / 2, top + height() / 2);
    }
}
