package fr.minobot.ui.overlay.characters;

import fr.minobot.ui.CharacterEntry;
import fr.minobot.ui.OverlayActions;
import fr.minobot.ui.OverlayContent;
import fr.minobot.ui.components.tables.SmartTable;
import fr.minobot.ui.overlay.characters.clazz.ClassIcons;
import fr.minobot.ui.utils.Scale;

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
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

    /** The height of one character's tile — room for a 22-pixel class icon and its breathing space. */
    private static final int ROW_HEIGHT = 32;

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

    /** The settled order, reported to the cycler as character names — the table's job was only to move rows. */
    private void reorder(List<CharacterEntry> ordered) {
        actions.reorder(ordered.stream().map(entry -> entry.character().name()).toList());
    }

    /**
     * A plain click is not a reorder. The forget cross is tested first — it sits at the far right of a
     * disconnected row, where a connected one carries only its status dot — then the class cell: a click
     * there is a request to choose that character's class. Anywhere else, the list has already selected
     * the row.
     */
    private void onRowClick(JList<CharacterEntry> list, int index, Point point) {
        if (!maybeForget(list, index, point)) {
            maybeOpenClassPicker(list, index, point);
        }
    }

    /** Forgets the character when the click landed on the cross drawn in a disconnected row's status cell. */
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
        if (cell != null && new RowColumns(scale, cell.width).inStatus(point.x - cell.x)) {
            actions.forget(entry.character().name());
            return true;
        }
        return false;
    }

    /** Opens the picker when the click landed in the row's class column, on a real character. */
    private void maybeOpenClassPicker(JList<CharacterEntry> list, int index, Point point) {
        if (index < 0 || index >= model.size()) {
            return;
        }
        final var character = model.get(index).character();
        if (character.name().equals(OverlayContent.LOGGING_IN)) {
            return; // a not-yet-logged-in window has no character to give a class to
        }

        final var cell = list.getCellBounds(index, index);
        if (cell != null && new RowColumns(scale, cell.width).inClass(point.x - cell.x)) {
            openPicker.accept(character.name());
        }
    }
}
