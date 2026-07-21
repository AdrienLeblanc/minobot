package fr.minobot.ui.overlay.characters;

import fr.minobot.ui.CharacterEntry;
import fr.minobot.ui.OverlayActions;
import fr.minobot.ui.OverlayContent;
import fr.minobot.ui.components.buttons.SecondaryButton;
import fr.minobot.ui.components.labels.Hint;
import fr.minobot.ui.components.tables.SmartTable;
import fr.minobot.ui.overlay.characters.clazz.ClassIcons;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import javax.swing.Box;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.util.List;
import java.util.function.Consumer;

/**
 * The characters, on tiles of their own, dragged into the order the cycler follows. It owns the list
 * model and decides what a click on a row means; the scrollable, reorderable shell it lives in — and
 * the raw mouse handling that shell needs — is a reusable {@link SmartTable}.
 *
 * <p>Every click is routed by hand — a drag reorders, a click on the class cell opens the picker, a click
 * on a disconnected row's forget cross drops it — because the window this list lives on never takes the
 * focus, and Swing's own drag-and-drop and selection machinery expects a window that does. The
 * {@link SmartTable} does the routing; this class supplies the meaning.
 */
public final class CharacterList {

    /** Room for about five characters; beyond that the list scrolls rather than the card growing. */
    private static final int ROW_HEIGHT = 26;

    private final OverlayActions actions;
    private final ClassIcons classIcons;

    /** Opens the class picker for a character — the orchestrator owns the picker's open/closed state. */
    private final Consumer<String> openPicker;

    private final DefaultListModel<CharacterEntry> model = new DefaultListModel<>();

    /** Rebuilt each lay; read by the click routing for the cell sizes it measures against. */
    private Scale scale;

    /** The shell the rows live in, kept so the orchestrator can shrink it as the game leaves less room. */
    private SmartTable<CharacterEntry> table;

    public CharacterList(OverlayActions actions, ClassIcons classIcons, Consumer<String> openPicker) {
        this.actions = actions;
        this.classIcons = classIcons;
        this.openPicker = openPicker;
    }

    /**
     * @param innerWidth the card's content width in pixels
     */
    public JComponent build(Scale scale, int innerWidth) {
        this.scale = scale;
        table = new SmartTable<>(model, scale, innerWidth, ROW_HEIGHT,
                new CharacterRow(scale, classIcons), this::reorder, this::onRowClick);
        return table;
    }

    /** The line under the list: what a drag does on the left, and the button that re-reads the desktop. */
    public JComponent footer(Scale scale) {
        final var row = new JPanel(new BorderLayout(scale.px(Metrics.GAP), 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, scale.px(Metrics.ROW)));
        row.add(new Hint(scale, "drag to reorder"), BorderLayout.WEST);
        row.add(reloadButton(scale), BorderLayout.EAST);
        return row;
    }

    /** Fills the list with the characters to show. Called after the card is assembled, as the panel does. */
    public void fill(OverlayContent content) {
        model.clear();
        content.characters().forEach(model::addElement);
    }

    /** The natural row height, in pixels, so the orchestrator can floor a shrunk list at one row. */
    public int rowHeight() {
        return table.rowHeight();
    }

    /** Sets the list's height as the orchestrator shrinks it to the room the game leaves the cards. */
    public void resizeTo(int height) {
        table.resizeTo(height);
    }

    /**
     * Re-reads the desktop, for a character opened or closed since the panel went up.
     *
     * <p><strong>Off the event dispatch thread</strong>: the refresh enumerates every window and reads
     * each title, and the panel must not freeze while it does — the same reason a capture leaves the EDT.
     * The redraw it triggers hands itself back to the EDT on its own.
     */
    private JButton reloadButton(Scale scale) {
        final var button = new SecondaryButton(scale, "Reload");
        button.addActionListener(_ ->
                Thread.ofVirtual().name("overlay-reload").start(actions::reload));
        return button;
    }

    /** The settled order, reported to the cycler as character names — the table's job was only to move rows. */
    private void reorder(List<CharacterEntry> ordered) {
        actions.reorder(ordered.stream().map(entry -> entry.character().name()).toList());
    }

    /**
     * A plain click is not a reorder. The forget cross is tested first — it sits at the far right of a
     * disconnected row, inside where a class cell otherwise reaches — then the class cell: a click there
     * is a request to choose that character's class. Anywhere else, the list has already selected the row.
     */
    private void onRowClick(JList<CharacterEntry> list, int index, Point point) {
        if (!maybeForget(list, index, point)) {
            maybeOpenClassPicker(list, index, point);
        }
    }

    /** Forgets the character when the click landed on the cross drawn at the right of a disconnected row. */
    private boolean maybeForget(JList<CharacterEntry> list, int index, Point point) {
        if (index < 0 || index >= model.size()) {
            return false;
        }
        final var entry = model.get(index);
        // The cross is drawn on disconnected characters only, and never on the login placeholder.
        if (entry.connected() || entry.character().name().equals(OverlayContent.LOGGING_IN)) {
            return false;
        }

        final var cell = list.getCellBounds(index, index);
        if (cell != null && point.x >= cell.x + cell.width
                - scale.px(Metrics.GAP) - scale.px(CharacterRow.FORGET_SIZE)) {
            actions.forget(entry.character().name());
            return true;
        }
        return false;
    }

    /** Opens the picker when the click landed in the row's class cell, on a real character. */
    private void maybeOpenClassPicker(JList<CharacterEntry> list, int index, Point point) {
        if (index < 0 || index >= model.size()) {
            return;
        }
        final var character = model.get(index).character();
        if (character.name().equals(OverlayContent.LOGGING_IN)) {
            return; // a not-yet-logged-in window has no character to give a class to
        }

        final var cell = list.getCellBounds(index, index);
        if (cell != null && point.x >= cell.x + cell.width - scale.px(CharacterRow.CLASS_CELL_WIDTH)) {
            openPicker.accept(character.name());
        }
    }
}
