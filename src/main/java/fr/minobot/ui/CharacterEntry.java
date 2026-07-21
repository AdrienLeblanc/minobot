package fr.minobot.ui;

import fr.minobot.core.domain.Character;

/**
 * A character as the panel draws them: the {@link Character} the player configured, and whether their
 * game window is open right now.
 *
 * <p>The connection is <strong>transient window state</strong>, and deliberately not a field on
 * {@link Character}: a character owns its pins — a class, a sex — not the window it happens to be played
 * in. So the two are paired here, in the view's own vocabulary, rather than the domain being made to
 * carry a flag that means nothing on disk. Kept as one indivisible unit for the same reason
 * {@code WindowManager}'s snapshot pairs its window list with the instant it was taken.
 *
 * <p>{@code connected} is what the row's status chip reads: green when the window is up, light-grey when
 * it is not — a character the player pinned but is not playing right now.
 */
public record CharacterEntry(Character character, boolean connected) {
}
