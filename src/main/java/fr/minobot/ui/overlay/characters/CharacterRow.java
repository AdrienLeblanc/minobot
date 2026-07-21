package fr.minobot.ui.overlay.characters;

import fr.minobot.core.domain.Character;
import fr.minobot.ui.CharacterEntry;
import fr.minobot.ui.OverlayContent;
import fr.minobot.ui.Theme;
import fr.minobot.ui.components.labels.Chip;
import fr.minobot.ui.overlay.characters.clazz.ClassIcons;
import fr.minobot.ui.utils.Draw;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;
import javax.swing.border.EmptyBorder;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * A character on a tile of their own, so a row can be picked up by eye before it is by hand: their rank
 * as read off the panel, their name, a status chip for whether their window is open, and the class they
 * are pinned to.
 *
 * <p>The geometry it draws — where the forget cross sits, how wide the class cell is — is shared with
 * {@link CharacterList}, whose click routing has to land on the very things drawn here; a single set of
 * constants keeps the two in step.
 */
public final class CharacterRow extends DefaultListCellRenderer {

    /** The class shown on a row: a small icon, and the room its cell is given at the right. */
    static final int CLASS_ICON = 18;
    static final int CLASS_CELL_WIDTH = 104;

    /** The forget cross at the right of a disconnected row — the room it takes, so the class cell clears it. */
    static final int FORGET_SIZE = 16;

    /** Captured at build, and never changing under a row already on screen — the surface rebuilds instead. */
    private final Scale scale;
    private final ClassIcons classIcons;

    /** Never {@code getBackground()}: with none of its own, a component answers with its parent's. */
    private boolean highlighted;

    /** The row's character, kept apart from the numbered text, to draw their class and sex from. */
    private Character character = new Character("");

    /** Whether their game window is open: a grey chip and a forget cross say when it is not. */
    private boolean connected = true;

    public CharacterRow(Scale scale, ClassIcons classIcons) {
        this.scale = scale;
        this.classIcons = classIcons;
    }

    @Override
    public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                  boolean selected, boolean focused) {
        super.getListCellRendererComponent(list, value, index, selected, focused);

        final var entry = (CharacterEntry) value;
        character = entry.character();
        connected = entry.connected();
        // The number is the row's rank read off the panel — 1 at the top — and no more: it is a
        // mirror of the order, not a name, so a drag that reorders the list renumbers it for free.
        setText((index + 1) + " - " + character.name());

        highlighted = selected;
        setOpaque(false);
        // A disconnected character is dimmed to the same grey as its chip: still legible, plainly not
        // playing right now.
        setForeground(connected ? Theme.TEXT : Theme.MUTED);
        setFont(scale.font(Metrics.BODY, Metrics.PLAIN));
        setBorder(new EmptyBorder(0, scale.px(Metrics.GAP + 2), 0, scale.px(Metrics.GAP)));
        return this;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        final var canvas = Draw.smooth(graphics);
        final var inset = scale.px(1);

        canvas.setColor(highlighted ? Theme.SELECTED : Theme.SURFACE);
        canvas.fillRoundRect(0, inset, getWidth(), getHeight() - 2 * inset,
                scale.px(Metrics.RADIUS), scale.px(Metrics.RADIUS));

        if (highlighted) {
            // The grip: three bars where a row is taken hold of, and a hint that it can be.
            canvas.setColor(Theme.ACCENT);
            canvas.fillRoundRect(0, inset, scale.px(3), getHeight() - 2 * inset, scale.px(3), scale.px(3));
        }

        // The forget cross sits at the far right of a disconnected row; the class cell stops short of
        // it so the two never overlap. A connected row keeps its whole width for its class.
        final var forgetRoom = connected ? 0 : scale.px(Metrics.GAP) + scale.px(FORGET_SIZE);
        if (!connected) {
            Draw.cross(canvas, getWidth() - scale.px(Metrics.GAP) - scale.px(FORGET_SIZE),
                    (getHeight() - scale.px(FORGET_SIZE)) / 2, scale.px(FORGET_SIZE),
                    Theme.MUTED, scale.px(2));
        }

        // The status chip follows the name, whose pixel width we measure to place it after. Nothing for
        // the login placeholder — a window with no character has no connection of its own to state.
        if (!character.name().equals(OverlayContent.LOGGING_IN)) {
            final var fm = canvas.getFontMetrics(scale.font(Metrics.BODY, Metrics.PLAIN));
            final var nameEnd = scale.px(Metrics.GAP + 2) + fm.stringWidth(getText());
            Chip.paint(scale, canvas, connected ? Theme.CONNECTED : Theme.MUTED,
                    connected ? "connected" : "disconnected", nameEnd + scale.px(Metrics.GAP), getHeight());
        }

        // The class sits at the right of the row: its icon and name once chosen, an invitation to
        // choose one until then. Drawn before the name text (super) — the two never share the row's
        // width, the name being short and left, the class right — so neither writes over the other.
        paintClassCell(canvas, character, getWidth() - forgetRoom, getHeight());
        canvas.dispose();

        super.paintComponent(graphics);
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

        final var right = width - scale.px(Metrics.GAP);
        final var metrics = canvas.getFontMetrics(scale.font(Metrics.SMALL, Metrics.PLAIN));
        final var baseline = (height + metrics.getAscent() - metrics.getDescent()) / 2;

        final var clazz = character.clazz();
        if (clazz == null) {
            canvas.setFont(scale.font(Metrics.SMALL, Metrics.PLAIN));
            canvas.setColor(Theme.MUTED);
            final var text = "choose class…";
            canvas.drawString(text, right - metrics.stringWidth(text), baseline);
            return;
        }

        final var size = scale.px(CLASS_ICON);
        final var iconX = right - size;
        classIcons.paint(scale, canvas, clazz, character.sexOrDefault(), iconX, (height - size) / 2, size);

        canvas.setFont(scale.font(Metrics.SMALL, Metrics.PLAIN));
        canvas.setColor(Theme.TEXT);
        canvas.drawString(clazz.label(), iconX - scale.px(4) - metrics.stringWidth(clazz.label()), baseline);
    }
}
