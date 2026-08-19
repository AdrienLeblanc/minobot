package fr.minobot.core;

import fr.minobot.core.domain.Whisper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** What the panel's WHISPERS column is fed, how a card points back at one, and what it forgets. */
class WhisperLogTest {

    private static final Instant NOW = Instant.parse("2026-08-19T20:15:30Z");

    private final WhisperLog log = new WhisperLog(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("a whisper is kept whole, and stamped with when it arrived")
    void keepsTheWhisper() {
        final var whisper = log.add("Bravo", "Alpha", "Bonjour c'est toto");

        assertThat(whisper.receiver()).isEqualTo("Bravo");
        assertThat(whisper.sender()).isEqualTo("Alpha");
        assertThat(whisper.message()).isEqualTo("Bonjour c'est toto");
        assertThat(whisper.at()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("adding hands back the whisper as remembered, so a card can be raised under its id")
    void namesEachWhisper() {
        final var first = log.add("Bravo", "Alpha", "one");
        final var second = log.add("Bravo", "Alpha", "two");

        assertThat(first.id()).isNotBlank().isNotEqualTo(second.id());
        assertThat(log.find(first.id())).contains(first);
    }

    @Test
    @DisplayName("an id nobody knows resolves to nothing rather than throwing")
    void unknownIdIsEmpty() {
        assertThat(log.find("no-such-whisper")).isEmpty();
    }

    @Test
    @DisplayName("the newest is read first, as the panel draws them")
    void readsNewestFirst() {
        log.add("Bravo", "Alpha", "first");
        log.add("Bravo", "Alpha", "second");

        assertThat(log.recent()).extracting(Whisper::message).containsExactly("second", "first");
    }

    @Test
    @DisplayName("it forgets its oldest past its capacity rather than growing for the whole session")
    void dropsTheOldestPastCapacity() {
        IntStream.rangeClosed(1, WhisperLog.CAPACITY + 3)
                .forEach(i -> log.add("Bravo", "Alpha", "line " + i));

        assertThat(log.recent()).hasSize(WhisperLog.CAPACITY);
        assertThat(log.recent()).extracting(Whisper::message)
                .startsWith("line " + (WhisperLog.CAPACITY + 3))
                .endsWith("line 4");
    }

    @Test
    @DisplayName("clearing drops everything, and a cleared whisper is no longer found by its id")
    void clears() {
        final var whisper = log.add("Bravo", "Alpha", "read and gone");

        log.clear();

        assertThat(log.recent()).isEmpty();
        assertThat(log.find(whisper.id())).isEmpty();
    }
}
