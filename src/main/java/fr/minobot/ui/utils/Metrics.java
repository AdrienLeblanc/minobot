package fr.minobot.ui.utils;

/**
 * The design tokens the themed surfaces share — the spacing rhythm, the corner radii and the type scale,
 * in one place so every card, row and label is spaced and sized from the same vocabulary rather than each
 * carrying its own literals.
 *
 * <p>These are <strong>natural</strong> sizes, before {@link Scale}: a token is what a size <em>means</em>
 * ({@code GAP} between two rows, {@code SMALL} for a hint), and {@code Scale.px}/{@code Scale.font} is
 * what turns it into pixels on the player's monitor. A surface with a size of its own — the width of a
 * card, the height of a whisper card — keeps that size next to the code it governs; only the shared
 * rhythm lives here.
 *
 * <p>The radii are a <strong>nesting order</strong>, not a list of tastes: the further inside the panel a
 * shape sits, the tighter its corner. A tile drawn at the sheet's radius reads as a second panel rather
 * than as something resting on the first.
 */
public final class Metrics {

    // ------------------------------------------------------------------ the spacing rhythm

    /** The gap between two things that belong together — a row and the next, a label and its control. */
    public static final int GAP = 8;

    /** The gap between two things that merely sit side by side — one card and the card beside it. */
    public static final int GUTTER = 14;

    /** The breathing room a card keeps inside its edge. */
    public static final int PADDING = 16;

    /** The height of a control row — a section heading, a keybind, the size slider. */
    public static final int ROW = 26;

    // ------------------------------------------------------------------ the radii, outermost first

    /** The sheet the whole panel is drawn on. */
    public static final int RADIUS_SHEET = 18;

    /** A card resting on that sheet — the team, the settings, the drawer, the picker. */
    public static final int RADIUS = 14;

    /** Something resting on a card: a character's row, a class tile, a button. */
    public static final int RADIUS_TILE = 9;

    /** The tightest of all: a key chip, a small badge. */
    public static final int RADIUS_CHIP = 6;

    // ------------------------------------------------------------------ the type scale, largest first

    /** A drawer's or a picker's own title — the largest text the panel sets. */
    public static final float TITLE = 15f;

    /** The {@code MINOBOT} wordmark in the header. */
    public static final float WORDMARK = 14f;

    /** A character's name, a whisper's line: what the panel is actually about. */
    public static final float BODY = 13f;

    /** A control's label, an activity line — read as text, but not first. */
    public static final float LABEL = 12f;

    /** A hint, a class's name, a cycle index, a key chip. */
    public static final float SMALL = 11f;

    /** The tracked all-caps headings, and the {@code ON}/{@code OFF} beside a switch. */
    public static final float HEADING = 10f;

    /** The name under a class tile in the picker, where twelve of them must fit six to a row. */
    public static final float TINY = 9f;

    // ------------------------------------------------------------------ the tracking

    /** How far a section heading's letters are spread — what sets it apart, rather than a rule or a box. */
    public static final double HEADING_TRACKING = 0.18;

    /** The same idea, tighter, for a word inside a control: a state, a card's own heading. */
    public static final double BADGE_TRACKING = 0.14;

    private Metrics() {
    }
}
