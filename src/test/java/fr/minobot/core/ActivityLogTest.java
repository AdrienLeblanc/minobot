package fr.minobot.core;

import fr.minobot.core.domain.Activity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** What the panel's ACTIVITY column is fed, and what it forgets. */
class ActivityLogTest {

    private static final Instant NOW = Instant.parse("2026-08-19T20:15:30Z");

    private final ActivityLog log = new ActivityLog(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("an act is kept with its time, what it was, and the circumstance behind it")
    void keepsWhatWasDone() {
        log.record("Trade accepted on Bravo", "from Alpha");

        assertThat(log.recent()).singleElement()
                .isEqualTo(new Activity(NOW, "Trade accepted on Bravo", "from Alpha"));
    }

    @Test
    @DisplayName("an act that speaks for itself carries no detail rather than a blank-looking one")
    void allowsAnActWithNoDetail() {
        log.record("Taskbar reordered");

        assertThat(log.recent()).singleElement()
                .extracting(Activity::detail).isEqualTo("");
    }

    @Test
    @DisplayName("the newest is read first: the panel is opened to see what just happened")
    void readsNewestFirst() {
        log.record("first");
        log.record("second");
        log.record("third");

        assertThat(log.recent()).extracting(Activity::what)
                .containsExactly("third", "second", "first");
    }

    @Test
    @DisplayName("it forgets its oldest past its capacity rather than growing for the whole session")
    void dropsTheOldestPastCapacity() {
        IntStream.rangeClosed(1, ActivityLog.CAPACITY + 5).forEach(i -> log.record("act " + i));

        assertThat(log.recent()).hasSize(ActivityLog.CAPACITY);
        assertThat(log.recent()).extracting(Activity::what)
                .as("the five oldest were dropped, the newest is still at the head")
                .startsWith("act " + (ActivityLog.CAPACITY + 5))
                .endsWith("act 6");
    }

    @Test
    @DisplayName("nothing has happened yet is an empty list, never a null")
    void startsEmpty() {
        assertThat(log.recent()).isEmpty();
    }

    @Test
    @DisplayName("clearing drops everything")
    void clears() {
        log.record("something");

        log.clear();

        assertThat(log.recent()).isEmpty();
    }
}
