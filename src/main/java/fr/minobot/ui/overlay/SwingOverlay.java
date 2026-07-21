package fr.minobot.ui.overlay;

import fr.minobot.app.Config;
import fr.minobot.core.domain.DofusClass;
import fr.minobot.ui.OverlayActions;
import fr.minobot.ui.OverlayContent;
import fr.minobot.ui.OverlayView;
import fr.minobot.ui.Theme;
import fr.minobot.ui.components.buttons.CloseCross;
import fr.minobot.ui.components.buttons.PrimaryButton;
import fr.minobot.ui.components.buttons.SecondaryButton;
import fr.minobot.ui.components.containers.Card;
import fr.minobot.ui.components.labels.SectionHeader;
import fr.minobot.ui.overlay.characters.CharacterList;
import fr.minobot.ui.overlay.characters.clazz.ClassIcons;
import fr.minobot.ui.overlay.characters.clazz.ClassPicker;
import fr.minobot.ui.overlay.keybinds.KeybindsDrawer;
import fr.minobot.ui.overlay.preferences.Preferences;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;
import fr.minobot.win32.Rect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * The panel, in Swing — the orchestrator that owns the window and lays the sections out on it. The
 * drawing of each part lives in its own class ({@link LogoTile}, {@link CharacterList},
 * {@link KeybindsDrawer}, {@link ClassPicker}, {@link Preferences}, {@link SizeSlider}); this wires
 * them onto the {@link Sheet} and keeps the panel's transient state — whether the keybinds drawer is
 * open, and which character the class picker is open for.
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
 * <p><strong>Every size is a natural size, and none of them is a pixel.</strong> The panel covers the
 * game, and the game is played on screens of every size; each size reaches the screen multiplied by the
 * player's scale through a {@link Scale} — {@link Scale#px} for a length, {@link Scale#font} for a
 * typeface. A size that skips them is a size that will be wrong on somebody's monitor.
 *
 * <p>Two threads meet here, and neither may do the other's work. Swing lives on the event dispatch
 * thread, while {@code toggle()} arrives on the virtual thread of a hotkey — so every method below
 * hands its work to the EDT. And {@code captureHotkey()} <em>blocks</em>, waiting for a human, so it is
 * the one thing that must leave the EDT — see {@link KeybindsDrawer}.
 */
public final class SwingOverlay implements OverlayView {

    private static final Logger log = LoggerFactory.getLogger(SwingOverlay.class);

    /**
     * The card, at scale 1. The window is as wide as the game; the card is not.
     *
     * <p>The drawer beside it has no width of its own: it takes the one its rows need. A number here
     * would be a number to keep in step with the longest feature label, and it would lose.
     */
    private static final int CARD_WIDTH = 420;

    /** The close cross in the card's corner: the mouse's way out, where the hotkey is the keyboard's. */
    private static final int CLOSE_SIZE = 24;

    /** Room for about five characters; beyond that the list scrolls rather than the card growing. */
    private static final int CHARACTER_LIST_HEIGHT = 130;

    private final OverlayActions actions;

    /** Touched on the event dispatch thread only. Built on first show: there may never be one. */
    private JWindow window;
    private Font baseFont;

    // The sections, each drawing one part of the panel. Built once, on first show; asked for a fresh
    // component at each lay, at the scale of the moment.
    private LogoTile logoTile;
    private CharacterList characterList;
    private KeybindsDrawer keybindsDrawer;
    private ClassPicker classPicker;
    private Preferences preferences;
    private SizeSlider sizeSlider;

    private JPanel card;
    private JPanel keybinds;
    private JComponent classPickerComp;

    /**
     * The character the class picker is open for, or {@code null} when it is closed. The picker is a
     * modal over the whole sheet, so it is the panel's state, not the player's: nothing on disk remembers
     * a half-made choice.
     */
    private String classPickerFor;

    /** What every size on the cards was computed with. A change to it is a card built again. */
    private Scale scale;

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
        if (fits < scale.factor()) {
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
    private void lay(OverlayContent content, double factor, Rect bounds) {
        this.scale = new Scale(factor, baseFont);

        card = characterCard(content);
        keybinds = keybindsDrawer.build(scale, content);
        keybinds.setVisible(keybindsOpen);
        classPickerComp = classPickerFor == null ? null : classPicker.build(scale, classPickerFor, content);
        window.setContentPane(new Sheet());

        characterList.fill(content);

        shrinkListToFit(bounds);
    }

    private void build() {
        window = new JWindow();
        window.setAlwaysOnTop(true);

        // The whole point: the panel receives the mouse, and never the activation.
        window.setFocusableWindowState(false);

        translucentIfSupported();

        baseFont = new JLabel().getFont();

        final var classIcons = new ClassIcons();
        logoTile = new LogoTile();
        characterList = new CharacterList(actions, classIcons, this::openClassPicker);
        keybindsDrawer = new KeybindsDrawer(actions, this::redraw);
        classPicker = new ClassPicker(actions, classIcons, this::dismissClassPicker, this::chooseClass);
        preferences = new Preferences(actions);
        sizeSlider = new SizeSlider(actions);
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

    // ------------------------------------------------------------------ the card

    /** The panel proper: the application, the characters, the combat switches, and the size. */
    private JPanel characterCard(OverlayContent content) {
        final var innerWidth = px(CARD_WIDTH - 2 * Metrics.PADDING);

        final var card = Card.column(scale, Theme.BACKGROUND);
        card.setBorder(new EmptyBorder(px(Metrics.PADDING), px(Metrics.PADDING),
                px(Metrics.PADDING), px(Metrics.PADDING)));

        card.add(logoTile.build(scale, innerWidth));
        card.add(Box.createVerticalStrut(px(Metrics.PADDING)));
        card.add(new SectionHeader(scale, "Characters", keybindsButton()));
        card.add(characterList.build(scale, innerWidth));
        card.add(Box.createVerticalStrut(px(Metrics.GAP)));
        card.add(characterList.footer(scale));

        preferences.addTo(card, scale, content);

        card.add(Box.createVerticalStrut(px(Metrics.PADDING)));
        card.add(sizeSlider.build(scale));
        return card;
    }

    /** The drawer's switch. It says which way it will go, which is all a chevron is for. */
    private JButton keybindsButton() {
        final JButton button = keybindsOpen
                ? new PrimaryButton(scale, "Keybinds ‹")
                : new SecondaryButton(scale, "Keybinds ›");
        button.addActionListener(_ -> {
            keybindsOpen = !keybindsOpen;
            redraw();
        });
        return button;
    }

    // ------------------------------------------------------------------ the class picker's open state

    /** Opens the class picker for a character — the picker's open/closed state is the panel's, not disk's. */
    private void openClassPicker(String character) {
        classPickerFor = character;
        redraw();
    }

    private void dismissClassPicker() {
        classPickerFor = null;
        redraw();
    }

    /**
     * Pins a class and closes the picker. The picker is cleared first, so the redraw the assignment
     * triggers finds it already shut; the assignment persists on its own thread through the controller.
     */
    private void chooseClass(String character, DofusClass clazz) {
        classPickerFor = null;
        actions.assignClass(character, clazz);
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
        private final JButton close = CloseCross.button(scale, SwingOverlay.this::hide);

        private Sheet() {
            setLayout(null);
            setOpaque(false);
            // Swing paints index 0 last, so the first added sits on top. The picker, while open, must
            // cover everything and be the only thing to click — so it goes in first, ahead of the close
            // cross, which itself goes ahead of the card it rests in the corner of.
            if (classPickerComp != null) {
                add(classPickerComp);
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
                final var past = left + main.width + px(Metrics.GAP) + drawer.width
                        - (getWidth() - px(Metrics.PADDING));
                left = Math.max(px(Metrics.PADDING), left - Math.max(0, past));
            }

            card.setBounds(left, top, main.width, main.height);
            keybinds.setBounds(left + main.width + px(Metrics.GAP), top, drawer.width, drawer.height);

            // Pinned to the card's top-right corner, and it follows when the card slides for the drawer.
            final var size = px(CLOSE_SIZE);
            close.setBounds(left + main.width - px(Metrics.GAP) - size, top + px(Metrics.GAP), size, size);

            // The picker covers the whole sheet: it dims what is behind it and catches every click.
            if (classPickerComp != null) {
                classPickerComp.setBounds(0, 0, getWidth(), getHeight());
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            final var canvas = (Graphics2D) graphics.create();
            canvas.setColor(Theme.BACKDROP);
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
            listHeight(Math.max(characterList.rowHeight(), full - overflow));
        }
    }

    private void listHeight(int height) {
        characterList.resizeTo(height);
        card.revalidate();
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
            return scale.factor();
        }
        return Math.max(Config.MIN_OVERLAY_SCALE, scale.factor() * fits);
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
                ? px(Metrics.GAP) + keybinds.getPreferredSize().width
                : 0;
        return card.getPreferredSize().width + drawer;
    }

    /** What the game leaves the cards, once the sheet has kept its margin around them. */
    private Dimension room(Rect bounds) {
        return new Dimension(bounds.width() - 2 * px(Metrics.PADDING),
                bounds.height() - 2 * px(Metrics.PADDING));
    }

    private void redraw() {
        if (lastContent != null && lastBounds != null) {
            draw(lastContent, lastBounds);
        }
    }

    /** A natural length, at the size the player asked for — the orchestrator's own shorthand for its layout. */
    private int px(int natural) {
        return scale.px(natural);
    }
}
