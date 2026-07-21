package fr.minobot.ui;

import fr.minobot.win32.Rect;

/**
 * The whisper stack on the screen — a fourth door to the outside world beside {@code win32.WindowApi},
 * {@code core.Input} and {@link OverlayView}, and kept just as narrow.
 *
 * <p>Only its implementation knows about Swing. What <em>decides</em> — which messages are whispers,
 * which character each belongs to, when the stack shows and when it hides — lives in the controller
 * behind {@link ToastActions}, and is tested with no screen at all.
 *
 * <p>The view draws what it is handed and asks for what the player wants through {@link ToastActions};
 * it never reaches for the configuration and never touches the game.
 */
public interface ToastView {

    /**
     * Draws the stack, or redraws it in place if it is already up.
     *
     * @param anchor the foreground character's client area in screen coordinates — the game, not the
     *               title bar above it. The view sizes itself to the stack alone and pins it to the
     *               left edge of that area, centred top to bottom, so it blocks only a narrow band.
     */
    void show(ToastContent content, Rect anchor);

    /**
     * Puts the stack back over the game, which has moved or been resized. Separate from {@link #show}
     * because it is called many times a second while the window is dragged: it moves what is already
     * drawn rather than building it again.
     */
    void moveTo(Rect anchor);

    void hide();

    boolean isVisible();
}
