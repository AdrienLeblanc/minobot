package fr.minobot.ui.components.containers;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Draw;
import fr.minobot.ui.utils.Scale;

import javax.swing.BoxLayout;
import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

/**
 * A card: a rounded sheet, drawn rather than bordered, so its corners are not square. The base surface
 * everything on a themed panel rests on.
 *
 * <p>It draws its own fill and its own hairline edge at the shared {@link Metrics#RADIUS}, both from
 * {@link Theme}. Two factories cover the common shapes: {@link #column} for a rounded card that stacks
 * its children top to bottom, and {@link #plainColumn} for a transparent stack that carries no surface of
 * its own — a group of rows that sits <em>on</em> a card rather than being one.
 */
public final class Card extends JPanel {

    private final Scale scale;
    private final Color fill;
    private final Color edge;

    public Card(Scale scale, Color fill, Color edge) {
        this.scale = scale;
        this.fill = fill;
        this.edge = edge;
        setOpaque(false);
    }

    /** A rounded card of the given fill, stacking its children in a column. */
    public static Card column(Scale scale, Color fill) {
        final var card = new Card(scale, fill, Theme.EDGE);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        return card;
    }

    /** A transparent column — rows grouped together, drawing no surface of their own. */
    public static JPanel plainColumn() {
        final var panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        final var canvas = Draw.smooth(graphics);

        canvas.setColor(fill);
        canvas.fillRoundRect(0, 0, getWidth(), getHeight(), scale.px(Metrics.RADIUS), scale.px(Metrics.RADIUS));
        if (edge != null) {
            canvas.setColor(edge);
            canvas.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1,
                    scale.px(Metrics.RADIUS), scale.px(Metrics.RADIUS));
        }
        canvas.dispose();

        super.paintComponent(graphics);
    }
}
