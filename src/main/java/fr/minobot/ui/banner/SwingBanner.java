package fr.minobot.ui.banner;

import fr.minobot.ui.BannerActions;
import fr.minobot.ui.BannerContent;
import fr.minobot.ui.BannerView;
import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Draw;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;
import fr.minobot.win32.Rect;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * The auto-pass banner, in Swing. The only class in the banner that knows Swing exists — a sibling of
 * {@code SwingOverlay} and {@code SwingToastStack}, held to the same discipline. It shares the design
 * system ({@link Scale} for every size, {@link Draw} for the smoothing and the close cross, {@link Theme}
 * for the dark card), so the three surfaces read at the same size on the same monitor.
 *
 * <p><strong>It must never take the foreground.</strong> Like the panel and the whisper stack, it would
 * otherwise land between two keystrokes of the invitation relay or a turn-pass — the very feature it
 * announces. Hence {@code setFocusableWindowState(false)}, and hence nothing here is typed: the banner is
 * read, or dismissed with the mouse. <strong>Do not add {@code WS_EX_NOACTIVATE} to make sure</strong>:
 * Swing already does this one.
 *
 * <p>Unlike the panel it is <strong>not</strong> the whole game: it is sized to the one card and pinned to
 * the top of the game, centred left to right with a little padding below the top edge, so it blocks only a
 * narrow band. Its one click — the close cross — is routed by hand, as the whisper cards' are.
 *
 * <p>The follow loop calls {@link #show}, {@link #moveTo} and {@link #hide} from a virtual thread, and
 * every one of them hands its work to the event dispatch thread, where Swing lives.
 */
public final class SwingBanner implements BannerView {

    /** The card's own sizes, at scale 1 — kept next to the code, only the shared rhythm comes from Metrics. */
    private static final int RADIUS = 11;

    /** The room inside the card, around the text: {@code PAD_X} left and right, {@code PAD_Y} above and below.
     *  The height is measured from the text plus {@code PAD_Y} twice, so the card is always taller than its
     *  message rather than pinned to a guessed constant. */
    private static final int PAD_X = 15;
    private static final int PAD_Y = 12;

    /** The breathing room the window keeps around the card, so a rounded corner is not clipped. */
    private static final int MARGIN = 8;

    /** How far below the game's top edge the card hangs — padding, not flush to the top. */
    private static final int TOP_PADDING = 18;

    /** The close cross in the card's corner: the mouse's way to take the banner down. */
    private static final int CLOSE_SIZE = 16;

    /** The dot that marks the banner as a live state, in the SECONDARY accent — reads "on" at a glance. */
    private static final int DOT = 9;

    private static final float MESSAGE_SIZE = 13f;

    private final BannerActions actions;

    /** Touched on the event dispatch thread only. Built on first show: there may never be one. */
    private JWindow window;
    private Banner banner;
    private Font baseFont;

    /** What every size was computed with, and where the banner was last hung. */
    private Scale scale;
    private Rect anchor;

    /** Read from the follow thread, so it cannot live inside Swing. */
    private volatile boolean visible;

    public SwingBanner(BannerActions actions) {
        this.actions = actions;
    }

    @Override
    public void show(BannerContent content, Rect anchor) {
        visible = true;
        SwingUtilities.invokeLater(() -> draw(content, anchor));
    }

    @Override
    public void moveTo(Rect anchor) {
        SwingUtilities.invokeLater(() -> {
            if (window == null || !window.isVisible()) {
                return;
            }
            this.anchor = anchor;
            place();
        });
    }

    @Override
    public void hide() {
        visible = false;
        SwingUtilities.invokeLater(() -> {
            if (window != null) {
                window.setVisible(false);
            }
        });
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    // ------------------------------------------------------------------ the event dispatch thread

    private void draw(BannerContent content, Rect anchor) {
        if (window == null) {
            build();
        }

        this.scale = new Scale(content.scale(), baseFont);
        this.anchor = anchor;
        banner.setMessage(content.message());

        final var size = bannerSize(content.message());
        window.setSize(size);
        banner.setPreferredSize(size);

        place();
        window.setVisible(true);
        window.revalidate();
        window.repaint();
    }

    private void build() {
        window = new JWindow();
        window.setAlwaysOnTop(true);

        // The whole point: the banner receives the mouse, and never the activation.
        window.setFocusableWindowState(false);

        translucentIfSupported();

        baseFont = new JLabel().getFont();
        banner = new Banner();
        window.setContentPane(banner);
    }

    /**
     * A translucent background needs the desktop to support per-pixel alpha. It normally does — but a
     * remote session or a disabled compositor does not, and there the banner is simply drawn on a black
     * band rather than not at all.
     */
    private void translucentIfSupported() {
        final var device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
            window.setBackground(new Color(0, 0, 0, 0));
        }
    }

    /** The box the card needs: the accent dot, the measured message, the close cross, padding and margin. */
    private Dimension bannerSize(String message) {
        final var metrics = banner.getFontMetrics(font(MESSAGE_SIZE, Font.BOLD));

        // The row's tallest thing sets the height — the text, or the close cross when it is taller — plus a
        // full PAD_Y above and below, so the card always clears its own message rather than crowding it.
        final var rowHeight = Math.max(metrics.getAscent() + metrics.getDescent(), px(CLOSE_SIZE));
        final var cardHeight = rowHeight + 2 * px(PAD_Y);

        final var cardWidth = 2 * px(PAD_X)
                + px(DOT) + px(Metrics.GAP)
                + metrics.stringWidth(message) + px(Metrics.GAP)
                + px(CLOSE_SIZE);

        return new Dimension(cardWidth + 2 * px(MARGIN), cardHeight + 2 * px(MARGIN));
    }

    /** Pins the window to the top of the game, centred left to right, a little below the top edge. */
    private void place() {
        if (anchor == null) {
            return;
        }
        final var left = anchor.left() + (anchor.width() - window.getWidth()) / 2;
        window.setLocation(left, anchor.top() + px(TOP_PADDING) - px(MARGIN));
    }

    private int px(int natural) {
        return scale.px(natural);
    }

    private Font font(float natural, int style) {
        return scale.font(natural, style);
    }

    // ------------------------------------------------------------------ the card

    /**
     * The card itself: it paints the accent dot, the message and the close cross, and remembers where the
     * cross landed so a click can be told whether it hit it. Painting and hit-testing read the same
     * placement, so they can never disagree.
     */
    private final class Banner extends JPanel {

        private String message = "";

        /** Where the close cross was last painted, for the mouse to test against; {@code null} until drawn. */
        private Rectangle close;

        /** Whether the pointer is over the cross, so it can be lit. */
        private boolean closeHovered;

        private Banner() {
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
            final var mouse = new Clicks();
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        private void setMessage(String message) {
            this.message = message;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            final var canvas = Draw.smooth(graphics);

            // The card fills the window less its margin — in both dimensions, so the drawn height tracks the
            // window the same way the width does rather than a constant that could disagree with it.
            final var x = px(MARGIN);
            final var y = px(MARGIN);
            final var width = getWidth() - 2 * px(MARGIN);
            final var height = getHeight() - 2 * px(MARGIN);

            // The card, on the dark theme, with a hairline edge.
            canvas.setColor(Theme.BACKGROUND);
            canvas.fillRoundRect(x, y, width, height, px(RADIUS), px(RADIUS));
            canvas.setColor(Theme.EDGE);
            canvas.drawRoundRect(x, y, width - 1, height - 1, px(RADIUS), px(RADIUS));

            // The accent dot, vertically centred — the SECONDARY that says "this is on".
            final var dotX = x + px(PAD_X);
            final var dotY = y + (height - px(DOT)) / 2;
            canvas.setColor(Theme.SECONDARY);
            canvas.fillOval(dotX, dotY, px(DOT), px(DOT));

            // The message, to the right of the dot, baseline centred in the card.
            final var textLeft = dotX + px(DOT) + px(Metrics.GAP);
            canvas.setColor(Theme.TEXT);
            canvas.setFont(font(MESSAGE_SIZE, Font.BOLD));
            final var fm = canvas.getFontMetrics();
            final var baseline = y + (height - (fm.getAscent() + fm.getDescent())) / 2 + fm.getAscent();
            canvas.drawString(message, textLeft, baseline);

            // The close cross, pinned to the card's right padding, vertically centred.
            close = new Rectangle(
                    x + width - px(PAD_X) - px(CLOSE_SIZE),
                    y + (height - px(CLOSE_SIZE)) / 2,
                    px(CLOSE_SIZE), px(CLOSE_SIZE));
            if (closeHovered) {
                canvas.setColor(Theme.HOVER);
                canvas.fillRoundRect(close.x, close.y, close.width, close.height, px(RADIUS), px(RADIUS));
            }
            Draw.cross(canvas, close.x, close.y, close.width,
                    closeHovered ? Theme.TEXT : Theme.MUTED, Math.max(1, px(2)));

            canvas.dispose();
        }

        private final class Clicks extends MouseAdapter {

            @Override
            public void mouseReleased(MouseEvent event) {
                if (close != null && close.contains(event.getPoint())) {
                    actions.dismiss();
                }
            }

            @Override
            public void mouseMoved(MouseEvent event) {
                final var over = close != null && close.contains(event.getPoint());
                if (over != closeHovered) {
                    closeHovered = over;
                    setCursor(Cursor.getPredefinedCursor(over ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    repaint();
                }
            }
        }
    }
}
