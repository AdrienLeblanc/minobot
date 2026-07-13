package fr.minobot.feature;

import fr.minobot.win32.FakeWindowApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cycling order, driven against an in-memory desktop.
 *
 * <p>The windows are laid out so that the configured order (Zora, Lenore, Aria) is the reverse of the
 * alphabetical one: a test that passes by accident on the enumeration order cannot pass here.
 */
class WindowCyclerTest {

    private static final Map<String, Object> CYCLE_ORDER =
            Map.of("window_cycle_order", List.of("Zora", "Lenore", "Aria"));

    /** Cycling visits them as Zora (3), Lenore (2), Aria (1). */
    private FakeWindowApi desktop() {
        return new FakeWindowApi()
                .withWindow(1, "Aria - Dofus")
                .withWindow(2, "Lenore - Dofus")
                .withWindow(3, "Zora - Dofus");
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
                    .onMonitor(1, 2L) // Aria sits on the second screen
                    .withForeground(2);

            new Features(api, CYCLE_ORDER).cycler().cycleNext();

            // Zora and Lenore alone are left, so next from Lenore wraps back to Zora, not to Aria.
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
