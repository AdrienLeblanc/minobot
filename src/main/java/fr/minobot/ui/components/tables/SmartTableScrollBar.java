package fr.minobot.ui.components.tables;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Draw;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Rectangle;

/**
 * The desktop's scrollbar is a grey slab with two arrows on it. This one is a thumb, and dark: no track,
 * no arrows, so the card shows through around a rounded thumb that lights under the pointer.
 */
public final class SmartTableScrollBar extends BasicScrollBarUI {

    @Override
    protected JButton createIncreaseButton(int orientation) {
        return noButton();
    }

    @Override
    protected JButton createDecreaseButton(int orientation) {
        return noButton();
    }

    private static JButton noButton() {
        final var button = new JButton();
        button.setPreferredSize(new Dimension());
        return button;
    }

    @Override
    protected void paintTrack(Graphics graphics, JComponent scrollbar, Rectangle bounds) {
        // The card shows through: a track drawn here would be a stripe down the side of it.
    }

    @Override
    protected void paintThumb(Graphics graphics, JComponent scrollbar, Rectangle bounds) {
        final var canvas = Draw.smooth(graphics);
        canvas.setColor(isDragging || isThumbRollover() ? Theme.HOVER : Theme.EDGE);
        canvas.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height, bounds.width, bounds.width);
        canvas.dispose();
    }
}
