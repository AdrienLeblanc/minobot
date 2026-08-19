package fr.minobot.ui;

/**
 * What the auto-pass banner shows: what is running, and what it is doing about it. The immutable snapshot
 * the view draws, exactly as {@link OverlayContent} is for the panel and {@link ToastContent} for the
 * whisper stack.
 *
 * <p>Two strings and not one. The {@code heading} names the feature in the fewest words that identify it
 * — it is set in caps, and a player who glances at the top of their game must be able to read it without
 * stopping. The {@code message} is the consequence, in a full phrase, for the player who does stop.
 * Together they answer "what is that?" and "so what?" without either sentence having to do both jobs.
 *
 * <p>Both are handed in rather than known to the view, so the view stays a dumb surface — the controller
 * decides the wording, the view only draws it.
 *
 * <p>The scale is the player's — the same {@code overlay_scale} the panel and the whisper stack are drawn
 * at, because the banner covers a band of the same game on the same monitor. It is a multiplier of the
 * natural size of every piece of the banner, and the view is the only one that knows those natural sizes.
 */
public record BannerContent(double scale, String heading, String message) {
}
