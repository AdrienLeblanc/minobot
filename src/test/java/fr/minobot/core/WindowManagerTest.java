package fr.minobot.core;

import fr.minobot.app.Settings;
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

    private static Settings settings(Map<String, Object> overrides) {
        return TestConfigs.settings(overrides);
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
                    .withWindow(1, "Bravo - Dofus Retro")
                    .withWindow(2, "Untitled - Notepad")
                    .withWindow(3, "Charlie - Dofus Retro");

            final var manager = new WindowManager(api, settings(Map.of()));
            manager.refresh();

            assertThat(titlesOf(manager.windows())).isEqualTo(List.of("Bravo - Dofus Retro", "Charlie - Dofus Retro"));
        }

        @Test
        @DisplayName("ignores a browser tab that merely mentions the game in its title")
        void ignoresAPageThatMentionsTheGame() {
            // A browser window open on a page about the game: "Dofus Retro" is in the page's text, buried
            // mid-sentence rather than opening the title the way the game writes it. Windows lists it
            // beside the real windows; only the shape tells them apart. Left in, the overlay opens over the
            // browser and the multi-click reaches into it.
            final var api = new FakeWindowApi()
                    .withWindow(1, "Alpha - Dofus Retro v1.48.18")
                    .withWindow(2, "How to play Dofus Retro on several accounts — Mozilla Firefox");

            final var manager = new WindowManager(api, settings(Map.of()));
            manager.refresh();

            assertThat(titlesOf(manager.windows()))
                    .as("the character's window is kept; the browser tab about the game is not")
                    .containsExactly("Alpha - Dofus Retro v1.48.18");
        }

        @Test
        @DisplayName("a not-yet-logged-in window is kept: its process is the game, though its title has no name")
        void keepsAWindowWithNoCharacterYetByItsProcess() {
            // The login screen, before a character is picked: the title is the bare game name, with no
            // "<name> - " in front of it — so its title has no window shape at all. Only the process says
            // it is the game.
            final var api = new FakeWindowApi()
                    .withWindow(1, "Dofus Retro v1.48.18")
                    .runningAs(1, "C:\\Users\\me\\AppData\\Local\\Ankama\\Retro\\Dofus Retro.exe");

            final var manager = new WindowManager(api, settings(Map.of()));
            manager.refresh();

            assertThat(titlesOf(manager.windows()))
                    .as("the overlay must be able to open on a window whose character is not logged in yet")
                    .containsExactly("Dofus Retro v1.48.18");
        }

        @Test
        @DisplayName("the game's process is kept by its exe alone; a page that only mentions the game is not")
        void keepsTheGameByItsProcess() {
            final var api = new FakeWindowApi()
                    // A page that mentions the game without running it: neither game-shaped title nor exe.
                    .withWindow(1, "Dofus Retro — how to multibox — Mozilla Firefox")
                    .runningAs(1, "C:\\Program Files\\Mozilla Firefox\\firefox.exe")
                    // A game window kept on the strength of its process, whatever the leaf's casing.
                    .withWindow(2, "Bravo - Dofus Retro v1.48.18")
                    .runningAs(2, "C:\\Games\\DOFUS RETRO.EXE");

            final var manager = new WindowManager(api, settings(Map.of()));
            manager.refresh();

            assertThat(titlesOf(manager.windows()))
                    .containsExactly("Bravo - Dofus Retro v1.48.18");
        }

        @Test
        void findsACharacterByItsExactName() {
            final var api = new FakeWindowApi()
                    .withWindow(1, "Bravo - Dofus Retro")
                    .withWindow(2, "Charlie - Dofus Retro");

            final var manager = new WindowManager(api, settings(Map.of()));

            assertThat(manager.findWindow("bravo").map(GameWindow::hwnd)).contains(1L);
        }

        @Test
        @DisplayName("falls back to a substring of the whole title when no name matches")
        void findsACharacterByPartialTitle() {
            final var api = new FakeWindowApi().withWindow(7, "Bravo - Dofus Retro v1.48");

            final var manager = new WindowManager(api, settings(Map.of()));

            assertThat(manager.findWindow("v1.48").map(GameWindow::hwnd)).contains(7L);
            assertThat(manager.findWindow("Nobody")).isEmpty();
        }
    }

    @Nested
    class CharacterNames {

        private final WindowManager manager = new WindowManager(new FakeWindowApi(), settings(Map.of()));

        @Test
        void cutsTheTitleAtTheFirstConfiguredSeparator() {
            assertThat(manager.extractCharacterName("Bravo - Dofus Retro v1.48.18")).isEqualTo("Bravo");
            assertThat(manager.extractCharacterName("Bravo: Dofus")).isEqualTo("Bravo");
            assertThat(manager.extractCharacterName("Bravo | Dofus")).isEqualTo("Bravo");
        }

        @Test
        @DisplayName("a title with no separator is the name")
        void keepsTheTitleWhenItHasNoSeparator() {
            assertThat(manager.extractCharacterName("  Bravo  ")).isEqualTo("Bravo");
        }
    }

    @Nested
    class Ordering {

        private final FakeWindowApi api = new FakeWindowApi()
                .withWindow(1, "Delta - Dofus")
                .withWindow(2, "Charlie - Dofus")
                .withWindow(3, "Bravo - Dofus")
                .withWindow(4, "Alpha - Dofus");

        @Test
        @DisplayName("follows window_cycle_order, whatever order Windows enumerated the windows in")
        void sortsByConfiguredOrder() {
            final var manager = new WindowManager(api,
                    settings(Map.of("window_cycle_order", List.of("Bravo", "Charlie"))));

            assertThat(titlesOf(manager.orderedWindows())).isEqualTo(List.of("Bravo - Dofus", "Charlie - Dofus", "Alpha - Dofus", "Delta - Dofus"));
        }

        @Test
        @DisplayName("windows absent from the config go last, alphabetically rather than arbitrarily")
        void breaksTiesAlphabetically() {
            final var manager = new WindowManager(api, settings(Map.of("window_cycle_order", List.of())));

            assertThat(titlesOf(manager.orderedWindows())).isEqualTo(List.of("Alpha - Dofus", "Bravo - Dofus", "Charlie - Dofus", "Delta - Dofus"));
        }

        @Test
        @DisplayName("reversing flips the ranks but keeps the alphabetical tie-break")
        void reversesTheConfiguredOrder() {
            final var manager = new WindowManager(api,
                    settings(Map.of("window_cycle_order", List.of("Bravo", "Charlie"))));

            assertThat(titlesOf(manager.orderedWindows(true))).isEqualTo(List.of("Alpha - Dofus", "Delta - Dofus", "Charlie - Dofus", "Bravo - Dofus"));
        }

        @Test
        @DisplayName("the config matches on a fragment of the title, case-insensitively")
        void matchesTheConfiguredNameCaseInsensitively() {
            final var manager = new WindowManager(api,
                    settings(Map.of("window_cycle_order", List.of("dElTa"))));

            assertThat(titlesOf(manager.orderedWindows()).getFirst()).isEqualTo("Delta - Dofus");
        }

        @Test
        void dropsTheMinimizedWindows() {
            api.minimize(2);
            final var manager = new WindowManager(api, settings(Map.of()));

            assertThat(titlesOf(manager.activeOrderedWindows())).isEqualTo(List.of("Alpha - Dofus", "Bravo - Dofus", "Delta - Dofus"));
        }

        @Test
        @DisplayName("a new order is in effect on the next cycle — the overlay's drag & drop needs no restart")
        void followsALiveChangeOfTheOrder() {
            final var live = settings(Map.of("window_cycle_order", List.of("Bravo", "Charlie")));
            final var manager = new WindowManager(api, live);

            assertThat(titlesOf(manager.orderedWindows()).getFirst()).isEqualTo("Bravo - Dofus");

            live.update(config -> config.withWindowCycleOrder(List.of("Delta", "Alpha")));

            assertThat(titlesOf(manager.orderedWindows()))
                    .as("the very same manager must sort by the order the player just dragged")
                    .isEqualTo(List.of("Delta - Dofus", "Alpha - Dofus", "Bravo - Dofus", "Charlie - Dofus"));
        }
    }

    @Nested
    class Monitors {

        @Test
        @DisplayName("keeps the windows sharing a monitor with the focused one")
        void filtersByTheMonitorOfTheForegroundWindow() {
            FakeWindowApi api = new FakeWindowApi()
                    .withWindow(1, "Bravo - Dofus")
                    .withWindow(2, "Charlie - Dofus")
                    .withWindow(3, "Delta - Dofus")
                    .onMonitor(3, 2L)
                    .withForeground(1);

            WindowManager manager = new WindowManager(api, settings(Map.of()));

            assertThat(titlesOf(manager.windowsOnCurrentMonitor())).isEqualTo(List.of("Bravo - Dofus", "Charlie - Dofus"));
        }

        @Test
        @DisplayName("with nothing focused, every active window qualifies")
        void fallsBackToEveryActiveWindow() {
            FakeWindowApi api = new FakeWindowApi()
                    .withWindow(1, "Bravo - Dofus")
                    .withWindow(2, "Charlie - Dofus");

            WindowManager manager = new WindowManager(api, settings(Map.of()));

            assertThat(manager.windowsOnCurrentMonitor()).hasSize(2);
        }

        @Test
        @DisplayName("a game window on another monitor than the focused one yields nothing")
        void yieldsNothingWhenTheFocusedMonitorHasNoGameWindow() {
            FakeWindowApi api = new FakeWindowApi()
                    .withWindow(1, "Bravo - Dofus")
                    .withWindow(2, "Untitled - Notepad")
                    .onMonitor(1, 1L)
                    .onMonitor(2, 2L)
                    .withForeground(2);

            WindowManager manager = new WindowManager(api, settings(Map.of()));

            assertThat(manager.windowsOnCurrentMonitor()).isEmpty();
        }
    }
}
