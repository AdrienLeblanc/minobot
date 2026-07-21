package fr.minobot.ui.components.labels;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import javax.swing.*;
import java.awt.*;

/** A section's name, small and spaced out — set apart by its tracking, not by a rule or a box. */
public final class Caption extends JLabel {

    public Caption(Scale scale, String text) {
        super(text.toUpperCase());
        this.setForeground(Theme.MUTED);
        this.setFont(scale.tracked(scale.font(Metrics.HEADING, Metrics.BOLD), Metrics.HEADING_TRACKING));
        this.setAlignmentX(Component.LEFT_ALIGNMENT);
    }
}
