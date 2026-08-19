package fr.minobot.ui.components.labels;

import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import javax.swing.Box;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * The line that opens a section: its {@link Caption}, an optional word of context beside it, and an
 * optional control pinned to the far right.
 *
 * <p>The note sits next to the heading and not under it — {@code TEAM} {@code 4 online of 8} reads as one
 * sentence, where a second line would read as a second thing to take in.
 */
public final class SectionHeader extends JPanel {

    public SectionHeader(Scale scale, String text, Component control) {
        this(scale, text, null, control);
    }

    /**
     * @param note    a word of context beside the heading — {@code 4 online of 8}; {@code null} for none
     * @param control what the section is acted on by — a switch, a drawer; {@code null} for none
     */
    public SectionHeader(Scale scale, String text, String note, Component control) {
        super(new BorderLayout());
        this.setOpaque(false);
        this.setAlignmentX(Component.LEFT_ALIGNMENT);
        this.setBorder(new EmptyBorder(0, 0, scale.px(Metrics.GAP), 0));
        this.setMaximumSize(new Dimension(Integer.MAX_VALUE, scale.px(Metrics.ROW)));

        final var heading = new JPanel(new FlowLayout(FlowLayout.LEFT, scale.px(Metrics.GAP), 0));
        heading.setOpaque(false);
        heading.setBorder(new EmptyBorder(0, 0, 0, 0));
        heading.add(new Caption(scale, text));
        if (note != null) {
            heading.add(new Hint(scale, note));
        }

        this.add(heading, BorderLayout.WEST);
        this.add(control == null ? Box.createHorizontalGlue() : control, BorderLayout.EAST);
    }
}
