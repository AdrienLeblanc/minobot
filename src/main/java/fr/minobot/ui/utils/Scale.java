package fr.minobot.ui.utils;

import java.awt.Font;
import java.awt.font.TextAttribute;
import java.util.Map;

/**
 * The one place a natural size becomes a screen size. Every length and every typeface on a themed
 * surface passes through here, multiplied by the player's {@code overlay_scale} on its way to the pixel.
 *
 * <p>Swing's own defaults were laid out for a 96-DPI desktop, which on the large screen the game is
 * played on is a panel nobody can read. So a component is never handed a pixel: it is handed a
 * {@code Scale} and a natural size, and asks this for the pixel — {@link #px} for a length,
 * {@link #font} for a typeface. <strong>A size that skips {@code px()} is a size that will be wrong on
 * somebody's monitor.</strong>
 *
 * <p>{@link #font} takes a <em>face</em> from {@link Fonts} and derives only its size. It does not take
 * a style bit: {@code Font.BOLD} on a regular face is Java2D smearing the letters, and the panel ships
 * a real Medium and a real SemiBold rather than wear that smear.
 *
 * <p>It is an immutable value: a new scale is a new {@code Scale}, handed down at build time. The
 * surfaces above rebuild rather than patch, so a component captures the scale of the moment and never
 * has to watch it change.
 */
public final class Scale {

    private final double factor;

    public Scale(double factor) {
        this.factor = factor;
    }

    /** The multiplier itself, for the rare caller that reads the scale rather than a size drawn at it. */
    public double factor() {
        return factor;
    }

    /** A natural length, at the size the player asked for. Every length on a surface goes through here. */
    public int px(int natural) {
        return (int) Math.round(natural * factor);
    }

    /**
     * A typeface at the size the player asked for.
     *
     * @param face a face from {@link Fonts} — the weight is the face's, never a style bit derived here
     */
    public Font font(Font face, float natural) {
        return face.deriveFont(natural * (float) factor);
    }

    /** Letters given room to breathe — what a heading is set apart by, rather than by a rule or a box. */
    public Font tracked(Font font, double tracking) {
        return font.deriveFont(Map.of(TextAttribute.TRACKING, tracking));
    }
}
