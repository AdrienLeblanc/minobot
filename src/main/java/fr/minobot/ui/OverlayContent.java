package fr.minobot.ui;

import fr.minobot.app.Feature;
import fr.minobot.core.domain.Character;

import java.util.List;
import java.util.Map;

/**
 * What the panel shows: the characters Minobot has found, in the order the player cycles through them,
 * and the key each feature currently answers to.
 *
 * <p>Characters, not windows — the panel is the story the player tells, and they cycle through
 * <em>Bravo</em> and <em>Charlie</em>, not through {@code 0x000407A2}. Each {@link Character} carries its
 * own name and whatever the player has pinned to it — a class, a sex — so the panel reads one list rather
 * than a list of names beside a map of classes beside a map of sexes.
 *
 * <p>A blank hotkey is a feature the player has turned off; there is no separate flag for it. The
 * order of {@code hotkeys} means nothing: walk {@link Feature#values()} to lay them out.
 *
 * <p>The scale is how big the player wants the panel drawn — the panel covers the game, and the game
 * is played on screens of every size. It is a multiplier of the natural size of every piece of the
 * panel, and the view is the only one that knows what those natural sizes are.
 *
 * <p>{@code autoPassTurn} and {@code autoAcceptTrade} are the features the panel shows a state for
 * rather than a key: they have no hotkey, so the panel draws each as an explicit on/off switch.
 *
 * <p>The list holds only the characters on screen: one whose class the player pinned but who is not
 * launched right now stays in the configuration, but the panel speaks of what is in front of the player.
 * A character with no class has none pinned yet, and the panel offers to give it one; one with no sex is
 * drawn as male.
 */
public record OverlayContent(List<Character> characters, Map<Feature, String> hotkeys, double scale,
                             boolean autoPassTurn, boolean autoAcceptTrade) {

    /**
     * What the panel lists a game window with no character loaded yet under — a login or selection
     * screen. It has a window but no name to be cycled by or given a class to, so it rides in the list
     * as a {@link Character} under this name and is left out of both the saved order and the classes.
     * Here rather than in the controller so the view can recognise the row without hardcoding the string.
     */
    public static final String LOGGING_IN = "(logging in…)";

    public OverlayContent {
        characters = List.copyOf(characters);
        hotkeys = Map.copyOf(hotkeys);
    }
}
