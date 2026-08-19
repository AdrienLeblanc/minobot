package fr.minobot.ui.components.tables;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Fonts;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * A scrollable, reorderable list of tiles — the character list's shell, made agnostic of what a row
 * holds. It owns the Swing a flat, dark, drag-to-reorder list needs: the {@link JList}, the
 * {@link SmartTableScrollBar}, and the hand-routed press/drag/release that reorders a row without Swing's
 * focus-hungry drag-and-drop (see {@code ui/CLAUDE.md}).
 *
 * <p>What a row <em>is</em> — how it draws, what a plain click on it means — is the caller's, handed in
 * as a {@link ListCellRenderer} and two callbacks: {@code onReorder} for the settled order, and
 * {@code onClick} for a click that was not a drag. So nothing here knows a character from a keybind;
 * that knowledge stays one layer up, with the section that built the table.
 *
 * @param <T> the element a row stands for
 */
public final class SmartTable<T> extends JScrollPane {

    /**
     * A plain click — not a drag — landed on a row. The {@link JList} and the row's {@code index} are
     * handed along so the caller can measure the click against the cell it drew.
     */
    public interface RowClick<T> {
        void onClick(JList<T> list, int index, Point point);
    }

    /** The card's content width, so a shrunk list keeps its width while it gives up height. */
    private final int innerWidth;

    /** The row height in pixels, so the orchestrator can floor a shrunk list at one row. */
    private final int rowHeight;

    /**
     * @param innerWidth the card's content width in pixels, kept for {@link #resizeTo}
     * @param rowHeight  the natural row height, scaled here so callers speak only natural sizes
     */
    public SmartTable(DefaultListModel<T> model, Scale scale, int innerWidth, int rowHeight,
                      ListCellRenderer<? super T> renderer,
                      Consumer<List<T>> onReorder, RowClick<T> onClick) {
        this.innerWidth = innerWidth;
        this.rowHeight = scale.px(rowHeight);

        final JList<T> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setOpaque(false);
        list.setForeground(Theme.TEXT);
        list.setFont(scale.font(Fonts.MEDIUM, Metrics.BODY));
        list.setFixedCellHeight(this.rowHeight);
        list.setCellRenderer(renderer);
        list.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        final var drag = new ReorderByDragging<>(list, model, onReorder, onClick);
        list.addMouseListener(drag);
        list.addMouseMotionListener(drag);

        setViewportView(list);
        setBorder(null);
        setOpaque(false);
        getViewport().setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        getVerticalScrollBar().setUI(new SmartTableScrollBar());
        getVerticalScrollBar().setOpaque(false);
        getVerticalScrollBar().setPreferredSize(new Dimension(scale.px(Metrics.GAP / 2 + 2), 0));
    }

    /** The natural row height, in pixels, so the orchestrator can floor a shrunk list at one row. */
    public int rowHeight() {
        return rowHeight;
    }

    /** Sets the list's height as the orchestrator shrinks it to the room the game leaves the cards. */
    public void resizeTo(int height) {
        setPreferredSize(new Dimension(innerWidth, height));
        setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    /**
     * Reordering by hand, rather than through Swing's drag and drop.
     *
     * <p>Swing's own machinery goes through the platform's drag-and-drop stack, which is a great deal
     * of native ceremony to move a row inside one list — and it is ceremony that expects a window with
     * the focus, which the surface this table lives on deliberately does not have. Press, drag, release
     * is three mouse events and no ceremony at all.
     *
     * <p>A press that never drags is a plain click: it is handed back through {@code onClick} for the
     * caller to make sense of. Selection needs no help here — the {@link JList}'s own listener does it.
     */
    private static final class ReorderByDragging<T> extends MouseAdapter {

        private final JList<T> list;
        private final DefaultListModel<T> model;
        private final Consumer<List<T>> onReorder;
        private final RowClick<T> onClick;

        private int from = -1;
        private boolean moved;

        private ReorderByDragging(JList<T> list, DefaultListModel<T> model,
                                  Consumer<List<T>> onReorder, RowClick<T> onClick) {
            this.list = list;
            this.model = model;
            this.onReorder = onReorder;
            this.onClick = onClick;
        }

        @Override
        public void mousePressed(MouseEvent event) {
            from = list.locationToIndex(event.getPoint());
            moved = false;
        }

        @Override
        public void mouseDragged(MouseEvent event) {
            final var to = list.locationToIndex(event.getPoint());
            if (from < 0 || to < 0 || to == from || to >= model.size()) {
                return;
            }

            model.add(to, model.remove(from));
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
                onReorder.accept(Collections.list(model.elements()));
                return;
            }

            onClick.onClick(list, index, event.getPoint());
        }
    }
}
