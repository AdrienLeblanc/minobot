package fr.minobot.core.domain;

/**
 * A Windows toast, reduced to what the game puts in it.
 *
 * <p>{@code title} carries the character ("Lenore - Dofus Retro"), {@code message} the event
 * ("Vous avez recu une invitation de groupe").
 */
public record Notification(String title, String message) {
}
