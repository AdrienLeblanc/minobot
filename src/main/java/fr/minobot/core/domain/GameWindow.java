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
     * Whether a character is loaded in this window yet.
     *
     * <p>False on the login and character-selection screens: there the game titles the window with its
     * own name alone ({@code "Dofus Retro v1.48.18"}), no {@code "<character> - "} in front. The window
     * is the game's — the process says so — but there is no character for it to speak of yet, and
     * {@link #name()} would answer with the bare game title.
     */
    public boolean hasCharacterName() {
        return !suffixIn(title).isEmpty();
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

    /**
     * What the game appends after the character name — {@code "Dofus Retro v1.48.18"} in a window's
     * title. Empty when the title carries no separator at all.
     *
     * <p>Cut at the <em>same</em> first separator as {@link #nameIn}, so the name and the suffix are the
     * two halves of one title with nothing lost between them.
     */
    public static String suffixIn(String title) {
        for (final var separator : SEPARATORS) {
            final var index = title.indexOf(separator);
            if (index >= 0) {
                return title.substring(index + separator.length()).strip();
            }
        }
        return "";
    }
}
