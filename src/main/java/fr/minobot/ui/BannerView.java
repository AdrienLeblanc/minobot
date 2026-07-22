package fr.minobot.ui;

import fr.minobot.win32.Rect;

/**
 * The auto-pass banner on the screen — another door to the outside world beside {@code win32.WindowApi},
 * {@code core.Input}, {@link OverlayView} and {@link ToastView}, and kept just as narrow.
 *
 * <p>Only its implementation knows about Swing. What <em>decides</em> — whether the banner is up at all,
 * when it hides, what a click on its cross means — lives in the controller behind {@link BannerActions},
 * and is tested with no screen at all.
 *
 * <p>The view draws what it is handed and asks for what the player wants through {@link BannerActions};
 * it never reaches for the configuration and never touches the game.
 */
public interface BannerView {

    /**
     * Draws the banner, or redraws it in place if it is already up.
     *
     * @param anchor the foreground character's client area in screen coordinates — the game, not the title
     *               bar above it. The view sizes itself to the banner alone and pins it to the top of that
     *               area, centred left to right with a little padding below the top edge.
     */
    void show(BannerContent content, Rect anchor);

    /**
     * Puts the banner back over the game, which has moved or been resized. Separate from {@link #show}
     * because it is called many times a second while the window is dragged: it moves what is already drawn
     * rather than building it again.
     */
    void moveTo(Rect anchor);

    void hide();

    boolean isVisible();
}
