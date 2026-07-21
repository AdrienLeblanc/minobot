package fr.minobot.ui.overlay;

import fr.minobot.app.Config;
import fr.minobot.ui.OverlayActions;
import fr.minobot.ui.Theme;
import fr.minobot.ui.components.buttons.FlatSlider;
import fr.minobot.ui.components.labels.Caption;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import javax.swing.*;
import java.awt.*;

/**
 * How big the panel is drawn — the one control that changes the panel it lives on.
 *
 * <p>Which is why it lands only when the player <em>lets go</em>: a scale that took effect on every pixel
 * of the drag would rebuild the card, and the slider under the mouse with it, several times a second. The
 * percentage beside it follows the thumb, so the drag is not silent for all that. The thumb reads the
 * scale the panel is <em>drawn</em> at — capped to what the game window holds — not what the player asked
 * for, so it never points off the edge of a control that could bring the panel back.
 */
public final class SizeSlider {

    private final OverlayActions actions;

    public SizeSlider(OverlayActions actions) {
        this.actions = actions;
    }

    public JPanel build(Scale scale) {
        final var percent = new JLabel(percentage(scale.factor()) + "%");
        percent.setForeground(Theme.MUTED);
        percent.setFont(scale.font(Metrics.SMALL, Metrics.PLAIN));

        final var slider = new JSlider(
                percentage(Config.MIN_OVERLAY_SCALE), percentage(Config.MAX_OVERLAY_SCALE),
                percentage(scale.factor()));
        slider.setUI(new FlatSlider(slider, scale));
        slider.setOpaque(false);
        slider.setFocusable(false); // it could not take the focus anyway; do not draw as if it could
        slider.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        slider.addChangeListener(_ -> {
            percent.setText(slider.getValue() + "%");
            if (!slider.getValueIsAdjusting()) {
                actions.rescale(slider.getValue() / 100.0);
            }
        });

        final var row = new JPanel(new BorderLayout(scale.px(Metrics.GAP), 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, scale.px(Metrics.ROW)));
        row.add(new Caption(scale, "Size"), BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(percent, BorderLayout.EAST);
        return row;
    }

    /** The scale as the player reads it, and as the slider carries it: 150 rather than 1.5. */
    private static int percentage(double scale) {
        return (int) Math.round(scale * 100);
    }
}
