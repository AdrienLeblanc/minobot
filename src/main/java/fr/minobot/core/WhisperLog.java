package fr.minobot.core;

import fr.minobot.core.domain.Whisper;

import java.time.Clock;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The whispers the player's characters have received, kept after their cards have faded.
 *
 * <p>A whisper toast stands ten seconds, which is the right life for a card in the corner of a fight and
 * the wrong one for the message itself: a player who was mid-turn when it appeared has lost the line and
 * the name of whoever sent it, and the game gives it back only by switching to that character and
 * reading the chat. So the card is transient and the <strong>whisper is not</strong> — the two are
 * separate lifetimes, which is why this is not a field of the toaster.
 *
 * <p>That split is also what lets the panel offer the jump: a row here names a character, so clicking it
 * is the same act the card offered before it faded.
 *
 * <p>Bounded at {@link #CAPACITY}, oldest dropped, nothing on disk — the same forgetfulness as
 * {@link ActivityLog}, and for the same reason: this answers "who wrote to me just now?", not "what was
 * said this session?".
 *
 * <p>Written from a notification's virtual thread and read from the panel's, so the deque is the lock.
 */
public final class WhisperLog {

    /** Enough that a burst of whispers during one fight all survive it. */
    public static final int CAPACITY = 20;

    /** Newest last, so an add is a tail push and the eviction is a head poll. */
    private final ArrayDeque<Whisper> whispers = new ArrayDeque<>(CAPACITY);

    /** Names each whisper so a card or a row can point back at one. Only ever grows. */
    private final AtomicLong ids = new AtomicLong();

    private final Clock clock;

    public WhisperLog() {
        this(Clock.systemDefaultZone());
    }

    /** @param clock what a whisper is stamped with — a fixed one in the tests, the wall clock in life */
    public WhisperLog(Clock clock) {
        this.clock = clock;
    }

    /**
     * Remembers one whisper and names it.
     *
     * @return the whisper as it was remembered, so the caller can raise a card under the very id the
     *         panel will later show the same message under
     */
    public Whisper add(String receiver, String sender, String message) {
        final var whisper = new Whisper(Long.toString(ids.incrementAndGet()),
                clock.instant(), receiver, sender, message);
        synchronized (whispers) {
            if (whispers.size() == CAPACITY) {
                whispers.pollFirst();
            }
            whispers.addLast(whisper);
        }
        return whisper;
    }

    /** What has been received, <strong>newest first</strong> — the order the panel reads it in. */
    public List<Whisper> recent() {
        synchronized (whispers) {
            return whispers.reversed().stream().toList();
        }
    }

    /** One whisper by its id, or empty when it has already been dropped or cleared. */
    public Optional<Whisper> find(String id) {
        synchronized (whispers) {
            return whispers.stream().filter(whisper -> whisper.id().equals(id)).findFirst();
        }
    }

    /** Drops everything — the panel's {@code Clear}, and how the tests get a clean slate. */
    public void clear() {
        synchronized (whispers) {
            whispers.clear();
        }
    }
}
