package fr.minobot.ui.components.labels;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Fonts;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import javax.swing.JLabel;
import java.awt.Component;

/**
 * Secondary text under a control — what a drag does, what a click on a whisper does, what a key may not
 * be. There to be found once and then never read again, which is why it is the quiet weight and not the
 * bold one.
 */
public final class Hint extends JLabel {

    public Hint(Scale scale, String text) {
        super(text);
        this.setForeground(Theme.FAINT);
        this.setFont(scale.font(Fonts.REGULAR, Metrics.SMALL));
        this.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
}
