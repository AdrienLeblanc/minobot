package fr.minobot.ui.overlay;

import fr.minobot.ui.Theme;
import fr.minobot.ui.components.containers.Card;
import fr.minobot.ui.utils.Draw;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * The application's own tile, at the top of the card.
 *
 * <p>It shows {@code logo.png} centred and scaled to fit, and the {@code MINOBOT} wordmark when there is
 * no such file — so the space is never read as a mistake, whether or not a logo was shipped. It draws no
 * background of its own: the card's dark surface carries the logo, which sits on the card, not in a box
 * on it.
 */
public final class LogoTile {

    private static final Logger log = LoggerFactory.getLogger(LogoTile.class);

    /** The application's logo, on the classpath so it rides inside the jar. Absent is not an error. */
    private static final String LOGO_RESOURCE = "/logo.png";

    /** The band the logo is drawn in. Tall on purpose: the logo is the first thing the panel says. */
    private static final int HEIGHT = 300;

    /** The wordmark's letters, spread wider than a heading's — it stands in for a picture, not a label. */
    private static final double WORDMARK_TRACKING = 0.22;

    /** The logo, read once; {@code null} when there is no {@code logo.png} to fall back from. */
    private final BufferedImage logo = loadLogo();

    /**
     * @param innerWidth the card's content width in pixels, so the tile is as wide as everything below it
     */
    public JPanel build(Scale scale, int innerWidth) {
        final var tile = logo != null ? new Tile(scale) : Card.plainColumn();
        tile.setMaximumSize(new Dimension(Integer.MAX_VALUE, scale.px(HEIGHT)));
        tile.setPreferredSize(new Dimension(innerWidth, scale.px(HEIGHT)));
        tile.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (logo == null) {
            tile.setLayout(new GridBagLayout());
            final var wordmark = new JLabel("MINOBOT");
            wordmark.setForeground(Theme.ACCENT);
            wordmark.setFont(scale.tracked(scale.font(Metrics.LOGO, Metrics.BOLD), WORDMARK_TRACKING));
            tile.add(wordmark);
        }
        return tile;
    }

    /**
     * The logo, drawn to fit its tile with its proportions kept — the same picture wherever the panel is
     * scaled, sharpened by asking the graphics for a smooth downscale rather than the nearest pixel.
     */
    private final class Tile extends JPanel {

        /** The logo is not laid against the tile's edge: it keeps a margin, like everything else does. */
        private static final int INSET = Metrics.GAP;

        private final Scale scale;

        private Tile(Scale scale) {
            this.scale = scale;
            setOpaque(false); // no tile behind it: the card's dark surface shows through
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            final var room = new Dimension(getWidth() - 2 * scale.px(INSET), getHeight() - 2 * scale.px(INSET));
            final var fit = Math.min(room.width / (double) logo.getWidth(),
                    room.height / (double) logo.getHeight());
            final var w = (int) Math.round(logo.getWidth() * fit);
            final var h = (int) Math.round(logo.getHeight() * fit);

            final var canvas = Draw.smooth(graphics);
            canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            canvas.drawImage(logo, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
            canvas.dispose();
        }
    }

    /**
     * The logo, or {@code null} if none was shipped.
     *
     * <p>Read once, off the classpath so a packaged {@code Minobot.exe} finds it where a loose file in
     * {@code assets/} would be lost. Its absence is not a failure — the wordmark stands in for it — so a
     * missing file is a debug line, not a warning, and a corrupt one does not bring the panel down.
     */
    private static BufferedImage loadLogo() {
        final var resource = LogoTile.class.getResource(LOGO_RESOURCE);
        if (resource == null) {
            log.debug("No {} on the classpath: the overlay falls back to its wordmark.", LOGO_RESOURCE);
            return null;
        }

        try {
            return ImageIO.read(resource);
        } catch (IOException e) {
            log.warn("Could not read the overlay logo {}: {}", LOGO_RESOURCE, e.getMessage());
            return null;
        }
    }
}
