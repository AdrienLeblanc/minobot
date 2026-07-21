package fr.minobot.ui.overlay.characters.clazz;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;
import fr.minobot.core.domain.DofusClass;
import fr.minobot.core.domain.Sex;
import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * The class icons, loaded once and drawn wherever a class is shown — a character's row, and the picker's
 * tiles. One place, so the row and the tile draw the same picture and a missing file falls back the same
 * way in both.
 *
 * <p>They are vectors, rendered afresh at each size straight onto the canvas rather than off a cached
 * bitmap: as sharp at 200% as at 100%, which is the whole reason the icons are SVG.
 */
public final class ClassIcons {

    private static final Logger log = LoggerFactory.getLogger(ClassIcons.class);

    /** One document per class and sex; a pair with no SVG shipped simply stays out and draws as a badge. */
    private final Map<DofusClass, Map<Sex, SVGDocument>> icons = load();

    /**
     * A class's icon at a given size, for a sex: its SVG if one was shipped, a lettered badge if not — the
     * same fallback the logo makes to its wordmark, so a class with no art is still told apart at a glance.
     */
    public void paint(Scale scale, Graphics2D canvas, DofusClass clazz, Sex sex, int x, int y, int size) {
        final var document = icons.getOrDefault(clazz, Map.of()).get(sex);
        if (document != null) {
            final var g = (Graphics2D) canvas.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            document.render(null, g, new ViewBox(x, y, size, size));
            g.dispose();
            return;
        }

        canvas.setColor(Theme.SURFACE);
        canvas.fillRoundRect(x, y, size, size, size, size);
        canvas.setColor(Theme.SECONDARY);
        canvas.setFont(scale.font(Metrics.SMALL, Metrics.BOLD));
        final var initial = clazz.label().substring(0, 1).toUpperCase(Locale.ROOT);
        final var fm = canvas.getFontMetrics();
        canvas.drawString(initial, x + (size - fm.stringWidth(initial)) / 2,
                y + (size + fm.getAscent() - fm.getDescent()) / 2);
    }

    /**
     * Every class icon that was shipped, one per class and sex, parsed once off the classpath so a
     * packaged {@code Minobot.exe} finds each where a loose file would be lost. A pair with no SVG simply
     * stays out of the map: its absence is not a failure — {@link #paint} draws a lettered badge in its
     * place — so a missing file is a debug line, and a corrupt one does not bring the panel down.
     */
    private static Map<DofusClass, Map<Sex, SVGDocument>> load() {
        final var loader = new SVGLoader();
        final var icons = new EnumMap<DofusClass, Map<Sex, SVGDocument>>(DofusClass.class);

        for (final var clazz : DofusClass.values()) {
            final var perSex = new EnumMap<Sex, SVGDocument>(Sex.class);
            for (final var sex : Sex.values()) {
                final var path = clazz.iconResource(sex);
                final var resource = ClassIcons.class.getResource(path);
                if (resource == null) {
                    log.debug("No {}: {} {} falls back to a badge.", path, clazz.label(), sex);
                    continue;
                }
                // load() returns null on a malformed SVG and can throw on a broken stream; either way a
                // bad icon must not take the panel down, so the pair is simply left to its badge.
                try {
                    final var document = loader.load(resource);
                    if (document != null) {
                        perSex.put(sex, document);
                    } else {
                        log.warn("Could not parse the class icon {}.", path);
                    }
                } catch (RuntimeException e) {
                    log.warn("Could not read the class icon {}: {}", path, e.getMessage());
                }
            }
            icons.put(clazz, perSex);
        }
        return icons;
    }
}
