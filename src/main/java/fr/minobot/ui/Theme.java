package fr.minobot.ui;

import java.awt.Color;

/**
 * The colours the panel is drawn in, in one place so every Swing surface draws from the same vocabulary
 * rather than each carrying its own literals.
 *
 * <p>It is a <strong>charcoal-and-ember</strong> theme: a warm near-black the game shows through, and one
 * accent — the ember {@link #ACCENT} — spent only on what is <em>live</em>: the character being cycled,
 * a switch that is on, a class nobody has picked yet. Everything else is said in the grey ladder below,
 * and the discipline that makes the accent read at all is that it is rationed. <strong>A second accent
 * colour is not a colour, it is a competing claim on the player's eye</strong>; the two exceptions each
 * earn their place by meaning something no grey can say — {@link #CONNECTED}, a window that is open
 * right now, and {@link #WHISPER}, somebody talking to you.
 *
 * <p>The greys are a <em>ladder</em>, not a set: {@link #TEXT} is read first and {@link #GHOST} is read
 * last, and a surface picks the rung that matches how loudly the thing should speak. Reaching for a
 * literal instead is how two rows that mean the same end up a shade apart.
 *
 * <p>A surface that is deliberately <em>not</em> this theme keeps its own colours rather than bending
 * these; sharing a palette is for surfaces that share a look, not a rule that every surface must look
 * the same.
 */
public final class Theme {

    // ------------------------------------------------------------------ the surfaces, darkest first

    /** The dim laid over the game so the panel stands out, without hiding the character behind it. */
    public static final Color BACKDROP = new Color(6, 5, 5, 170);

    /** The panel's own sheet: the darkest surface, what every card rests on. */
    public static final Color BACKGROUND = new Color(11, 9, 8, 246);

    /** A card set on the sheet — the team, the settings, the drawer. */
    public static final Color SURFACE = new Color(18, 16, 16);

    /** One step up again: a tile, a chip, a quiet button picked out on a card. */
    public static final Color RAISED = new Color(28, 23, 22);

    /** A character's row while their window is open. */
    public static final Color ROW = new Color(29, 24, 23);

    /** A character's row while it is not — the same shape, plainly asleep. */
    public static final Color ROW_QUIET = new Color(22, 18, 17);

    /** Anything under the pointer. */
    public static final Color HOVER = new Color(36, 30, 28);

    // ------------------------------------------------------------------ the lines

    /** The hairline drawn around a card or a tile. */
    public static final Color EDGE = new Color(42, 34, 32);

    /** An edge meant to be seen: the row of a character on screen, the picker over the panel. */
    public static final Color EDGE_STRONG = new Color(58, 46, 43);

    /** The rule between two sections of a card — quieter than an edge, it separates rather than encloses. */
    public static final Color RULE = new Color(34, 28, 27);

    // ------------------------------------------------------------------ the grey ladder, loudest first

    /** Read first: a character's name, a heading, the message of a banner. */
    public static final Color TEXT = new Color(242, 236, 232);

    /** Read next: an activity line, a quiet button's label. */
    public static final Color TEXT_SOFT = new Color(216, 207, 202);

    /** A value beside a label — a class, a whisper's line, the name of a character not playing. */
    public static final Color MUTED = new Color(168, 157, 152);

    /** A hint, a caption: there to be found, not to be read. */
    public static final Color FAINT = new Color(140, 128, 123);

    /** A section's heading, and a feature nobody bound a key to. */
    public static final Color DIM = new Color(109, 99, 95);

    /** Read last: a timestamp, a drag handle, the index of a character not playing. */
    public static final Color GHOST = new Color(90, 81, 78);

    // ------------------------------------------------------------------ the ember, and the two it allows

    /** The one accent: what is live — the cycled character, a switch that is on, a class to pick. */
    public static final Color ACCENT = new Color(226, 84, 46);

    /** The ember under the pointer, and the ember used as text, where the fill would be too dark to read. */
    public static final Color ACCENT_HOVER = new Color(232, 115, 74);

    /** A wash of the ember: the fill of a control that is on, which must tint its card and not shout. */
    public static final Color ACCENT_WASH = new Color(226, 84, 46, 36);

    /** The edge of that same control, so an on switch is outlined as well as tinted. */
    public static final Color ACCENT_EDGE = new Color(226, 84, 46, 115);

    /** A character whose window is open: the one green on the panel, and it says only that. */
    public static final Color CONNECTED = new Color(121, 196, 143);

    /** Somebody is talking to you — the whisper card's heading, and nothing else. */
    public static final Color WHISPER = new Color(240, 160, 56);

    private Theme() {
    }
}
