package fr.minobot.ui.components.labels;

import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * A status chip: a coloured dot and a word, on a faint wash of the same colour. It says one thing twice —
 * the colour and the word — so a state reads at a glance. Painted straight onto a canvas rather than laid
 * out as a component: it follows text whose width the caller has measured, at the {@code (x)} it hands in.
 */
public final class Chip {

    /** The dot before the word — small, so the word carries the chip and the dot only tints it. */
    private static final int DOT = 7;

    /**
     * @param x      the chip's left edge, in the canvas's coordinates
     * @param height the row's height, the chip centred vertically in it
     */
    public static void paint(Scale scale, Graphics2D canvas, Color colour, String label, int x, int height) {
        canvas.setFont(scale.font(Metrics.HEADING, Metrics.PLAIN));
        final var fm = canvas.getFontMetrics();
        final var dot = scale.px(DOT);
        final var padding = scale.px(Metrics.GAP / 2 + 1);
        final var width = padding + dot + scale.px(3) + fm.stringWidth(label) + padding;
        final var chipHeight = fm.getHeight();
        final var top = (height - chipHeight) / 2;

        // A faint wash of the status colour, so the chip reads as one against the row without shouting.
        canvas.setColor(new Color(colour.getRed(), colour.getGreen(), colour.getBlue(), 38));
        canvas.fillRoundRect(x, top, width, chipHeight, chipHeight, chipHeight);

        canvas.setColor(colour);
        canvas.fillOval(x + padding, top + (chipHeight - dot) / 2, dot, dot);
        canvas.drawString(label, x + padding + dot + scale.px(3),
                top + (chipHeight + fm.getAscent() - fm.getDescent()) / 2);
    }

    private Chip() {
    }
}
