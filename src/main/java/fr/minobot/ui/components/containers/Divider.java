package fr.minobot.ui.components.containers;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Scale;

import javax.swing.JComponent;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Dimension;

/**
 * A hairline between two parts of one card — the settings above, what Minobot has been doing below.
 *
 * <p>It separates without enclosing, which is why it is a rule and not a second card: two cards would
 * say the two halves are two things, and they are one thing read in two passes. It is drawn in
 * {@link Theme#RULE}, the quietest line the panel has, for the same reason.
 */
public final class Divider {

    /** A rule across the card, between one stacked section and the next. */
    public static JComponent horizontal(Scale scale) {
        return rule(new Dimension(Integer.MAX_VALUE, scale.px(1)), scale.px(1));
    }

    /** A rule down the card, between two columns read side by side. */
    public static JComponent vertical(Scale scale) {
        return rule(new Dimension(scale.px(1), Integer.MAX_VALUE), scale.px(1));
    }

    private static JComponent rule(Dimension maximum, int thickness) {
        final var line = new JPanel();
        line.setBackground(Theme.RULE);
        line.setMaximumSize(maximum);
        line.setPreferredSize(new Dimension(
                maximum.width == Integer.MAX_VALUE ? thickness : maximum.width,
                maximum.height == Integer.MAX_VALUE ? thickness : maximum.height));
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
    }

    private Divider() {
    }
}
