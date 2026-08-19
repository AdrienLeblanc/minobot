package fr.minobot.ui.utils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * The small drawing primitives every themed surface shares — so a shape drawn in two places is drawn by
 * one routine, and cannot drift a pixel apart between them.
 */
public final class Draw {

    /** The ellipsis a cut string ends in. One character, so the room it needs is measured once. */
    private static final String ELLIPSIS = "…";

    /** A canvas that smooths its edges and its text. Disposed by the caller, not shared with Swing's own. */
    public static Graphics2D smooth(Graphics graphics) {
        final var canvas = (Graphics2D) graphics.create();
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return canvas;
    }

    /**
     * The close/forget cross, drawn from two strokes rather than a glyph so it stays sharp at every scale.
     * Drawn in several places — the panel's close cross, a disconnected row's forget cross, a keybind's
     * clear — which is why it is one routine.
     *
     * @param size the side of the square the cross is centred in, from its top-left {@code (x, y)}
     */
    public static void cross(Graphics2D canvas, int x, int y, int size, Color color, int stroke) {
        final var arm = size / 4;
        final var midX = x + size / 2;
        final var midY = y + size / 2;
        canvas.setColor(color);
        canvas.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        canvas.drawLine(midX - arm, midY - arm, midX + arm, midY + arm);
        canvas.drawLine(midX - arm, midY + arm, midX + arm, midY - arm);
    }

    /**
     * A status dot, centred vertically in a row of {@code height}.
     *
     * <p>The panel says a state with a dot rather than a word wherever the word would be the same on every
     * row: eight rows each labelled <em>connected</em> is eight labels nobody reads, where eight dots are
     * one column the eye runs down.
     */
    public static void dot(Graphics2D canvas, int x, int height, int size, Color color) {
        canvas.setColor(color);
        canvas.fillOval(x, (height - size) / 2, size, size);
    }

    /**
     * The drag handle at the head of a reorderable row: two columns of three dots, the shape a row is
     * taken hold of by. Drawn rather than set as a glyph ({@code ⠿}) because a font that has no braille
     * block would draw the row a tofu box instead.
     *
     * @param width  the cell the handle is centred in horizontally, from its left edge {@code x}
     * @param height the cell it is centred in vertically, from its top edge {@code y}
     */
    public static void grip(Graphics2D canvas, int x, int y, int width, int height, Color color) {
        final var dot = Math.max(1, width / 5);
        final var step = dot * 2;
        final var left = x + (width - (dot + step)) / 2;
        final var top = y + (height - (dot + 2 * step)) / 2;

        canvas.setColor(color);
        for (var column = 0; column < 2; column++) {
            for (var row = 0; row < 3; row++) {
                canvas.fillOval(left + column * step, top + row * step, dot, dot);
            }
        }
    }

    /**
     * The text, cut with a trailing ellipsis when it does not fit {@code maxWidth}, or whole when it does.
     *
     * <p>Every string the panel draws into a cell of its own goes through here — a character's name, a
     * whisper's line, an activity's detail — so a name too long spills nowhere rather than running into
     * the column beside it.
     */
    public static String elide(FontMetrics metrics, String text, int maxWidth) {
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        final var room = maxWidth - metrics.stringWidth(ELLIPSIS);
        var end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end)) > room) {
            end--;
        }
        return text.substring(0, end) + ELLIPSIS;
    }

    private Draw() {
    }
}
