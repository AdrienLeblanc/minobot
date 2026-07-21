package fr.minobot.ui.utils;

import java.awt.Font;

/**
 * The design tokens the themed surfaces share — the spacing rhythm and the type scale, in one place so
 * every card, row and label is spaced and sized from the same vocabulary rather than each carrying its
 * own literals.
 *
 * <p>These are <strong>natural</strong> sizes, before {@link Scale}: a token is what a size <em>means</em>
 * ({@code GAP} between two rows, {@code SMALL} for a hint), and {@code Scale.px}/{@code Scale.font} is
 * what turns it into pixels on the player's monitor. A surface with a size of its own — the width of a
 * card, the height of the logo band — keeps that size next to the code it governs; only the shared rhythm
 * lives here.
 */
public final class Metrics {

    /** The gap between two things that belong together — a row and the next, a label and its control. */
    public static final int GAP = 8;

    /** The breathing room a card keeps inside its edge. */
    public static final int PADDING = 16;

    /** The radius the app's corners are rounded to — a card, a pill, a tile. */
    public static final int RADIUS = 12;

    /** The height of a control row — a section heading, a keybind, the size slider. */
    public static final int ROW = 26;

    // The type scale, in natural points. Read first is largest.
    public static final float LOGO = 17f;
    public static final float BODY = 12f;
    public static final float SMALL = 11f;
    public static final float HEADING = 10f;

    /** The tracking a heading's letters are spread by, so it is set apart without a rule or a box. */
    public static final double HEADING_TRACKING = 0.14;

    /** The style bit for the bold weight, so a caller need not reach for {@link Font} for the common case. */
    public static final int BOLD = Font.BOLD;
    public static final int PLAIN = Font.PLAIN;

    private Metrics() {
    }
}
