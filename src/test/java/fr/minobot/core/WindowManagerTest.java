package fr.minobot.core;

import fr.minobot.app.Config;
import fr.minobot.app.TestConfigs;
import fr.minobot.core.domain.GameWindow;
import fr.minobot.win32.FakeWindowApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sorting, the monitor filtering and the name extraction, driven against an in-memory desktop.
 */
class WindowManagerTest {

    private static Config config(Map<String, Object> overrides) {
        return TestConfigs.with(overrides);
    }

    private static List<String> titlesOf(List<GameWindow> windows) {
        return windows.stream().map(GameWindow::title).toList();
    }

    @Nested
    class Detection {

        @Test
        @DisplayName("keeps only the visible windows whose title carries a game keyword")
        void filtersByKeyword() {
            final var api = new FakeWindowApi()
                    .withWindow(1, "Lenore - Dofus Retro")
                    .withWindow(2, "Untitled - Notepad")
                    .withWindow(3, "Mino - Dofus Retro");

            final var manager = new WindowManager(api, config(Map.of()));
            manager.refresh();

            assertThat(titlesOf(manager.windows())).isEqualTo(List.of("Lenore - Dofus Retro", "Mino - Dofus Retro"));
        }

        @Test
        void findsACharacterByItsExactName() {
            final var api = new FakeWindowApi()
                    .withWindow(1, "Lenore - Dofus Retro")
                    .withWindow(2, "Mino - Dofus Retro");

            final var manager = new WindowManager(api, config(Map.of()));

            assertThat(manager.findWindow("lenore").map(GameWindow::hwnd)).contains(1L);
        }

        @Test
        @DisplayName("falls back to a substring of the whole title when no name matches")
        void findsACharacterByPartialTitle() {
            final var api = new FakeWindowApi().withWindow(7, "Lenore - Dofus Retro v1.48");

            final var manager = new WindowManager(api, config(Map.of()));

            assertThat(manager.findWindow("v1.48").map(GameWindow::hwnd)).contains(7L);
            assertThat(manager.findWindow("Nobody")).isEmpty();
        }
    }

    @Nested
    class CharacterNames {

        private final WindowManager manager = new WindowManager(new FakeWindowApi(), config(Map.of()));

        @Test
        void cutsTheTitleAtTheFirstConfiguredSeparator() {
            assertThat(manager.extractCharacterName("Lenore - Dofus Retro v1.48.18")).isEqualTo("Lenore");
            assertThat(manager.extractCharacterName("Lenore: Dofus")).isEqualTo("Lenore");
            assertThat(manager.extractCharacterName("Lenore | Dofus")).isEqualTo("Lenore");
        }

        @Test
        @DisplayName("a title with no separator is the name")
        void keepsTheTitleWhenItHasNoSeparator() {
            assertThat(manager.extractCharacterName("  Lenore  ")).isEqualTo("Lenore");
        }
    }

    @Nested
    class Ordering {

        private final FakeWindowApi api = new FakeWindowApi()
                .withWindow(1, "Zora - Dofus")
                .withWindow(2, "Mino - Dofus")
                .withWindow(3, "Lenore - Dofus")
                .withWindow(4, "Aria - Dofus");

        @Test
        @DisplayName("follows window_cycle_order, whatever order Windows enumerated the windows in")
        void sortsByConfiguredOrder() {
            final var manager = new WindowManager(api,
                    config(Map.of("window_cycle_order", List.of("Lenore", "Mino"))));

            assertThat(titlesOf(manager.orderedWindows())).isEqualTo(List.of("Lenore - Dofus", "Mino - Dofus", "Aria - Dofus", "Zora - Dofus"));
        }

        @Test
        @DisplayName("windows absent from the config go last, alphabetically rather than arbitrarily")
        void breaksTiesAlphabetically() {
            final var manager = new WindowManager(api, config(Map.of("window_cycle_order", List.of())));

            assertThat(titlesOf(manager.orderedWindows())).isEqualTo(List.of("Aria - Dofus", "Lenore - Dofus", "Mino - Dofus", "Zora - Dofus"));
        }

        @Test
        @DisplayName("reversing flips the ranks but keeps the alphabetical tie-break")
        void reversesTheConfiguredOrder() {
            final var manager = new WindowManager(api,
                    config(Map.of("window_cycle_order", List.of("Lenore", "Mino"))));

            assertThat(titlesOf(manager.orderedWindows(true))).isEqualTo(List.of("Aria - Dofus", "Zora - Dofus", "Mino - Dofus", "Lenore - Dofus"));
        }

        @Test
        @DisplayName("the config matches on a fragment of the title, case-insensitively")
        void matchesTheConfiguredNameCaseInsensitively() {
            final var manager = new WindowManager(api,
                    config(Map.of("window_cycle_order", List.of("zORa"))));

            assertThat(titlesOf(manager.orderedWindows()).getFirst()).isEqualTo("Zora - Dofus");
        }

        @Test
        void dropsTheMinimizedWindows() {
            api.minimize(2);
            final var manager = new WindowManager(api, config(Map.of()));

            assertThat(titlesOf(manager.activeOrderedWindows())).isEqualTo(List.of("Aria - Dofus", "Lenore - Dofus", "Zora - Dofus"));
        }
    }

    @Nested
    class Monitors {

        @Test
        @DisplayName("keeps the windows sharing a monitor with the focused one")
        void filtersByTheMonitorOfTheForegroundWindow() {
            FakeWindowApi api = new FakeWindowApi()
                    .withWindow(1, "Lenore - Dofus")
                    .withWindow(2, "Mino - Dofus")
                    .withWindow(3, "Zora - Dofus")
                    .onMonitor(3, 2L)
                    .withForeground(1);

            WindowManager manager = new WindowManager(api, config(Map.of()));

            assertThat(titlesOf(manager.windowsOnCurrentMonitor())).isEqualTo(List.of("Lenore - Dofus", "Mino - Dofus"));
        }

        @Test
        @DisplayName("with nothing focused, every active window qualifies")
        void fallsBackToEveryActiveWindow() {
            FakeWindowApi api = new FakeWindowApi()
                    .withWindow(1, "Lenore - Dofus")
                    .withWindow(2, "Mino - Dofus");

            WindowManager manager = new WindowManager(api, config(Map.of()));

            assertThat(manager.windowsOnCurrentMonitor()).hasSize(2);
        }

        @Test
        @DisplayName("a game window on another monitor than the focused one yields nothing")
        void yieldsNothingWhenTheFocusedMonitorHasNoGameWindow() {
            FakeWindowApi api = new FakeWindowApi()
                    .withWindow(1, "Lenore - Dofus")
                    .withWindow(2, "Untitled - Notepad")
                    .onMonitor(1, 1L)
                    .onMonitor(2, 2L)
                    .withForeground(2);

            WindowManager manager = new WindowManager(api, config(Map.of()));

            assertThat(manager.windowsOnCurrentMonitor()).isEmpty();
        }
    }
}
