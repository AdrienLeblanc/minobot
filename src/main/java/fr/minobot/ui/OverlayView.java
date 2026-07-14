package fr.minobot.ui;

import fr.minobot.win32.Rect;

/**
 * The panel on the screen — the third door to the outside world, next to {@code win32.WindowApi} and
 * {@code core.Input}, and kept as narrow as they are.
 *
 * <p>Only its implementation knows about Swing. Everything that <em>decides</em> — which character the
 * panel belongs to, what it lists, what a drag means — lives in the controller behind
 * {@link OverlayActions}, and is tested with no screen at all.
 *
 * <p>The view never reaches for the configuration, and never touches the game: it draws what it is
 * handed, and asks for what the player wants through {@link OverlayActions}.
 */
public interface OverlayView {

    /**
     * Draws the panel, filling the given area, or redraws it in place if it is already up.
     *
     * @param bounds the character's client area in screen coordinates: the game, and not the title bar
     *               Windows draws above it
     */
    void show(OverlayContent content, Rect bounds);

    /**
     * Puts the panel back over its character, which has moved or been resized.
     *
     * <p>Separate from {@link #show} because it is called many times a second while the player drags
     * their window: it moves what is already drawn, rather than building it again.
     */
    void moveTo(Rect bounds);

    void hide();

    boolean isVisible();
}
