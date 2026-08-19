package fr.minobot.core.domain;

import java.time.Instant;

/**
 * A private message one of the player's characters received: when, who was written to, by whom, and
 * what they said.
 *
 * <p>The {@code id} is how a surface points back at one — the card whose cross was pressed, the line in
 * the panel that was clicked. It is minted where the whisper is remembered, so the toast on screen and
 * the row in the panel name the <em>same</em> whisper and a click on either finds it.
 */
public record Whisper(String id, Instant at, String receiver, String sender, String message) {
}
