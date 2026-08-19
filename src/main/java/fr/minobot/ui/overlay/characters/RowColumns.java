package fr.minobot.ui.overlay.characters;

import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

/**
 * The six columns a character row is divided into — grip, index, icon, name, class, status — resolved
 * from their natural widths to pixels against the row's actual width. Shared by {@link CharacterRow},
 * which draws one cell per column, and {@link CharacterList}, whose click routing must land on the very
 * cells drawn: a single layout object, so the drawing and the hit-testing cannot drift a pixel apart.
 *
 * <p>Five columns take a fixed width; the <strong>name</strong> takes whatever room is left (and elides a
 * name too long for it), so the six always tile the row exactly. The <strong>status</strong> column is
 * reserved whether it holds a dot or a cross, so the columns to its left keep their place between a
 * connected row and a disconnected one.
 *
 * <p>The order is the order the eye needs it in: what the row can be <em>done to</em> at the head (the
 * grip, then its rank in the cycle), what it <em>is</em> in the middle (the icon and the name), and what
 * is merely <em>true of it</em> at the tail (its class, whether it is on screen).
 */
final class RowColumns {

    /** Natural widths of the fixed columns. The name column flexes to fill the rest of the row. */
    private static final int GRIP_WIDTH = 12;
    private static final int INDEX_WIDTH = 20;
    private static final int ICON_WIDTH = 22;
    private static final int CLASS_WIDTH = 74;
    private static final int STATUS_WIDTH = 18;

    /** The room the row keeps inside its own rounded edge, left and right. */
    private static final int PADDING = 10;

    final int gripX;
    final int gripWidth;
    final int indexX;
    final int indexWidth;
    final int iconX;
    final int iconWidth;
    final int nameX;
    final int nameWidth;
    final int classX;
    final int classWidth;
    final int statusX;
    final int statusWidth;

    /**
     * @param totalWidth the row's actual width in pixels; the name column absorbs whatever the five
     *                   fixed columns leave, so the six columns always tile the row exactly.
     */
    RowColumns(Scale scale, int totalWidth) {
        final var padding = scale.px(PADDING);
        final var gap = scale.px(Metrics.GAP);

        gripWidth = scale.px(GRIP_WIDTH);
        indexWidth = scale.px(INDEX_WIDTH);
        iconWidth = scale.px(ICON_WIDTH);
        classWidth = scale.px(CLASS_WIDTH);
        statusWidth = scale.px(STATUS_WIDTH);

        gripX = padding;
        indexX = gripX + gripWidth + gap;
        iconX = indexX + indexWidth + gap;
        nameX = iconX + iconWidth + gap;
        statusX = totalWidth - padding - statusWidth;
        classX = statusX - gap - classWidth;
        nameWidth = classX - gap - nameX;
    }

    /** Whether an {@code x} — measured from the row's left edge — falls in the class column. */
    boolean inClass(int x) {
        return x >= classX && x < statusX;
    }

    /** Whether an {@code x} — measured from the row's left edge — falls in the status column. */
    boolean inStatus(int x) {
        return x >= statusX;
    }
}
