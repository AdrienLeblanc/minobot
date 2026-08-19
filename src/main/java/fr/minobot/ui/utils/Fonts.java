package fr.minobot.ui.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;

/**
 * The six typefaces the panel is set in, read once off the classpath.
 *
 * <p>The surfaces name a <strong>weight</strong>, not a style bit: {@code Font.BOLD} asks Java2D to
 * embolden a regular face by smearing it, and at the sizes a hint or a keybind chip is drawn that smear
 * is what the letters read as. A real Medium and a real SemiBold are two files, so they are shipped —
 * which is also why {@link Scale#font(Font, float)} takes a face and only ever derives its size.
 *
 * <p>They are <strong>resources, not loose files</strong>, exactly as {@code logo.png} and the class
 * icons are: they ride inside the jar, so a packaged {@code Minobot.exe} unpacked anywhere still finds
 * them. A font that cannot be read is not a failure — the panel falls back to the desktop's own, the way
 * the logo falls back to its wordmark — so a missing file costs a warning and nothing else.
 *
 * <p>Every face here is at size 1 and plain: it is a <em>face</em>, not a size. Nothing draws with one
 * directly; it is handed to a {@code Scale}, which is what turns it into pixels on the player's monitor.
 */
public final class Fonts {

    private static final Logger log = LoggerFactory.getLogger(Fonts.class);

    /** Where the typefaces live on the classpath. */
    private static final String DIRECTORY = "/fonts/";

    /** Body text, and anything read as a sentence. */
    public static final Font REGULAR = load("Barlow-Regular.ttf", Font.SANS_SERIF, Font.PLAIN);

    /** A name, a label, a value — the weight most of the panel is set in. */
    public static final Font MEDIUM = load("Barlow-Medium.ttf", Font.SANS_SERIF, Font.PLAIN);

    /** What is meant to be read first: a character's name, a button, a switch. */
    public static final Font SEMIBOLD = load("Barlow-SemiBold.ttf", Font.SANS_SERIF, Font.BOLD);

    /**
     * The all-caps section headings — {@code TEAM}, {@code ACTIVITY}, {@code MINOBOT}. Semi-condensed, so
     * a heading spread by {@link Metrics#HEADING_TRACKING} still takes less room than the row it names.
     */
    public static final Font CONDENSED = load("BarlowSemiCondensed-Bold.ttf", Font.SANS_SERIF, Font.BOLD);

    /**
     * Figures and keys: the cycle index, a timestamp, a keybind chip.
     *
     * <p>Monospaced because those three are read as <em>columns</em> — {@code 01} above {@code 02}, one
     * timestamp above the next — and a proportional digit makes a column that does not line up.
     */
    public static final Font MONO = load("JetBrainsMono-Medium.ttf", Font.MONOSPACED, Font.PLAIN);

    /** The same, for the figure that carries its row: the index of a connected character. */
    public static final Font MONO_BOLD = load("JetBrainsMono-Bold.ttf", Font.MONOSPACED, Font.BOLD);

    /**
     * One typeface off the classpath, or the desktop's nearest equivalent when it cannot be read.
     *
     * @param fallbackFamily a logical family ({@link Font#SANS_SERIF}, {@link Font#MONOSPACED}), which
     *                       every desktop resolves to something, unlike a name like {@code "Barlow"}
     */
    private static Font load(String file, String fallbackFamily, int fallbackStyle) {
        try (final InputStream stream = Fonts.class.getResourceAsStream(DIRECTORY + file)) {
            if (stream == null) {
                log.warn("No {}{} on the classpath: falling back to the desktop's own typeface.",
                        DIRECTORY, file);
                return new Font(fallbackFamily, fallbackStyle, 1);
            }
            return Font.createFont(Font.TRUETYPE_FONT, stream);
        } catch (IOException | FontFormatException e) {
            log.warn("Could not read the typeface {}{}: {}", DIRECTORY, file, e.getMessage());
            return new Font(fallbackFamily, fallbackStyle, 1);
        }
    }

    private Fonts() {
    }
}
