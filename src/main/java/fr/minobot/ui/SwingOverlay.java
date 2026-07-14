package fr.minobot.ui;

import fr.minobot.app.Config;
import fr.minobot.app.Feature;
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
import java.util.Map;

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
    private DefaultListModel<String> characters;
    private JPanel card;
    private JPanel keybinds;
    private JPanel hotkeyRows;
    private JScrollPane characterList;

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

        card = characterCard();
        keybinds = keybindsCard();
        keybinds.setVisible(keybindsOpen);
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
        characters = new DefaultListModel<>();
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

        /** Added first so it sits on top of the card it rests in the corner of. */
        private final JButton close = closeButton();

        private Sheet() {
            setLayout(null);
            setOpaque(false);
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

    /** The panel proper: the application, the characters, and how big all of it is drawn. */
    private JPanel characterCard() {
        characterList = characterList();

        final var card = roundedColumn(BACKGROUND);
        card.setBorder(new EmptyBorder(px(PADDING), px(PADDING), px(PADDING), px(PADDING)));

        card.add(logo());
        card.add(Box.createVerticalStrut(px(PADDING)));
        card.add(sectionHeading("Characters", keybindsButton()));
        card.add(characterList);
        card.add(Box.createVerticalStrut(px(GAP)));
        card.add(hint("drag to reorder"));
        card.add(Box.createVerticalStrut(px(PADDING)));
        card.add(scaleSlider());
        return card;
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

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);

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
            canvas.dispose();

            super.paintComponent(graphics);
        }
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

        private final JList<String> list;

        private int from = -1;
        private boolean moved;

        private ReorderByDragging(JList<String> list) {
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
            from = -1;
            if (!moved) {
                return; // a plain click is not a reorder, and must not rewrite the order
            }
            moved = false;

            actions.reorder(Collections.list(characters.elements()));
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

    /** A section of the card: its name, small and spaced out, and whatever button belongs to it. */
    private JPanel sectionHeading(String text, JButton button) {
        final var row = new JPanel(new BorderLayout());
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setBorder(new EmptyBorder(0, 0, px(GAP), 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, px(HOTKEY_ROW_HEIGHT)));

        row.add(caption(text), BorderLayout.WEST);
        if (button != null) {
            row.add(button, BorderLayout.EAST);
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
     * the controller's follow loop then takes the panel down the same way, and {@code F10} reopens it
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
