package fr.minobot.ui.components.labels;

import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/** Secondary text under a control — what a switch means, what a drag does. */
public final class SectionHeader extends JPanel {

    public SectionHeader(Scale scale, String text, Component control) {
        super(new BorderLayout());
        this.setOpaque(false);
        this.setAlignmentX(Component.LEFT_ALIGNMENT);
        this.setBorder(new EmptyBorder(0, 0, scale.px(Metrics.GAP), 0));
        this.setMaximumSize(new Dimension(Integer.MAX_VALUE, scale.px(Metrics.ROW)));

        this.add(new Caption(scale, text), BorderLayout.WEST);
        if (control != null) {
            this.add(control, BorderLayout.EAST);
        }
    }
}
