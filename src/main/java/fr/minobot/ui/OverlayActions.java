package fr.minobot.ui;

import fr.minobot.app.Feature;

import java.util.List;
import java.util.Optional;

/**
 * What the player can ask for through the panel. Implemented by the controller, so the view itself
 * never touches the configuration, the keyboard or the game.
 *
 * <p>Every one of these runs on whichever thread the view calls it from — Swing's, in practice. None
 * of them blocks for long except {@link #captureHotkey()}, which by nature waits for a human.
 */
public interface OverlayActions {

    /** The characters as the player has just dragged them: the order the cycler will follow. */
    void reorder(List<String> characters);

    /**
     * Re-enumerates the desktop and redraws the list — a character opened or closed since the panel
     * went up shows or goes. The list refreshes itself every thirty seconds anyway; this is the player
     * saying they will not wait for the next sweep.
     */
    void reload();

    /**
     * Binds the feature to a combination. A blank one turns the feature off — that is the toggle, and
     * the reason there is no {@code *_enabled} flag anywhere.
     */
    void rebind(Feature feature, String combination);

    /**
     * Draws the panel bigger or smaller — what the player has just dragged the slider to.
     *
     * <p>A value outside what the slider offers is brought back inside it: the configuration is the one
     * holding the bounds, not the view.
     */
    void rescale(double scale);

    /**
     * Switches the automatic turn-passer on or off — a feature with a state rather than a key.
     * Session-only, like every edit made through the panel.
     */
    void toggleAutoPassTurn(boolean on);

    /**
     * Switches the automatic trade-accepter on or off — the panel's other stateful toggle.
     * Session-only, like every edit made through the panel.
     */
    void toggleAutoAcceptTrade(boolean on);

    /**
     * Waits for the player to press the key they want, and reads it back — {@code "shift+F7"}.
     *
     * <p>Empty if they pressed nothing in time. No hotkey fires while this is waiting: a key being
     * named is not a key being used.
     */
    Optional<String> captureHotkey();
}
