package fr.minobot.ui;

import fr.minobot.ui.toast.SwingToastStack;

import java.awt.Color;

/**
 * The colours the panel is drawn in, in one place so every Swing surface draws from the same vocabulary
 * rather than each carrying its own literals.
 *
 * <p>This is the dark control-surface theme the overlay proposed — a dim over the game, a card on top of
 * it, and one accent. It was born inside {@code SwingOverlay}; it lives here so the next surface that
 * wants the same look ({@link SwingToastStack}, and features to come) reaches for {@code Palette.ACCENT}
 * instead of copying {@code new Color(122, 168, 255)} and drifting a shade off it.
 *
 * <p>A surface that is deliberately <em>not</em> this theme — the whisper card is a light one, the way the
 * game's own toasts read — keeps its own colours rather than bending these; sharing a palette is for
 * surfaces that share a look, not a rule that every surface must look the same.
 */
public final class Theme {

    /** The dim laid over the game so the card stands out, without hiding the character behind it. */
    public static final Color BACKDROP = new Color(6, 8, 12, 150);

    /** The card's own fill: the darkest surface, what everything else sits on. */
    public static final Color BACKGROUND = new Color(22, 24, 30, 243);

    /** One step up from the card — a tile, a pill, a row picked out on it. */
    public static final Color SURFACE = new Color(33, 36, 44);

    /** A surface under the pointer. */
    public static final Color HOVER = new Color(48, 53, 64);

    /** The hairline drawn around a card or a tile. */
    public static final Color EDGE = new Color(50, 55, 67);

    /** Body text, and anything meant to be read first. */
    public static final Color TEXT = new Color(232, 235, 241);

    /** Secondary text — a hint, a caption, a key nobody bound. */
    public static final Color MUTED = new Color(124, 131, 145);

    /** The row the player has picked out in a list. */
    public static final Color SELECTED = new Color(46, 52, 64);

    /** The one colour that is not a grey: a bound key, an on switch, the thing the eye should land on. */
    public static final Color ACCENT = new Color(122, 168, 255);

    /** A primary color */
    public static final Color PRIMARY = new Color(48, 84, 156);
    public static final Color PRIMARY_HOVER = new Color(62, 104, 184);

    /** A secondary color */
    public static final Color SECONDARY = new Color(150, 181, 234);
    public static final Color SECONDARY_HOVER = new Color(174, 200, 245);

    /** A tertiary color */
    public static final Color TERTIARY = new Color(242, 244, 245);
    public static final Color TERTIARY_HOVER = new Color(167, 180, 188);

    /** A character whose window is open: the green of a status chip that reads "here right now". */
    public static final Color CONNECTED = new Color(122, 199, 140);

    private Theme() {
    }
}
