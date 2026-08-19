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

    /**
     * A rule is a hairline in the direction it is thin, and takes what it is given in the other.
     *
     * <p><strong>Its minimum is set, and that is not a formality.</strong> A bare {@link JPanel} keeps
     * Swing's default {@code FlowLayout}, whose minimum size is its two gaps — <em>ten pixels</em>, even
     * with no children and a preferred size of one. A {@code BoxLayout} that cannot fit the sum of its
     * children's minimums stops shrinking and lets the row overflow instead: the rule took its ten
     * pixels, and the column to its right was pushed past the edge of the card and clipped there. That
     * is how the whispers' {@code Clear} came to read {@code Clea}.
     */
    private static JComponent rule(Dimension maximum, int thickness) {
        final var line = new JPanel();
        line.setBackground(Theme.RULE);

        final var hairline = new Dimension(
                maximum.width == Integer.MAX_VALUE ? thickness : maximum.width,
                maximum.height == Integer.MAX_VALUE ? thickness : maximum.height);
        line.setMaximumSize(maximum);
        line.setPreferredSize(hairline);
        line.setMinimumSize(hairline);
        line.setAlignmentX(Component.LEFT_ALIGNMENT);
        return line;
    }

    private Divider() {
    }
}
