package fr.minobot.ui.components.buttons;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Draw;
import fr.minobot.ui.utils.Scale;

import javax.swing.JButton;
import java.awt.Cursor;
import java.awt.Graphics;

/**
 * A close cross — the mouse's way out, where a hotkey is the keyboard's. A hover square behind two
 * strokes; the strokes are {@link Draw#cross}, so it stays sharp at every scale and matches the forget
 * cross and the toast's own.
 *
 * <p>Like every control on a surface that never takes the foreground, it is <strong>not focusable</strong>.
 */
public final class CloseCross {

    public static JButton button(Scale scale, Runnable onClose) {
        final var button = new JButton() {
            @Override
            protected void paintComponent(Graphics graphics) {
                final var canvas = Draw.smooth(graphics);
                final var hover = getModel().isRollover();

                if (hover) {
                    canvas.setColor(Theme.HOVER);
                    canvas.fillRoundRect(0, 0, getWidth(), getHeight(),
                            scale.px(Metrics.RADIUS), scale.px(Metrics.RADIUS));
                }

                Draw.cross(canvas, 0, 0, getWidth(), hover ? Theme.TEXT : Theme.MUTED, scale.px(2));
                canvas.dispose();
            }
        };

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
