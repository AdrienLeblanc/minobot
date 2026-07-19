package fr.minobot.ui;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;
import fr.minobot.app.Config;
import fr.minobot.app.Feature;
import fr.minobot.core.domain.Character;
import fr.minobot.core.domain.DofusClass;
import fr.minobot.core.domain.Sex;
import fr.minobot.win32.Rect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.plaf.basic.BasicSliderUI;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.TextAttribute;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The panel, in Swing. The only class in Minobot that knows Swing exists.
 *
 * <p><strong>It must never take the foreground.</strong> There is one screen, and
 * {@code core.FocusManager} is what keeps the features from fighting over it: a panel that stole the
 * focus would land between two keystrokes of the invitation relay and send the rest of an
 * {@code /invite} to another character's window. Hence {@code setFocusableWindowState(false)} — a
 * window that receives the mouse and never the activation — and hence, too, the fact that
 * <strong>nothing here is typed</strong>: a keybind is read from the keyboard by
 * {@link OverlayActions#captureHotkey()}, not typed into a text field that would need the focus.
 *
 * <p>That one line is enough, and it was <em>measured</em> against the real game rather than assumed —
 * both when the panel is shown and when it is clicked, the character the player is on keeps the
 * screen. <strong>Do not add {@code WS_EX_NOACTIVATE} to the window to make sure</strong>: Swing
 * already does this one, and the extended style would be a native call bought for nothing.
 *
 * <p><strong>Every size below is a natural size, and none of them is a pixel.</strong> The panel covers
 * the game, and the game is played on screens of every size; Swing's own defaults were laid out for a
 * 96-DPI desktop, which on a large screen is a panel nobody can read. So each is multiplied by the
 * player's scale on its way to the screen — {@link #px} for a length, {@link #font} for a typeface —
 * and a size that skips them is a size that will be wrong on somebody's monitor.
 *
 * <p>Two threads meet here, and neither may do the other's work. Swing lives on the event dispatch
 * thread, while {@code toggle()} arrives on the virtual thread of a hotkey — so every method below
 * hands its work to the EDT. And {@code captureHotkey()} <em>blocks</em>, waiting for a human, so it
 * is the one thing that must leave the EDT: run on it, it would freeze the panel it is meant to
 * update, and freeze it in front of a player who is pressing keys at it.
 */
public final class SwingOverlay implements OverlayView {

    private static final Logger log = LoggerFactory.getLogger(SwingOverlay.class);

    /** The application's logo, on the classpath so it rides inside the jar. Absent is not an error. */
    private static final String LOGO_RESOURCE = "/logo.png";

    /**
     * The card, at scale 1. The window is as wide as the game; the card is not.
     *
     * <p>The drawer beside it has no width of its own: it takes the one its rows need. A number here
     * would be a number to keep in step with the longest feature label, and it would lose.
     */
    private static final int CARD_WIDTH = 280;

    private static final int PADDING = 16;
    private static final int GAP = 8;
    private static final int RADIUS = 12;

    /** The band the logo is drawn in. Tall on purpose: the logo is the first thing the panel says. */
    private static final int LOGO_HEIGHT = 300;

    /** The close cross in the card's corner: the mouse's way out, where the hotkey is the keyboard's. */
    private static final int CLOSE_SIZE = 24;

    /** Room for about five characters; beyond that the list scrolls rather than the card growing. */
    private static final int CHARACTER_LIST_HEIGHT = 130;
    private static final int CHARACTER_ROW_HEIGHT = 26;
    private static final int HOTKEY_ROW_HEIGHT = 26;

    /** The class shown on a character's row: a small icon, and the room its cell is given at the right. */
    private static final int CLASS_ICON = 18;
    private static final int CLASS_CELL_WIDTH = 104;

    /** The class picker: a grid of tiles, each a class's icon over its name, four to a row. */
    private static final int CLASS_COLUMNS = 4;
    private static final int CLASS_TILE = 58;
    private static final int CLASS_TILE_ICON = 32;

    /** The auto-pass switch: a pill the knob slides across, wide enough to read as one and not a dot. */
    private static final int SWITCH_WIDTH = 40;
    private static final int SWITCH_HEIGHT = 20;
    private static final int SWITCH_PADDING = 3;

    private static final float LOGO_SIZE = 17f;
    private static final float BODY_SIZE = 12f;
    private static final float SMALL_SIZE = 11f;
    private static final float HEADING_SIZE = 10f;

    /** The game is dimmed, not hidden: the player must still see the character they are configuring. */
    private static final Color BACKDROP = new Color(6, 8, 12, 150);

    private static final Color BACKGROUND = new Color(22, 24, 30, 243);
    private static final Color SURFACE = new Color(33, 36, 44);
    private static final Color HOVER = new Color(48, 53, 64);
    private static final Color EDGE = new Color(50, 55, 67);
    private static final Color TEXT = new Color(232, 235, 241);
    private static final Color MUTED = new Color(124, 131, 145);
    private static final Color SELECTED = new Color(46, 52, 64);
    private static final Color ACCENT = new Color(122, 168, 255);

    /** What a feature with no key shows, and what the player clicks to give it one back. */
    private static final String UNBOUND = "—";

    private final OverlayActions actions;

    /** Touched on the event dispatch thread only. Built on first show: there may never be one. */
    private JWindow window;
    private Font baseFont;

    /** The logo, read once; {@code null} when there is no {@code logo.png} to fall back from. */
    private BufferedImage logo;

    /**
     * The class icons, parsed once off the classpath, one per class and sex. A pair missing from the map
     * has no SVG shipped for it, and is drawn as a lettered badge instead — the same fallback the logo
     * makes to its wordmark. They are vectors: rendered afresh at each size, so they stay crisp wherever
     * the player scales the panel.
     */
    private Map<DofusClass, Map<Sex, SVGDocument>> classIcons;
    private DefaultListModel<Character> characters;
    private JPanel card;
    private JPanel keybinds;
    private JPanel hotkeyRows;
    private JScrollPane characterList;

    /**
     * The character the class picker is open for, or {@code null} when it is closed. The picker is a
     * modal over the whole sheet, so it is the panel's state, not the player's: nothing on disk remembers
     * a half-made choice.
     */
    private String classPickerFor;
    private JComponent classPicker;

    /** What every size on the cards was computed with. A change to it is a card built again. */
    private double scale;

    /**
     * Whether the keybinds are unfolded. The panel's, not the player's: it is a drawer the view opens,
     * and there is nothing in {@code config.json} for it to be remembered in.
     */
    private boolean keybindsOpen;

    /** So the panel can be redrawn after a capture that came to nothing, or a drawer that opened. */
    private OverlayContent lastContent;
    private Rect lastBounds;

    /** Read from the hotkey's thread, so it cannot live inside Swing. */
    private volatile boolean visible;

    public SwingOverlay(OverlayActions actions) {
        this.actions = actions;
    }

    @Override
    public void show(OverlayContent content, Rect bounds) {
        visible = true;
        SwingUtilities.invokeLater(() -> draw(content, bounds));
    }

    /**
     * Called many times a second while the player drags their window, so it does no more than it must:
     * the panel is already built, and only its bounds have changed.
     */
    @Override
    public void moveTo(Rect bounds) {
        SwingUtilities.invokeLater(() -> {
            if (window == null || !window.isVisible()) {
                return;
            }
            lastBounds = bounds;
            window.setBounds(bounds.left(), bounds.top(), bounds.width(), bounds.height());
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

    /**
     * Whether the player has the panel up.
     *
     * <p>Answered from the flag rather than from Swing: the hotkey asks on its own thread, and it asks
     * the instant it is pressed — before the EDT has had a chance to draw anything.
     */
    @Override
    public boolean isVisible() {
        return visible;
    }

    // ------------------------------------------------------------------ the event dispatch thread

    private void draw(OverlayContent content, Rect bounds) {
        lastContent = content;
        lastBounds = bounds;

        if (window == null) {
            build();
        }

        // The cards are built again rather than patched: every size on them was computed with the scale
        // of the moment — the paddings, the fonts, the height of a row — and walking them all to correct
        // them would be the walk that builds them.
        lay(content, content.scale(), bounds);

        final var fits = scaleThatFits(bounds);
        if (fits < scale) {
            lay(content, fits, bounds);
        }

        // The panel <em>is</em> the game's client area: exactly as wide and as tall, and starting where
        // the game itself starts — below the title bar, whose buttons must stay clickable.
        window.setBounds(bounds.left(), bounds.top(), bounds.width(), bounds.height());
        window.setVisible(true);
        window.getContentPane().revalidate();
        window.getContentPane().repaint();
    }

    /** The cards, at one scale: built, filled, and shrunk to the room the game leaves them. */
    private void lay(OverlayContent content, double scale, Rect bounds) {
        this.scale = scale;

        card = characterCard(content);
        keybinds = keybindsCard();
        keybinds.setVisible(keybindsOpen);
        classPicker = classPickerFor == null ? null : classPickerScrim(classPickerFor);
        window.setContentPane(new Sheet());

        characters.clear();
        content.characters().forEach(characters::addElement);

        for (final var feature : Feature.values()) {
            hotkeyRows.add(hotkeyRow(feature, content.hotkeys().getOrDefault(feature, "")));
        }

        shrinkListToFit(bounds);
    }

    private void build() {
        window = new JWindow();
        window.setAlwaysOnTop(true);

        // The whole point: the panel receives the mouse, and never the activation.
        window.setFocusableWindowState(false);

        translucentIfSupported();

        baseFont = new JLabel().getFont();
        logo = loadLogo();
        classIcons = loadClassIcons();
        characters = new DefaultListModel<>();
    }

    /**
     * Every class icon that was shipped, one per class and sex, parsed once off the classpath so a
     * packaged {@code Minobot.exe} finds each where a loose file would be lost. A pair with no SVG simply
     * stays out of the map: its absence is not a failure — {@link #paintClassIcon} draws a lettered badge
     * in its place — so a missing file is a debug line, and a corrupt one does not bring the panel down.
     */
    private Map<DofusClass, Map<Sex, SVGDocument>> loadClassIcons() {
        final var loader = new SVGLoader();
        final var icons = new EnumMap<DofusClass, Map<Sex, SVGDocument>>(DofusClass.class);

        for (final var clazz : DofusClass.values()) {
            final var perSex = new EnumMap<Sex, SVGDocument>(Sex.class);
            for (final var sex : Sex.values()) {
                final var path = clazz.iconResource(sex);
                final var resource = SwingOverlay.class.getResource(path);
                if (resource == null) {
                    log.debug("No {}: {} {} falls back to a badge.", path, clazz.label(), sex);
                    continue;
                }
                // load() returns null on a malformed SVG and can throw on a broken stream; either way a
                // bad icon must not take the panel down, so the pair is simply left to its badge.
                try {
                    final var document = loader.load(resource);
                    if (document != null) {
                        perSex.put(sex, document);
                    } else {
                        log.warn("Could not parse the class icon {}.", path);
                    }
                } catch (RuntimeException e) {
                    log.warn("Could not read the class icon {}: {}", path, e.getMessage());
                }
            }
            icons.put(clazz, perSex);
        }
        return icons;
    }

    /**
     * The logo, or {@code null} if none was shipped.
     *
     * <p>Read once, off the classpath so a packaged {@code Minobot.exe} finds it where a loose file in
     * {@code assets/} would be lost. Its absence is not a failure — the wordmark stands in for it — so a
     * missing file is a debug line, not a warning, and a corrupt one does not bring the panel down.
     */
    private BufferedImage loadLogo() {
        final var resource = SwingOverlay.class.getResource(LOGO_RESOURCE);
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

    /**
     * A translucent background needs the desktop to support per-pixel alpha. It normally does — but a
     * remote session or a disabled compositor does not, and there the panel is simply opaque rather
     * than absent.
     */
    private void translucentIfSupported() {
        final var device = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
        if (device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.PERPIXEL_TRANSLUCENT)) {
            window.setBackground(new Color(0, 0, 0, 0));
        }
    }

    // ------------------------------------------------------------------ where the cards sit

    /**
     * The dim sheet over the game, with the cards resting on it.
     *
     * <p>The window covers the whole client area, so the game is dimmed rather than hidden — the player
     * has to see the character they are configuring. The card is placed <strong>in the middle of it</strong>:
     * it is where the player is already looking, and a panel that opens under the eye is a panel that
     * needs no hunting for. The keybinds drawer hangs off its right edge.
     *
     * <p>The card gives up the middle for one reason only: a drawer that would otherwise open past the
     * right edge of the game. It then slides left by exactly what the drawer is short of, and no more.
     * A card that stayed centred there would have to be <em>shrunk</em> for the drawer to fit beside it,
     * and a panel that changes size when a button is clicked reads as a bug; a panel that shifts by an
     * inch reads as a drawer opening.
     *
     * <p>No layout manager, because the rule <em>is</em> a layout manager, and it is shorter than the
     * constraints one would have to be given.
     */
    private final class Sheet extends JPanel {

        /** Added ahead of the card so it sits on top of the corner it rests in. */
        private final JButton close = closeButton();

        private Sheet() {
            setLayout(null);
            setOpaque(false);
            // Swing paints index 0 last, so the first added sits on top. The picker, while open, must
            // cover everything and be the only thing to click — so it goes in first, ahead of the close
            // cross, which itself goes ahead of the card it rests in the corner of.
            if (classPicker != null) {
                add(classPicker);
            }
            add(close);
            add(card);
            add(keybinds);
        }

        @Override
        public void doLayout() {
            final var main = card.getPreferredSize();
            final var drawer = keybinds.getPreferredSize();

            final var top = (getHeight() - main.height) / 2;
            var left = (getWidth() - main.width) / 2;

            if (keybinds.isVisible()) {
                final var past = left + main.width + px(GAP) + drawer.width
                        - (getWidth() - px(PADDING));
                left = Math.max(px(PADDING), left - Math.max(0, past));
            }

            card.setBounds(left, top, main.width, main.height);
            keybinds.setBounds(left + main.width + px(GAP), top, drawer.width, drawer.height);

            // Pinned to the card's top-right corner, and it follows when the card slides for the drawer.
            final var size = px(CLOSE_SIZE);
            close.setBounds(left + main.width - px(GAP) - size, top + px(GAP), size, size);

            // The picker covers the whole sheet: it dims what is behind it and catches every click.
            if (classPicker != null) {
                classPicker.setBounds(0, 0, getWidth(), getHeight());
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            final var canvas = (Graphics2D) graphics.create();
            canvas.setColor(BACKDROP);
            canvas.fillRect(0, 0, getWidth(), getHeight());
            canvas.dispose();

            super.paintComponent(graphics);
        }
    }

    /**
     * The list of characters gives way first when the cards are taller than the game.
     *
     * <p>It is the one part of them that can be short and still be usable, because it scrolls — and it
     * stands above the controls, which must not be pushed off the bottom of the screen.
     */
    private void shrinkListToFit(Rect bounds) {
        final var full = px(CHARACTER_LIST_HEIGHT);
        listHeight(full);

        final var overflow = tallest() - room(bounds).height;
        if (overflow > 0) {
            listHeight(Math.max(px(CHARACTER_ROW_HEIGHT), full - overflow));
        }
    }

    /**
     * The scale the game's window can actually hold — the player's, or less.
     *
     * <p>The cards are scaled and the window is not: it is the game's, and the game may be a small
     * window on a large screen. Past a certain size the cards no longer fit in it, and what falls off
     * the edge is the slider — <strong>the one control that could bring them back</strong>. So the panel
     * never grows past the game it covers, and the slider reads what the player is actually looking at.
     */
    private double scaleThatFits(Rect bounds) {
        final var room = room(bounds);
        final var fits = Math.min(
                room.height / (double) tallest(),
                room.width / (double) widest());

        if (fits >= 1) {
            return scale;
        }
        return Math.max(Config.MIN_OVERLAY_SCALE, scale * fits);
    }

    /** How tall the panel stands: the card, or the drawer beside it if that one is taller. */
    private int tallest() {
        return Math.max(card.getPreferredSize().height,
                keybinds.isVisible() ? keybinds.getPreferredSize().height : 0);
    }

    /**
     * How wide it stands: the card, and the drawer alongside it when that one is open. The card slides
     * off-centre to make room rather than the two overlapping, so their widths simply add up.
     */
    private int widest() {
        final var drawer = keybinds.isVisible()
                ? px(GAP) + keybinds.getPreferredSize().width
                : 0;
        return card.getPreferredSize().width + drawer;
    }

    /** What the game leaves the cards, once the sheet has kept its margin around them. */
    private Dimension room(Rect bounds) {
        return new Dimension(bounds.width() - 2 * px(PADDING), bounds.height() - 2 * px(PADDING));
    }

    private void listHeight(int height) {
        characterList.setPreferredSize(new Dimension(px(CARD_WIDTH - 2 * PADDING), height));
        characterList.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
        card.revalidate();
    }

    // ------------------------------------------------------------------ the card

    /** The panel proper: the application, the characters, the combat switches, and the size. */
    private JPanel characterCard(OverlayContent content) {
        characterList = characterList();

        final var card = roundedColumn(BACKGROUND);
        card.setBorder(new EmptyBorder(px(PADDING), px(PADDING), px(PADDING), px(PADDING)));

        card.add(logo());
        card.add(Box.createVerticalStrut(px(PADDING)));
        card.add(sectionHeading("Characters", keybindsButton()));
        card.add(characterList);
        card.add(Box.createVerticalStrut(px(GAP)));
        card.add(charactersFooter());

        card.add(Box.createVerticalStrut(px(PADDING)));
        final var autoPass = content.autoPassTurn();
        card.add(sectionHeading("Auto-pass turns", switchControl(autoPass, actions::toggleAutoPassTurn)));
        card.add(hint(autoPass ? "on — every character ends its own turn" : "off — turns are yours to play"));

        card.add(Box.createVerticalStrut(px(GAP)));
        final var autoAccept = content.autoAcceptTrade();
        card.add(sectionHeading("Auto-accept trades", switchControl(autoAccept, actions::toggleAutoAcceptTrade)));
        card.add(hint(autoAccept ? "on — my characters' trades accept themselves" : "off"));

        card.add(Box.createVerticalStrut(px(PADDING)));
        card.add(scaleSlider());
        return card;
    }

    /**
     * A switch, and the word for which way it is set — the panel's most explicit control, because these
     * are the features that act on the game for the player. The word and the colour say the same thing
     * twice on purpose: a switch that quietly plays or accepts on its own has to be unmistakable.
     */
    private JComponent switchControl(boolean on, Consumer<Boolean> onToggle) {
        final var state = new JLabel(on ? "ON" : "OFF");
        state.setForeground(on ? ACCENT : MUTED);
        state.setFont(tracked(font(HEADING_SIZE, Font.BOLD), 0.14));

        final var control = new JPanel(new FlowLayout(FlowLayout.RIGHT, px(GAP), 0));
        control.setOpaque(false);
        control.add(state);
        control.add(switchPill(on, onToggle));
        return control;
    }

    /** The pill the knob rests in: filled and knob-right when on, hollow and knob-left when off. */
    private JButton switchPill(boolean on, Consumer<Boolean> onToggle) {
        final var pill = new JButton() {
            @Override
            protected void paintComponent(Graphics graphics) {
                final var canvas = smooth(graphics);
                final var knob = getHeight() - 2 * px(SWITCH_PADDING);
                final var x = on ? getWidth() - px(SWITCH_PADDING) - knob : px(SWITCH_PADDING);

                canvas.setColor(on ? ACCENT : SURFACE);
                canvas.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                if (!on) {
                    canvas.setColor(EDGE);
                    canvas.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                }
                canvas.setColor(on ? BACKGROUND : MUTED);
                canvas.fillOval(x, px(SWITCH_PADDING), knob, knob);
                canvas.dispose();
            }
        };

        pill.setPreferredSize(new Dimension(px(SWITCH_WIDTH), px(SWITCH_HEIGHT)));
        pill.setFocusable(false); // it could not take the focus anyway; do not draw as if it could
        pill.setContentAreaFilled(false);
        pill.setBorderPainted(false);
        pill.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        pill.addActionListener(_ -> onToggle.accept(!on));
        return pill;
    }

    /**
     * The application's own tile, at the top of the card.
     *
     * <p>It shows {@code logo.png} centred and scaled to fit, and the wordmark when there is no such
     * file — so the space is never read as a mistake, whether or not a logo was shipped. It draws no
     * background of its own: the card's dark surface carries the logo, which sits on the card, not in a
     * box on it.
     */
    private JPanel logo() {
        final var tile = logo != null ? new LogoTile() : column();
        tile.setMaximumSize(new Dimension(Integer.MAX_VALUE, px(LOGO_HEIGHT)));
        tile.setPreferredSize(new Dimension(px(CARD_WIDTH - 2 * PADDING), px(LOGO_HEIGHT)));
        tile.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (logo == null) {
            tile.setLayout(new GridBagLayout());
            final var wordmark = new JLabel("MINOBOT");
            wordmark.setForeground(ACCENT);
            wordmark.setFont(tracked(font(LOGO_SIZE, Font.BOLD), 0.22));
            tile.add(wordmark);
        }
        return tile;
    }

    /**
     * The logo, drawn to fit its tile with its proportions kept — the same picture wherever the panel is
     * scaled, sharpened by asking the graphics for a smooth downscale rather than the nearest pixel.
     */
    private final class LogoTile extends JPanel {

        /** The logo is not laid against the tile's edge: it keeps a margin, like everything else does. */
        private static final int INSET = GAP;

        private LogoTile() {
            setOpaque(false); // no tile behind it: the card's dark surface shows through
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            final var room = new Dimension(getWidth() - 2 * px(INSET), getHeight() - 2 * px(INSET));
            final var fit = Math.min(room.width / (double) logo.getWidth(),
                    room.height / (double) logo.getHeight());
            final var w = (int) Math.round(logo.getWidth() * fit);
            final var h = (int) Math.round(logo.getHeight() * fit);

            final var canvas = smooth(graphics);
            canvas.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            canvas.drawImage(logo, (getWidth() - w) / 2, (getHeight() - h) / 2, w, h, null);
            canvas.dispose();
        }
    }

    /** The drawer's switch. It says which way it will go, which is all a chevron is for. */
    private JButton keybindsButton() {
        final var button = flatButton((keybindsOpen ? "Keybinds ‹" : "Keybinds ›"),
                keybindsOpen ? ACCENT : MUTED);
        button.addActionListener(_ -> {
            keybindsOpen = !keybindsOpen;
            redraw();
        });
        return button;
    }

    // ------------------------------------------------------------------ the characters

    /** The line under the list: what a drag does on the left, and the button that re-reads the desktop. */
    private JComponent charactersFooter() {
        final var row = new JPanel(new BorderLayout(px(GAP), 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, px(HOTKEY_ROW_HEIGHT)));
        row.add(hint("drag to reorder"), BorderLayout.WEST);
        row.add(reloadButton(), BorderLayout.EAST);
        return row;
    }

    /**
     * Re-reads the desktop, for a character opened or closed since the panel went up.
     *
     * <p><strong>Off the event dispatch thread</strong>: the refresh enumerates every window and reads
     * each title, and the panel must not freeze while it does — the same reason a capture leaves the EDT.
     * The redraw it triggers hands itself back to the EDT on its own.
     */
    private JButton reloadButton() {
        final var button = flatButton("Reload", MUTED);
        button.addActionListener(_ ->
                Thread.ofVirtual().name("overlay-reload").start(actions::reload));
        return button;
    }

    private JScrollPane characterList() {
        final var list = new JList<>(characters);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setOpaque(false);
        list.setForeground(TEXT);
        list.setFont(font(BODY_SIZE, Font.PLAIN));
        list.setFixedCellHeight(px(CHARACTER_ROW_HEIGHT));
        list.setCellRenderer(new CharacterRow());
        list.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        final var drag = new ReorderByDragging(list);
        list.addMouseListener(drag);
        list.addMouseMotionListener(drag);

        final var scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUI(new FlatScrollBar());
        scroll.getVerticalScrollBar().setOpaque(false);
        scroll.getVerticalScrollBar().setPreferredSize(new Dimension(px(GAP / 2 + 2), 0));
        return scroll;
    }

    /** A character on a tile of their own, so a row can be picked up by eye before it is by hand. */
    private final class CharacterRow extends DefaultListCellRenderer {

        /** Never {@code getBackground()}: with none of its own, a component answers with its parent's. */
        private boolean highlighted;

        /** The row's character, kept apart from the numbered text, to draw their class and sex from. */
        private Character character = new Character("");

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);

            character = (Character) value;
            // The number is the row's rank read off the panel — 1 at the top — and no more: it is a
            // mirror of the order, not a name, so a drag that reorders the list renumbers it for free.
            setText((index + 1) + " - " + character.name());

            highlighted = selected;
            setOpaque(false);
            setForeground(TEXT);
            setFont(font(BODY_SIZE, Font.PLAIN));
            setBorder(new EmptyBorder(0, px(GAP + 2), 0, px(GAP)));
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            final var canvas = smooth(graphics);
            final var inset = px(1);

            canvas.setColor(highlighted ? SELECTED : SURFACE);
            canvas.fillRoundRect(0, inset, getWidth(), getHeight() - 2 * inset, px(RADIUS), px(RADIUS));

            if (highlighted) {
                // The grip: three bars where a row is taken hold of, and a hint that it can be.
                canvas.setColor(ACCENT);
                canvas.fillRoundRect(0, inset, px(3), getHeight() - 2 * inset, px(3), px(3));
            }

            // The class sits at the right of the row: its icon and name once chosen, an invitation to
            // choose one until then. Drawn before the name text (super) — the two never share the row's
            // width, the name being short and left, the class right — so neither writes over the other.
            paintClassCell(canvas, character, getWidth(), getHeight());
            canvas.dispose();

            super.paintComponent(graphics);
        }
    }

    /**
     * The class shown at the right of a character's row: the icon and the class's name once one is
     * pinned, a muted {@code choose class…} until then. Nothing at all for the login placeholder, which
     * is a window without a character to give a class to.
     */
    private void paintClassCell(Graphics2D canvas, Character character, int width, int height) {
        if (character.name().equals(OverlayContent.LOGGING_IN)) {
            return;
        }

        final var right = width - px(GAP);
        final var metrics = canvas.getFontMetrics(font(SMALL_SIZE, Font.PLAIN));
        final var baseline = (height + metrics.getAscent() - metrics.getDescent()) / 2;

        final var clazz = character.clazz();
        if (clazz == null) {
            canvas.setFont(font(SMALL_SIZE, Font.PLAIN));
            canvas.setColor(MUTED);
            final var text = "choose class…";
            canvas.drawString(text, right - metrics.stringWidth(text), baseline);
            return;
        }

        final var size = px(CLASS_ICON);
        final var iconX = right - size;
        paintClassIcon(canvas, clazz, character.sexOrDefault(), iconX, (height - size) / 2, size);

        canvas.setFont(font(SMALL_SIZE, Font.PLAIN));
        canvas.setColor(TEXT);
        canvas.drawString(clazz.label(), iconX - px(4) - metrics.stringWidth(clazz.label()), baseline);
    }

    /**
     * The character the panel currently holds under a name — the one the picker is open for, looked up so
     * the toggle and the tiles can read its sex. A name off the panel (a stale click racing a disconnect)
     * resolves to a fresh, unpinned character, drawn as male, which the controller then refuses to persist.
     */
    private Character characterNamed(String name) {
        if (lastContent == null) {
            return new Character(name);
        }
        return lastContent.characters().stream()
                .filter(character -> character.name().equals(name))
                .findFirst()
                .orElse(new Character(name));
    }

    /**
     * A class's icon at a given size, for a sex: its SVG if one was shipped, a lettered badge if not — the
     * same fallback the logo makes to its wordmark, so a class with no art is still told apart at a glance.
     *
     * <p>The SVG is a vector, rendered straight onto the canvas at the asked-for size rather than off a
     * cached bitmap: it is as sharp at 200% as at 100%, which is the whole reason the icons are SVG.
     */
    private void paintClassIcon(Graphics2D canvas, DofusClass clazz, Sex sex, int x, int y, int size) {
        final var document = classIcons.getOrDefault(clazz, Map.of()).get(sex);
        if (document != null) {
            final var g = (Graphics2D) canvas.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            document.render(null, g, new ViewBox(x, y, size, size));
            g.dispose();
            return;
        }

        canvas.setColor(SURFACE);
        canvas.fillRoundRect(x, y, size, size, size, size);
        canvas.setColor(ACCENT);
        canvas.setFont(font(SMALL_SIZE, Font.BOLD));
        final var initial = clazz.label().substring(0, 1).toUpperCase(Locale.ROOT);
        final var fm = canvas.getFontMetrics();
        canvas.drawString(initial, x + (size - fm.stringWidth(initial)) / 2,
                y + (size + fm.getAscent() - fm.getDescent()) / 2);
    }

    /**
     * Reordering by hand, rather than through Swing's drag and drop.
     *
     * <p>Swing's own machinery goes through the platform's drag-and-drop stack, which is a great deal
     * of native ceremony to move a row inside one list — and it is ceremony that expects a window with
     * the focus, which this one deliberately does not have. Press, drag, release is three mouse events
     * and no ceremony at all.
     */
    private final class ReorderByDragging extends MouseAdapter {

        private final JList<Character> list;

        private int from = -1;
        private boolean moved;

        private ReorderByDragging(JList<Character> list) {
            this.list = list;
        }

        @Override
        public void mousePressed(MouseEvent event) {
            from = list.locationToIndex(event.getPoint());
            moved = false;
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            final var to = list.locationToIndex(event.getPoint());
            if (from < 0 || to < 0 || to == from || to >= characters.size()) {
                return;
            }

            characters.add(to, characters.remove(from));
            list.setSelectedIndex(to);
            from = to;
            moved = true;
        }

        @Override
        public void mouseReleased(MouseEvent event) {
            final var index = from;
            from = -1;
            if (moved) {
                moved = false;
                actions.reorder(Collections.list(characters.elements()).stream()
                        .map(Character::name)
                        .toList());
                return;
            }

            // A plain click is not a reorder — but a plain click on the class cell at the right of a row
            // is a request to choose that character's class. Anywhere else it is just a selection.
            maybeOpenClassPicker(index, event.getPoint());
        }

        /** Opens the picker when the click landed in the row's class cell, on a real character. */
        private void maybeOpenClassPicker(int index, Point point) {
            if (index < 0 || index >= characters.size()) {
                return;
            }
            final var character = characters.get(index);
            if (character.name().equals(OverlayContent.LOGGING_IN)) {
                return; // a not-yet-logged-in window has no character to give a class to
            }

            final var cell = list.getCellBounds(index, index);
            if (cell != null && point.x >= cell.x + cell.width - px(CLASS_CELL_WIDTH)) {
                openClassPicker(character.name());
            }
        }
    }

    // ------------------------------------------------------------------ the keybinds drawer

    /**
     * Every feature's key, in a card of its own beside the panel.
     *
     * <p>They are a list of seven rows the player edits once and then forgets, and they were making the
     * panel twice as tall as what it is actually for. Folded away, they cost the card nothing; unfolded,
     * they cost it no room either — the drawer opens beside it, not inside it.
     */
    private JPanel keybindsCard() {
        hotkeyRows = column();

        // The lighter surface, so the drawer reads as a card set on the panel rather than part of it.
        final var drawer = roundedColumn(SURFACE);
        drawer.setBorder(new EmptyBorder(px(PADDING), px(PADDING), px(PADDING), px(PADDING)));

        drawer.add(sectionHeading("Keybinds", null));
        drawer.add(hotkeyRows);
        drawer.add(Box.createVerticalStrut(px(GAP)));
        drawer.add(hint("click a key to change it, × to turn it off"));
        return drawer;
    }

    private JPanel hotkeyRow(Feature feature, String hotkey) {
        final var row = new JPanel(new BorderLayout(px(GAP), 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, px(HOTKEY_ROW_HEIGHT)));

        final var name = new JLabel(feature.label());
        name.setForeground(hotkey.isBlank() ? MUTED : TEXT);
        name.setFont(font(SMALL_SIZE, Font.PLAIN));

        // The pills sit on the drawer's light surface, so their fill is the dark one — the reverse of
        // the buttons on the main card, and the same contrast the other way round.
        final var key = flatButton(hotkey.isBlank() ? UNBOUND : hotkey,
                hotkey.isBlank() ? MUTED : ACCENT, BACKGROUND);
        key.addActionListener(_ -> capture(feature, key));

        final var clear = flatButton("×", MUTED, BACKGROUND);
        clear.setEnabled(!hotkey.isBlank());
        clear.addActionListener(_ -> actions.rebind(feature, ""));

        final var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, px(4), 0));
        buttons.setOpaque(false);
        buttons.add(key);
        buttons.add(clear);

        row.add(name, BorderLayout.WEST);
        row.add(buttons, BorderLayout.EAST);
        return row;
    }

    /**
     * Waits for the player to press the key they want — <strong>off the event dispatch thread</strong>.
     *
     * <p>The wait is for a human, so it is seconds long. On the EDT it would freeze the very panel the
     * player is pressing keys at, and Swing would repaint nothing until they were done.
     */
    private void capture(Feature feature, JButton key) {
        key.setText("press…");
        key.setEnabled(false);

        Thread.ofVirtual().name("overlay-capture").start(() -> {
            final var captured = actions.captureHotkey();

            SwingUtilities.invokeLater(() -> captured.ifPresentOrElse(
                    combination -> actions.rebind(feature, combination),
                    // Nothing was pressed. The configuration did not change, so nothing will redraw the
                    // panel for us — and the button is still saying "press…".
                    this::redraw));
        });
    }

    private void redraw() {
        if (lastContent != null && lastBounds != null) {
            draw(lastContent, lastBounds);
        }
    }

    // ------------------------------------------------------------------ the class picker

    /**
     * Opens the class picker for a character, and closes it again.
     *
     * <p>The picker is a <em>modal</em>: it covers the whole sheet and catches every click, so there is
     * no way to edit the panel underneath it by accident. It is built in {@link #lay} from
     * {@code classPickerFor}, so a redraw carries it along until the choice is made — or the player clicks
     * off it. Neither is typed: a class is picked with the mouse, on a window that cannot take the focus.
     */
    private void openClassPicker(String character) {
        classPickerFor = character;
        redraw();
    }

    private void closeClassPicker() {
        classPickerFor = null;
        redraw();
    }

    /**
     * The picker over the whole sheet: a darkened backdrop with the grid of classes centred on it. A
     * click on the backdrop — anywhere but the grid — closes it; the game is not touched, so a mis-click
     * costs nothing but the picker.
     */
    private JComponent classPickerScrim(String character) {
        final var scrim = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics graphics) {
                final var canvas = (Graphics2D) graphics.create();
                canvas.setColor(BACKDROP);
                canvas.fillRect(0, 0, getWidth(), getHeight());
                canvas.dispose();
                super.paintComponent(graphics);
            }
        };
        scrim.setOpaque(false);
        scrim.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                closeClassPicker();
            }
        });

        scrim.add(classGrid(character));
        return scrim;
    }

    /**
     * The card the picker rests on the scrim: a heading naming the character, the sex toggle, and the
     * twelve classes drawn in the chosen sex.
     */
    private JComponent classGrid(String character) {
        final var sex = characterNamed(character).sexOrDefault();

        final var card = roundedColumn(BACKGROUND);
        card.setBorder(new EmptyBorder(px(PADDING), px(PADDING), px(PADDING), px(PADDING)));
        card.add(sectionHeading("Class · " + character, sexToggle(character, sex)));

        final var grid = new JPanel(new GridLayout(0, CLASS_COLUMNS, px(GAP), px(GAP)));
        grid.setOpaque(false);
        grid.setAlignmentX(Component.LEFT_ALIGNMENT);
        for (final var clazz : DofusClass.values()) {
            grid.add(classTile(character, clazz, sex));
        }
        card.add(grid);
        return card;
    }

    /**
     * The male/female toggle, next to the picker's heading. Picking a sex records it at once and leaves
     * the picker open — so the class tiles below redraw in that sex, and the class the player then chooses
     * is theirs in it. The sex already set is the lit one.
     */
    private JComponent sexToggle(String character, Sex current) {
        final var row = new JPanel(new FlowLayout(FlowLayout.RIGHT, px(4), 0));
        row.setOpaque(false);
        for (final var sex : Sex.values()) {
            final var active = sex == current;
            final var pill = flatButton(sex.label(), active ? BACKGROUND : MUTED, active ? ACCENT : SURFACE);
            pill.addActionListener(_ -> actions.assignSex(character, sex));
            row.add(pill);
        }
        return row;
    }

    /**
     * One class on the picker: its icon over its name, and a click that pins it to the character and
     * closes the picker. The assignment is made on the way out — {@code classPickerFor} is cleared first,
     * so the redraw the assignment triggers finds the picker already shut. The icon is drawn in the sex
     * the toggle currently shows, which is also the sex the class is pinned in.
     */
    private JButton classTile(String character, DofusClass clazz, Sex sex) {
        final var tile = new JButton() {
            @Override
            protected void paintComponent(Graphics graphics) {
                final var canvas = smooth(graphics);
                canvas.setColor(getModel().isRollover() ? HOVER : SURFACE);
                canvas.fillRoundRect(0, 0, getWidth(), getHeight(), px(RADIUS), px(RADIUS));

                final var size = px(CLASS_TILE_ICON);
                paintClassIcon(canvas, clazz, sex, (getWidth() - size) / 2, px(GAP), size);

                canvas.setColor(TEXT);
                canvas.setFont(font(HEADING_SIZE, Font.PLAIN));
                final var fm = canvas.getFontMetrics();
                canvas.drawString(clazz.label(),
                        (getWidth() - fm.stringWidth(clazz.label())) / 2, getHeight() - px(GAP));
                canvas.dispose();
            }
        };

        tile.setPreferredSize(new Dimension(px(CLASS_TILE), px(CLASS_TILE)));
        tile.setFocusable(false); // it could not take the focus anyway; do not draw as if it could
        tile.setContentAreaFilled(false);
        tile.setBorderPainted(false);
        tile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        tile.addActionListener(_ -> {
            classPickerFor = null;
            actions.assignClass(character, clazz);
        });
        return tile;
    }

    // ------------------------------------------------------------------ the size of the panel

    /**
     * How big the panel is drawn — the one control that changes the panel it lives on.
     *
     * <p>Which is why it lands only when the player <em>lets go</em>: a scale that took effect on every
     * pixel of the drag would rebuild the card, and the slider under the mouse with it, several times a
     * second. The percentage beside it follows the thumb, so the drag is not silent for all that.
     */
    private JPanel scaleSlider() {
        final var percent = new JLabel(percentage(scale) + "%");
        percent.setForeground(MUTED);
        percent.setFont(font(SMALL_SIZE, Font.PLAIN));

        final var slider = new JSlider(
                percentage(Config.MIN_OVERLAY_SCALE), percentage(Config.MAX_OVERLAY_SCALE),
                percentage(scale));
        slider.setUI(new FlatSlider(slider));
        slider.setOpaque(false);
        slider.setFocusable(false); // it could not take the focus anyway; do not draw as if it could
        slider.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        slider.addChangeListener(_ -> {
            percent.setText(slider.getValue() + "%");
            if (!slider.getValueIsAdjusting()) {
                actions.rescale(slider.getValue() / 100.0);
            }
        });

        final var row = new JPanel(new BorderLayout(px(GAP), 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, px(HOTKEY_ROW_HEIGHT)));
        row.add(caption("Size"), BorderLayout.WEST);
        row.add(slider, BorderLayout.CENTER);
        row.add(percent, BorderLayout.EAST);
        return row;
    }

    /** The scale as the player reads it, and as the slider carries it: 150 rather than 1.5. */
    private static int percentage(double scale) {
        return (int) Math.round(scale * 100);
    }

    /** The slider Swing draws is the desktop's, at the desktop's size. This one is the card's. */
    private final class FlatSlider extends BasicSliderUI {

        private static final int TRACK_HEIGHT = 4;
        private static final int THUMB_SIZE = 12;

        private FlatSlider(JSlider slider) {
            super(slider);
        }

        @Override
        protected Dimension getThumbSize() {
            return new Dimension(px(THUMB_SIZE), px(THUMB_SIZE));
        }

        @Override
        public void paintTrack(Graphics graphics) {
            final var canvas = smooth(graphics);
            final var height = px(TRACK_HEIGHT);
            final var top = trackRect.y + (trackRect.height - height) / 2;
            final var done = thumbRect.x + thumbRect.width / 2 - trackRect.x;

            canvas.setColor(SURFACE);
            canvas.fillRoundRect(trackRect.x, top, trackRect.width, height, height, height);
            canvas.setColor(ACCENT);
            canvas.fillRoundRect(trackRect.x, top, done, height, height, height);
            canvas.dispose();
        }

        @Override
        public void paintThumb(Graphics graphics) {
            final var canvas = smooth(graphics);
            canvas.setColor(ACCENT);
            canvas.fillOval(thumbRect.x, thumbRect.y, thumbRect.width, thumbRect.height);
            canvas.dispose();
        }

        @Override
        public void paintFocus(Graphics graphics) {
            // The panel cannot take the focus, so there is never a focus ring to draw.
        }
    }

    /** The desktop's scrollbar is a grey slab with two arrows on it. This one is a thumb, and dark. */
    private final class FlatScrollBar extends BasicScrollBarUI {

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
            final var canvas = smooth(graphics);
            canvas.setColor(isDragging || isThumbRollover() ? HOVER : EDGE);
            canvas.fillRoundRect(bounds.x, bounds.y, bounds.width, bounds.height,
                    bounds.width, bounds.width);
            canvas.dispose();
        }
    }

    // ------------------------------------------------------------------ the small pieces

    /** A natural length, at the size the player asked for. Every length on the card goes through here. */
    private int px(int natural) {
        return (int) Math.round(natural * scale);
    }

    private Font font(float natural, int style) {
        return baseFont.deriveFont(style, natural * (float) scale);
    }

    /** Letters given room to breathe — what a heading is set apart by, rather than by a rule or a box. */
    private static Font tracked(Font font, double tracking) {
        return font.deriveFont(Map.of(TextAttribute.TRACKING, tracking));
    }

    private static Graphics2D smooth(Graphics graphics) {
        final var canvas = (Graphics2D) graphics.create();
        canvas.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        canvas.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        return canvas;
    }

    /** A card: a rounded sheet, drawn rather than bordered, so that its corners are not square. */
    private class RoundedPanel extends JPanel {

        private final Color fill;
        private final Color edge;

        private RoundedPanel(Color fill, Color edge) {
            this.fill = fill;
            this.edge = edge;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            final var canvas = smooth(graphics);

            canvas.setColor(fill);
            canvas.fillRoundRect(0, 0, getWidth(), getHeight(), px(RADIUS), px(RADIUS));
            if (edge != null) {
                canvas.setColor(edge);
                canvas.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, px(RADIUS), px(RADIUS));
            }
            canvas.dispose();

            super.paintComponent(graphics);
        }
    }

    private JPanel roundedColumn(Color fill) {
        final var panel = new RoundedPanel(fill, EDGE);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private JPanel column() {
        final var panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    /** A section of the card: its name, small and spaced out, and whatever control belongs to it. */
    private JPanel sectionHeading(String text, Component control) {
        final var row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(0, 0, px(GAP), 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, px(HOTKEY_ROW_HEIGHT)));

        row.add(caption(text), BorderLayout.WEST);
        if (control != null) {
            row.add(control, BorderLayout.EAST);
        }
        return row;
    }

    private JLabel caption(String text) {
        final var label = new JLabel(text.toUpperCase());
        label.setForeground(MUTED);
        label.setFont(tracked(font(HEADING_SIZE, Font.BOLD), 0.14));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JLabel hint(String text) {
        final var label = new JLabel(text);
        label.setForeground(MUTED);
        label.setFont(font(SMALL_SIZE, Font.PLAIN));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JButton flatButton(String text, Color foreground) {
        return flatButton(text, foreground, SURFACE);
    }

    private JButton flatButton(String text, Color foreground, Color fill) {
        final var button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics graphics) {
                final var canvas = smooth(graphics);
                canvas.setColor(getModel().isRollover() ? HOVER : fill);
                canvas.fillRoundRect(0, 0, getWidth(), getHeight(), px(RADIUS), px(RADIUS));
                canvas.dispose();

                super.paintComponent(graphics);
            }
        };

        button.setFocusable(false); // it could not take the focus anyway; do not draw as if it could
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setForeground(foreground);
        button.setBorder(new EmptyBorder(px(4), px(GAP + 2), px(4), px(GAP + 2)));
        button.setFont(font(SMALL_SIZE, Font.PLAIN));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    /**
     * The close cross in the card's corner — the mouse's way out, where the hotkey is the keyboard's.
     *
     * <p>It calls {@link #hide()}, the very thing the hotkey and a departing character already trigger:
     * the controller's follow loop then takes the panel down the same way, and {@code shift+space} reopens it
     * cleanly. The cross is drawn from two strokes rather than a glyph, so it stays sharp at every scale.
     */
    private JButton closeButton() {
        final var button = new JButton() {
            @Override
            protected void paintComponent(Graphics graphics) {
                final var canvas = smooth(graphics);
                final var hover = getModel().isRollover();

                if (hover) {
                    canvas.setColor(HOVER);
                    canvas.fillRoundRect(0, 0, getWidth(), getHeight(), px(RADIUS), px(RADIUS));
                }

                final var arm = getWidth() / 4;
                final var mid = getWidth() / 2;
                canvas.setColor(hover ? TEXT : MUTED);
                canvas.setStroke(new BasicStroke(px(2), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                canvas.drawLine(mid - arm, mid - arm, mid + arm, mid + arm);
                canvas.drawLine(mid - arm, mid + arm, mid + arm, mid - arm);
                canvas.dispose();
            }
        };

        button.setFocusable(false); // it could not take the focus anyway; do not draw as if it could
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.addActionListener(_ -> hide());
        return button;
    }
}
