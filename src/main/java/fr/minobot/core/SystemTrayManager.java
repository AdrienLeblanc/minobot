package fr.minobot.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AWTException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

/**
 * The notification-area icon — the counterpart of {@code system_tray.py}.
 *
 * <p>{@code java.awt.SystemTray} replaces {@code pystray} + {@code PIL}: the icon (a golden M on
 * brown) is drawn at the size the OS asks for, rather than at a fixed 64×64 that Windows would
 * downscale.
 */
public final class SystemTrayManager {

    private static final Logger log = LoggerFactory.getLogger(SystemTrayManager.class);

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

            final var trayIcon = new TrayIcon(drawIcon(size), "Minobot", buildMenu());
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
