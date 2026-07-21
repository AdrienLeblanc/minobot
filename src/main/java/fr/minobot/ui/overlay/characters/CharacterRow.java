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
import java.awt.Component;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

/**
 * A character on a tile of their own, so a row can be picked up by eye before it is by hand: their rank
 * as read off the panel, their name, a status chip for whether their window is open, and the class they
 * are pinned to.
 *
 * <p>The row is divided into four columns — name, status, class, actions — by {@link RowColumns}, the one
 * layout {@link CharacterList} also reads so its click routing lands on the very cells drawn here. This
 * class draws a cell per column; how wide each column is lives in {@code RowColumns}, so the two never
 * disagree on where a cell begins.
 */
public final class CharacterRow extends DefaultListCellRenderer {

    /** The class icon's natural side, in the class column. */
    static final int CLASS_ICON = 18;

    /** The forget cross's natural side, centred in the actions column. */
    static final int FORGET_SIZE = 16;

    /** Captured at build, and never changing under a row already on screen — the surface rebuilds instead. */
    private final Scale scale;
    private final ClassIcons classIcons;

    /** Never {@code getBackground()}: with none of its own, a component answers with its parent's. */
    private boolean highlighted;

    /** The row's character, kept apart from the numbered text, to draw their class and sex from. */
    private Character character = new Character("");

    /** The rank-and-name drawn in the name cell — a mirror of the order, not stored, rebuilt each row. */
    private String rowText = "";

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
        rowText = (index + 1) + " - " + character.name();
        // The name is drawn by hand in a fixed cell (see paintComponent), so the label paints no text
        // of its own; only the selection state it carries is kept.
        setText("");

        highlighted = selected;
        setOpaque(false);
        return this;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        final var canvas = Draw.smooth(graphics);
        final var inset = scale.px(1);
        final var columns = new RowColumns(scale, getWidth());

        canvas.setColor(highlighted ? Theme.SELECTED : Theme.SURFACE);
        canvas.fillRoundRect(0, inset, getWidth(), getHeight() - 2 * inset,
                scale.px(Metrics.RADIUS), scale.px(Metrics.RADIUS));

        if (highlighted) {
            // The grip: three bars where a row is taken hold of, and a hint that it can be.
            canvas.setColor(Theme.SECONDARY);
            canvas.fillRoundRect(0, inset, scale.px(3), getHeight() - 2 * inset, scale.px(3), scale.px(3));
        }

        paintNameCell(canvas, columns);

        // The login placeholder is a window with no character yet: it has no status of its own, no class
        // to pin and nothing to forget, so the three columns to the right of the name stay empty for it.
        if (!character.name().equals(OverlayContent.LOGGING_IN)) {
            Chip.paint(scale, canvas, connected ? Theme.CONNECTED : Theme.MUTED,
                    connected ? "Connected" : "Disconnected", columns.statusX, getHeight());
            paintClassCell(canvas, columns);
            paintActionsCell(canvas, columns);
        }

        canvas.dispose();
    }

    /**
     * The rank and name, in the name column, elided if too long so it stays inside its cell instead of
     * running into the status chip. A disconnected character is dimmed to the same grey as its chip —
     * still legible, plainly not playing right now.
     */
    private void paintNameCell(Graphics2D canvas, RowColumns columns) {
        final var pad = scale.px(Metrics.GAP + 2);
        final var font = scale.font(Metrics.BODY, Metrics.PLAIN);
        final var fm = canvas.getFontMetrics(font);
        canvas.setFont(font);
        canvas.setColor(connected ? Theme.TEXT : Theme.MUTED);
        final var room = columns.nameWidth - pad - scale.px(Metrics.GAP);
        canvas.drawString(elide(fm, rowText, room),
                columns.nameX + pad, (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
    }

    /**
     * The text, cut with a trailing ellipsis if it does not fit {@code maxWidth}, so a long name stays
     * inside its cell instead of spilling into the chip that opens the next column.
     */
    private static String elide(FontMetrics fm, String text, int maxWidth) {
        if (fm.stringWidth(text) <= maxWidth) {
            return text;
        }
        final var room = maxWidth - fm.stringWidth("…");
        var end = text.length();
        while (end > 0 && fm.stringWidth(text.substring(0, end)) > room) {
            end--;
        }
        return text.substring(0, end) + "…";
    }

    /**
     * The class in its column, right-aligned against the column's edge: the icon and the class's label
     * once one is pinned, a muted {@code choose class…} until then.
     */
    private void paintClassCell(Graphics2D canvas, RowColumns columns) {
        final var height = getHeight();
        final var right = columns.classX + columns.classWidth - scale.px(Metrics.GAP);
        final var metrics = canvas.getFontMetrics(scale.font(Metrics.SMALL, Metrics.PLAIN));
        final var baseline = (height + metrics.getAscent() - metrics.getDescent()) / 2;

        final var clazz = character.clazz();
        if (clazz == null) {
            canvas.setFont(scale.font(Metrics.SMALL, Metrics.BOLD));
            canvas.setColor(Theme.MUTED);
            final var text = "Pick class…";
            canvas.drawString(text, right - metrics.stringWidth(text), baseline);
            return;
        }

        final var size = scale.px(CLASS_ICON);
        final var iconX = right - size;
        classIcons.paint(scale, canvas, clazz, character.sexOrDefault(), iconX, (height - size) / 2, size);

        canvas.setFont(scale.font(Metrics.SMALL, Metrics.BOLD));
        canvas.setColor(Theme.TEXT);
        canvas.drawString(clazz.label(), iconX - scale.px(8) - metrics.stringWidth(clazz.label()), baseline);
    }

    /**
     * The actions column: the forget cross, centred, for a disconnected character; empty for a connected
     * one. The cell is kept either way (see {@link RowColumns}), so the class column to its left does not
     * shift as a character connects or drops.
     */
    private void paintActionsCell(Graphics2D canvas, RowColumns columns) {
        if (connected) {
            return;
        }
        final var size = scale.px(FORGET_SIZE);
        Draw.cross(canvas, columns.actionsX + (columns.actionsWidth - size) / 2,
                (getHeight() - size) / 2, size, Theme.MUTED, scale.px(2));
    }
}
