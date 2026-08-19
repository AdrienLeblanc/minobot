package fr.minobot.ui.components.buttons;

import fr.minobot.ui.utils.Fonts;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Draw;
import fr.minobot.ui.utils.Scale;

import javax.swing.JButton;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;

/**
 * A flat button: a rounded fill that lights on hover, its label the only thing above it. The panel's
 * everyday control — reload, turn off, a sex, the drawer's switch.
 *
 * <p>It is drawn, not bordered, and it is <strong>not focusable</strong>: the surfaces it lives on never
 * take the foreground, so a focus ring would be a lie the panel cannot honour.
 *
 * <p>This base fixes only the <em>shape</em> and the hover mechanic; its colours are its concrete faces'
 * to choose. {@link PrimaryButton} and {@link SecondaryButton} each pin one pair of Theme colours, so a
 * caller names the emphasis it wants rather than handing in a {@link Color}. That is the whole point of
 * splitting it: one look per name, the same everywhere.
 */
abstract class FlatButton extends JButton {

    private final Scale scale;
    private final Color fill;
    private final Color hover;
    private final Color edge;

    FlatButton(Scale scale, String text, Color foreground, Color fill, Color hover, Color edge) {
        super(text);
        this.scale = scale;
        this.fill = fill;
        this.hover = hover;
        this.edge = edge;

        setFocusable(false); // it could not take the focus anyway; do not draw as if it could
        setContentAreaFilled(false);
        setBorderPainted(false);
        setForeground(foreground);
        setBorder(new EmptyBorder(scale.px(5), scale.px(Metrics.GAP + 3),
                scale.px(5), scale.px(Metrics.GAP + 3)));
        setFont(scale.font(Fonts.SEMIBOLD, Metrics.LABEL));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        final var canvas = Draw.smooth(graphics);
        final var radius = scale.px(Metrics.RADIUS_TILE);

        canvas.setColor(getModel().isRollover() ? hover : fill);
        canvas.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
        if (edge != null) {
            canvas.setColor(edge);
            canvas.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        }
        canvas.dispose();

        super.paintComponent(graphics);
    }
}
