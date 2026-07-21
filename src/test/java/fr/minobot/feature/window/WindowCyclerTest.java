package fr.minobot.feature.window;

import fr.minobot.app.TestConfigs;
import fr.minobot.feature.Features;
import fr.minobot.win32.FakeWindowApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cycling order, driven against an in-memory desktop.
 *
 * <p>The windows are laid out so that the configured order (Delta, Bravo, Alpha) is the reverse of the
 * alphabetical one: a test that passes by accident on the enumeration order cannot pass here.
 */
class WindowCyclerTest {

    private static final Map<String, Object> CYCLE_ORDER =
            Map.of("characters", TestConfigs.characters("Delta", "Bravo", "Alpha"));

    /** Cycling visits them as Delta (3), Bravo (2), Alpha (1). */
    private FakeWindowApi desktop() {
        return new FakeWindowApi()
                .withWindow(1, "Alpha - Dofus")
                .withWindow(2, "Bravo - Dofus")
                .withWindow(3, "Delta - Dofus");
    }

    @Nested
    class Forward {

        @Test
        void movesToTheNextWindowOfTheConfiguredOrder() {
            final var api = desktop().withForeground(3);

            new Features(api, CYCLE_ORDER).cycler().cycleNext();

            assertThat(api.foregroundWindow()).isEqualTo(2);
        }

        @Test
        @DisplayName("the last window wraps around to the first")
        void wrapsAround() {
            final var api = desktop().withForeground(1);

            new Features(api, CYCLE_ORDER).cycler().cycleNext();

            assertThat(api.foregroundWindow()).isEqualTo(3);
        }
    }

    @Nested
    class Backward {

        @Test
        void movesToThePreviousWindowOfTheConfiguredOrder() {
            final var api = desktop().withForeground(2);

            new Features(api, CYCLE_ORDER).cycler().cyclePrev();

            assertThat(api.foregroundWindow()).isEqualTo(3);
        }

        @Test
        @DisplayName("the first window wraps around to the last")
        void wrapsAround() {
            final var api = desktop().withForeground(3);

            new Features(api, CYCLE_ORDER).cycler().cyclePrev();

            assertThat(api.foregroundWindow()).isEqualTo(1);
        }
    }

    @Nested
    class OutsideTheCycle {

        @Test
        @DisplayName("from another application, next enters the cycle at the first window")
        void entersTheCycleFromTheFront() {
            final var api = desktop()
                    .withWindow(9, "Untitled - Notepad")
                    .withForeground(9);

            new Features(api, CYCLE_ORDER).cycler().cycleNext();

            assertThat(api.foregroundWindow()).isEqualTo(3);
        }

        @Test
        @DisplayName("from another application, previous enters the cycle at the last window")
        void entersTheCycleFromTheBack() {
            final var api = desktop()
                    .withWindow(9, "Untitled - Notepad")
                    .withForeground(9);

            new Features(api, CYCLE_ORDER).cycler().cyclePrev();

            assertThat(api.foregroundWindow()).isEqualTo(1);
        }
    }

    @Nested
    class Scope {

        @Test
        @DisplayName("only the windows of the current monitor take part")
        void staysOnTheCurrentMonitor() {
            final var api = desktop()
                    .onMonitor(1, 2L) // Alpha sits on the second screen
                    .withForeground(2);

            new Features(api, CYCLE_ORDER).cycler().cycleNext();

            // Delta and Bravo alone are left, so next from Bravo wraps back to Delta, not to Alpha.
            assertThat(api.foregroundWindow()).isEqualTo(3);
        }

        @Test
        void skipsTheMinimizedWindows() {
            final var api = desktop().minimize(2).withForeground(3);

            new Features(api, CYCLE_ORDER).cycler().cycleNext();

            assertThat(api.foregroundWindow()).isEqualTo(1);
        }

        @Test
        @DisplayName("with no game window at all, the focus is left alone")
        void doesNothingWithoutGameWindows() {
            final var api = new FakeWindowApi()
                    .withWindow(9, "Untitled - Notepad")
                    .withForeground(9);

            new Features(api, CYCLE_ORDER).cycler().cycleNext();

            assertThat(api.foregroundWindow()).isEqualTo(9);
            assertThat(api.focusedWindows()).isEmpty();
        }
    }
}
