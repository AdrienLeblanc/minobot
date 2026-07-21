package fr.minobot.ui.components.buttons;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Draw;
import fr.minobot.ui.utils.Scale;

import javax.swing.JSlider;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.Dimension;
import java.awt.Graphics;

/**
 * The slider Swing draws is the desktop's, at the desktop's size. This one is the card's: a thin dark
 * track, filled in the accent up to a round accent thumb, and every size taken through {@link Scale} so
 * it reads at the panel's size on the panel's monitor.
 */
public final class FlatSlider extends BasicSliderUI {

    private static final int TRACK_HEIGHT = 4;
    private static final int THUMB_SIZE = 12;

    private final Scale scale;

    public FlatSlider(JSlider slider, Scale scale) {
        super(slider);
        this.scale = scale;
    }

    @Override
    protected Dimension getThumbSize() {
        return new Dimension(scale.px(THUMB_SIZE), scale.px(THUMB_SIZE));
    }

    @Override
    public void paintTrack(Graphics graphics) {
        final var canvas = Draw.smooth(graphics);
        final var height = scale.px(TRACK_HEIGHT);
        final var top = trackRect.y + (trackRect.height - height) / 2;
        final var done = thumbRect.x + thumbRect.width / 2 - trackRect.x;

        canvas.setColor(Theme.SURFACE);
        canvas.fillRoundRect(trackRect.x, top, trackRect.width, height, height, height);
        canvas.setColor(Theme.ACCENT);
        canvas.fillRoundRect(trackRect.x, top, done, height, height, height);
        canvas.dispose();
    }

    @Override
    public void paintThumb(Graphics graphics) {
        final var canvas = Draw.smooth(graphics);
        canvas.setColor(Theme.ACCENT);
        canvas.fillOval(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height);
        canvas.dispose();
    }

    @Override
    public void paintFocus(Graphics graphics) {
        // The panel cannot take the focus, so there is never a focus ring to draw.
    }
}
