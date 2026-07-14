package fr.minobot.core.domain;

import java.util.List;

/**
 * A character, as seen from the outside: the window they are played in.
 *
 * <p>The handle is what Windows knows the window by; the title is what the game writes in it, and it
 * carries the character's name.
 */
public record GameWindow(long hwnd, String title) {

    /** What the game puts between the character's name and the rest: {@code "Bravo - Dofus Retro v1.48.18"}. */
    private static final List<String> SEPARATORS = List.of(" - ", ": ", " | ");

    /** The character played in this window. */
    public String name() {
        return nameIn(title);
    }

    /**
     * The character name a title carries, cut at the first separator.
     *
     * <p>Static because a toast's title is written the same way as a window's, and a toast is not a
     * window: {@code "Bravo - Dofus Retro v1.48.18"} yields {@code "Bravo"} either way.
     */
    public static String nameIn(String title) {
        for (final var separator : SEPARATORS) {
            final var index = title.indexOf(separator);
            if (index >= 0) {
                return title.substring(0, index).strip();
            }
        }
        return title.strip();
    }
}
