package fr.minobot.ui;

import fr.minobot.app.Feature;

import java.util.List;
import java.util.Map;

/**
 * What the panel shows: the characters Minobot has found, in the order the player cycles through them,
 * and the key each feature currently answers to.
 *
 * <p>Characters, not windows — the panel is the story the player tells, and they cycle through
 * <em>Bravo</em> and <em>Charlie</em>, not through {@code 0x000407A2}.
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
 */
public record OverlayContent(List<String> characters, Map<Feature, String> hotkeys, double scale,
                             boolean autoPassTurn, boolean autoAcceptTrade) {

    public OverlayContent {
        characters = List.copyOf(characters);
        hotkeys = Map.copyOf(hotkeys);
    }
}
