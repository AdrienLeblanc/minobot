package fr.minobot.ui.components.labels;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Fonts;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import javax.swing.JLabel;
import java.awt.Component;

/**
 * A section's name — {@code TEAM}, {@code ACTIVITY}, {@code KEYBINDS} — small, upper-case and spread out.
 *
 * <p>It is set apart by its <strong>tracking</strong>, not by a rule or a box: a heading that needs a box
 * to be found is a heading that was going to be missed. Semi-condensed, so the spreading costs no width.
 */
public final class Caption extends JLabel {

    public Caption(Scale scale, String text) {
        super(text.toUpperCase());
        this.setForeground(Theme.DIM);
        this.setFont(scale.tracked(scale.font(Fonts.CONDENSED, Metrics.HEADING), Metrics.HEADING_TRACKING));
        this.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
}
