package fr.minobot.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * The notification-area icon — the counterpart of {@code system_tray.py}.
 *
 * <p>{@code java.awt.SystemTray} replaces {@code pystray} + {@code PIL}: the icon is rendered at the
 * size the OS asks for, rather than at a fixed 64×64 that Windows would downscale. It shows the
 * application's artwork when a bitmap is shipped — {@code /tray.png} for choice, a square mark made
 * for tiny sizes, else the full {@code /logo.png} — and falls back to a drawn golden M so the tray
 * is never empty, whatever was packaged.
 */
public final class SystemTrayManager {

    private static final Logger log = LoggerFactory.getLogger(SystemTrayManager.class);

    /**
     * The icon bitmaps, most specific first. {@code /tray.png} is a square mark drawn for 16 px;
     * {@code /logo.png} is the full portrait artwork — usable but busy once shrunk to the tray.
     */
    private static final String[] ICON_RESOURCES = {"/tray.png", "/logo.png"};

    private static final Color BROWN = new Color(0x5D4037);
    private static final Color GOLD = new Color(0xFFD700);

    private final Runnable onQuit;
    private TrayIcon icon;

    public SystemTrayManager(Runnable onQuit) {
        this.onQuit = onQuit;
    }

    /**
     * Adds the icon to the notification area.
     *
     * <p>A missing tray is not fatal: the application keeps working, it just cannot be quit from
     * there.
     */
    public void start() {
        if (icon != null) {
            log.warn("System tray icon is already installed.");
            return;
        }
        if (GraphicsEnvironment.isHeadless() || !SystemTray.isSupported()) {
            log.warn("No system tray on this session; running without an icon.");
            return;
        }

        try {
            final var tray = SystemTray.getSystemTray();
            final var size = tray.getTrayIconSize();

            final var trayIcon = new TrayIcon(buildIcon(size), "Minobot", buildMenu());
            tray.add(trayIcon);
            icon = trayIcon;

            log.info("System tray icon installed.");
        } catch (AWTException e) {
            log.warn("Could not install the system tray icon: {}", e.getMessage());
        }
    }

    public void stop() {
        if (icon == null) {
            return;
        }
        SystemTray.getSystemTray().remove(icon);
        icon = null;
        log.info("System tray icon removed.");
    }

    private PopupMenu buildMenu() {
        final var header = new MenuItem("Minobot");
        header.setEnabled(false);

        final var quit = new MenuItem("Quit");
        quit.addActionListener(event -> onQuit.run());

        final var menu = new PopupMenu();
        menu.add(header);
        menu.addSeparator();
        menu.add(quit);
        return menu;
    }

    /**
     * The tray icon at the size the notification area wants: the shipped artwork when there is any,
     * else a golden M drawn to fit.
     */
    private static Image buildIcon(Dimension size) {
        final var artwork = loadArtwork();
        return artwork != null ? fit(artwork, size) : drawIcon(size);
    }

    /** The first icon bitmap on the classpath, or {@code null} when none was packaged. */
    private static BufferedImage loadArtwork() {
        for (final var resource : ICON_RESOURCES) {
            final var url = SystemTrayManager.class.getResource(resource);
            if (url == null) {
                continue;
            }
            try {
                return ImageIO.read(url);
            } catch (IOException e) {
                log.warn("Could not read the tray icon {}: {}", resource, e.getMessage());
            }
        }
        log.debug("No tray icon bitmap on the classpath: the tray falls back to its drawn M.");
        return null;
    }

    /**
     * The artwork scaled to sit inside {@code size} with its proportions kept — centred over a
     * transparent square, smoothed rather than sampled at the nearest pixel, so a large source and a
     * portrait one both come out clean at 16 px.
     */
    private static Image fit(BufferedImage artwork, Dimension size) {
        final var canvas = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        final var graphics = canvas.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        final var scale = Math.min(size.width / (double) artwork.getWidth(),
                size.height / (double) artwork.getHeight());
        final var w = (int) Math.round(artwork.getWidth() * scale);
        final var h = (int) Math.round(artwork.getHeight() * scale);
        graphics.drawImage(artwork, (size.width - w) / 2, (size.height - h) / 2, w, h, null);

        graphics.dispose();
        return canvas;
    }

    /** A golden M on a brown square, drawn to whatever size the notification area wants. */
    private static Image drawIcon(Dimension size) {
        final var image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        final var graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        graphics.setColor(BROWN);
        graphics.fillRect(0, 0, size.width, size.height);

        graphics.setColor(GOLD);
        graphics.setStroke(new BasicStroke(Math.max(2f, size.width / 8f)));

        final var width = size.width;
        final var height = size.height;
        final var xs = new int[]{round(0.19 * width), round(0.19 * width), round(0.50 * width), round(0.81 * width), round(0.81 * width)};
        final var ys = new int[]{round(0.81 * height), round(0.19 * height), round(0.50 * height), round(0.19 * height), round(0.81 * height)};
        graphics.drawPolyline(xs, ys, xs.length);

        graphics.dispose();
        return image;
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }
}
