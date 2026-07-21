package fr.minobot.ui.components.buttons;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Draw;
import fr.minobot.ui.utils.Scale;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.util.function.Consumer;

/**
 * A switch, and the word for which way it is set — the panel's most explicit control, for the features
 * that act on the game on the player's behalf. The word and the colour say the same thing twice on
 * purpose: a switch that quietly plays or accepts on its own has to be unmistakable.
 */
public final class Toggle {

    /** The pill the knob slides across, wide enough to read as one and not a dot. */
    private static final int WIDTH = 40;
    private static final int HEIGHT = 20;
    private static final int PAD = 3;

    /** The switch and its state label together — {@code ON} in the accent, {@code OFF} muted. */
    public static JComponent control(Scale scale, boolean on, Consumer<Boolean> onToggle) {
        final var state = new JLabel(on ? "ON" : "OFF");
        state.setForeground(on ? Theme.ACCENT : Theme.MUTED);
        state.setFont(scale.tracked(scale.font(Metrics.HEADING, Metrics.BOLD), Metrics.HEADING_TRACKING));

        final var control = new JPanel(new FlowLayout(FlowLayout.RIGHT, scale.px(Metrics.GAP), 0));
        control.setOpaque(false);
        control.add(state);
        control.add(pill(scale, on, onToggle));
        return control;
    }

    /** The pill the knob rests in: filled and knob-right when on, hollow and knob-left when off. */
    private static JButton pill(Scale scale, boolean on, Consumer<Boolean> onToggle) {
        final var pill = new JButton() {
            @Override
            protected void paintComponent(Graphics graphics) {
                final var canvas = Draw.smooth(graphics);
                final var knob = getHeight() - 2 * scale.px(PAD);
                final var x = on ? getWidth() - scale.px(PAD) - knob : scale.px(PAD);

                canvas.setColor(on ? Theme.ACCENT : Theme.SURFACE);
                canvas.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                if (!on) {
                    canvas.setColor(Theme.EDGE);
                    canvas.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                }
                canvas.setColor(on ? Theme.BACKGROUND : Theme.MUTED);
                canvas.fillOval(x, scale.px(PAD), knob, knob);
                canvas.dispose();
            }
        };

        pill.setPreferredSize(new Dimension(scale.px(WIDTH), scale.px(HEIGHT)));
        pill.setFocusable(false); // it could not take the focus anyway; do not draw as if it could
        pill.setContentAreaFilled(false);
        pill.setBorderPainted(false);
        pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pill.addActionListener(_ -> onToggle.accept(!on));
        return pill;
    }

    private Toggle() {
    }
}
