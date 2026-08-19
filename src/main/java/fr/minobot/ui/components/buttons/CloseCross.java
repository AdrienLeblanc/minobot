package fr.minobot.ui.components.buttons;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Draw;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import javax.swing.JButton;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;

/**
 * A close cross — the mouse's way out, where a hotkey is the keyboard's. A tile behind two strokes; the
 * strokes are {@link Draw#cross}, so it stays sharp at every scale and matches every other cross the
 * panel draws.
 *
 * <p>The tile is drawn whether or not the pointer is over it. A cross that appeared only once found is a
 * cross nobody finds, and this is the one control a player reaches for when they want the panel
 * <em>gone</em>.
 *
 * <p>Like every control on a surface that never takes the foreground, it is <strong>not focusable</strong>.
 */
public final class CloseCross {

    /** @param side the button's own square side, in pixels — it sits in a row that must size it */
    public static JButton button(Scale scale, Runnable onClose, int side) {
        final var button = new JButton() {
            @Override
            protected void paintComponent(Graphics graphics) {
                final var canvas = Draw.smooth(graphics);
                final var hover = getModel().isRollover();
                final var radius = scale.px(Metrics.RADIUS_TILE);

                canvas.setColor(hover ? Theme.HOVER : Theme.RAISED);
                canvas.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
                canvas.setColor(Theme.EDGE);
                canvas.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);

                Draw.cross(canvas, 0, 0, getWidth(), hover ? Theme.TEXT : Theme.FAINT, scale.px(2));
                canvas.dispose();
            }
        };

        final var size = new Dimension(side, side);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
        button.setFocusable(false); // it could not take the focus anyway; do not draw as if it could
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(_ -> onClose.run());
        return button;
    }

    private CloseCross() {
    }
}
