package fr.minobot.ui.components.buttons;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Draw;
import fr.minobot.ui.utils.Fonts;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * A handful of choices, of which exactly one holds: the options laid side by side in one track, the
 * chosen one filled in.
 *
 * <p>Two buttons where one lights up would say the same, but not that the two are <em>the same
 * question</em> — the shared track is what says it, and it is why a segmented control is not a pair of
 * pills. Use it where the options are few, short and permanent; a list that grows wants a list.
 *
 * <p>It knows nothing of what it is choosing between: it is handed labels and hands back an index.
 */
public final class Segmented {

    /** The room a segment keeps around its label. */
    private static final int PAD_X = 10;
    private static final int PAD_Y = 3;

    /** The track's own inset, so the chosen segment's fill does not touch the track's edge. */
    private static final int INSET = 2;

    /**
     * @param chosen   the index that holds right now
     * @param onChoose handed the index the player has just clicked, chosen or not
     */
    public static JComponent of(Scale scale, List<String> labels, int chosen, IntConsumer onChoose) {
        final var track = new Track(scale);
        for (var index = 0; index < labels.size(); index++) {
            track.add(new Segment(scale, labels.get(index), index == chosen, index, onChoose));
        }
        return track;
    }

    /** The one surface the segments sit in — what says they answer the same question. */
    private static final class Track extends JPanel {

        private final Scale scale;

        private Track(Scale scale) {
            super(new FlowLayout(FlowLayout.LEFT, 0, 0));
            this.scale = scale;
            setOpaque(false);
            setBorder(new EmptyBorder(scale.px(INSET), scale.px(INSET), scale.px(INSET), scale.px(INSET)));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            final var canvas = Draw.smooth(graphics);
            final var radius = scale.px(Metrics.RADIUS_TILE);
            canvas.setColor(Theme.RAISED);
            canvas.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            canvas.setColor(Theme.EDGE);
            canvas.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            canvas.dispose();

            super.paintComponent(graphics);
        }
    }

    /** One option: filled in the ember when it is the one that holds, bare text when it is not. */
    private static final class Segment extends JLabel {

        private final Scale scale;
        private final boolean chosen;

        private Segment(Scale scale, String label, boolean chosen, int index, IntConsumer onChoose) {
            super(label);
            this.scale = scale;
            this.chosen = chosen;

            // The chosen segment carries the ember, so its own label is the darkest thing on the card.
            setForeground(chosen ? Theme.BACKGROUND : Theme.FAINT);
            setFont(scale.font(Fonts.SEMIBOLD, Metrics.SMALL));
            setBorder(new EmptyBorder(scale.px(PAD_Y), scale.px(PAD_X), scale.px(PAD_Y), scale.px(PAD_X)));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent event) {
                    onChoose.accept(index);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (chosen) {
                final var canvas = Draw.smooth(graphics);
                final var radius = scale.px(Metrics.RADIUS_CHIP);
                canvas.setColor(Theme.ACCENT);
                canvas.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
                canvas.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    private Segmented() {
    }
}
