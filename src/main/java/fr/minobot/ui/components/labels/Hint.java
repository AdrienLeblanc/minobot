package fr.minobot.ui.components.labels;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Secondary text under a control — what a switch means, what a drag does. */
public final class Hint extends JLabel {

    public Hint(Scale scale, String text) {
        super(text);
        this.setForeground(Theme.MUTED);
        this.setFont(scale.font(Metrics.SMALL, Metrics.BOLD));
        this.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
}
