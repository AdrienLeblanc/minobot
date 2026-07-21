package fr.minobot.ui.overlay.characters;

import fr.minobot.ui.utils.Scale;

/**
 * The four columns a character row is divided into — name, status, class, actions — resolved from their
 * natural widths to pixels against the row's actual width. Shared by {@link CharacterRow}, which draws one
 * cell per column, and {@link CharacterList}, whose click routing must land on the very cells drawn: a
 * single layout object, so the drawing and the hit-testing cannot drift a pixel apart.
 *
 * <p>Three columns take a fixed width; the <strong>name</strong> column takes whatever room is left (and
 * elides a name too long for it), so the four always tile the row exactly. The <strong>actions</strong>
 * column is reserved whether or not it holds anything, so the columns to its left keep their place between
 * a connected row and a disconnected one.
 */
final class RowColumns {

    /** Natural widths of the fixed columns. The name column flexes to fill the rest of the row. */
    private static final int STATUS_WIDTH = 96;
    private static final int CLASS_WIDTH = 104;
    private static final int ACTIONS_WIDTH = 40;

    final int nameX;
    final int nameWidth;
    final int statusX;
    final int statusWidth;
    final int classX;
    final int classWidth;
    final int actionsX;
    final int actionsWidth;

    /**
     * @param totalWidth the row's actual width in pixels; the name column absorbs whatever the three
     *                   fixed columns leave, so the four columns always tile the row exactly.
     */
    RowColumns(Scale scale, int totalWidth) {
        statusWidth = scale.px(STATUS_WIDTH);
        classWidth = scale.px(CLASS_WIDTH);
        actionsWidth = scale.px(ACTIONS_WIDTH);

        nameX = 0;
        nameWidth = totalWidth - statusWidth - classWidth - actionsWidth;
        statusX = nameX + nameWidth;
        classX = statusX + statusWidth;
        actionsX = classX + classWidth;
    }

    /** Whether an {@code x} — measured from the row's left edge — falls in the class column. */
    boolean inClass(int x) {
        return x >= classX && x < actionsX;
    }

    /** Whether an {@code x} — measured from the row's left edge — falls in the actions column. */
    boolean inActions(int x) {
        return x >= actionsX;
    }
}
