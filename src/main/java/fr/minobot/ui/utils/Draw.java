package fr.minobot.ui.utils;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * The small drawing primitives every themed surface shares — so a shape drawn in two places is drawn by
 * one routine, and cannot drift a pixel apart between them.
 */
public final class Draw {

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
     * Drawn in three places — the panel's close cross, a disconnected row's forget cross, a toast's close
     * — which is why it is one routine.
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

    private Draw() {
    }
}
