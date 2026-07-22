package fr.minobot.ui;

/**
 * What the auto-pass banner shows: a single line telling the player the turn-passer is running. The
 * immutable snapshot the view draws, exactly as {@link OverlayContent} is for the panel and
 * {@link ToastContent} for the whisper stack.
 *
 * <p>The message is handed in rather than known to the view, so the view stays a dumb surface — the
 * controller decides the wording, the view only draws it.
 *
 * <p>The scale is the player's — the same {@code overlay_scale} the panel and the whisper stack are drawn
 * at, because the banner covers a band of the same game on the same monitor. It is a multiplier of the
 * natural size of every piece of the banner, and the view is the only one that knows those natural sizes.
 */
public record BannerContent(double scale, String message) {
}
