package fr.minobot.core.domain;

import java.time.Instant;

/**
 * One thing Minobot did, as the player would tell it: when, what, and about whom.
 *
 * <p>{@code what} is the act — <em>Passed Bravo's turn</em> — and {@code detail} is the circumstance that
 * makes it make sense — <em>from Alpha</em>, <em>4 characters</em>. Split in two because the panel draws
 * them in two columns: the acts line up down the middle of the card and are read as a list, while the
 * details sit at the right and are read only when one of the acts is a surprise.
 *
 * <p>Both are already the player's words when they get here. The panel does no phrasing of its own —
 * a feature says what it did, in terms of characters, and the panel shows it.
 */
public record Activity(Instant at, String what, String detail) {

    /** An act that needs no circumstance: the detail column is simply empty for it. */
    public Activity(Instant at, String what) {
        this(at, what, "");
    }
}
