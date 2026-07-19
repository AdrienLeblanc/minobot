package fr.minobot.feature;

import fr.minobot.app.Config;
import fr.minobot.app.Feature;
import fr.minobot.app.Settings;
import fr.minobot.app.TestConfigs;
import fr.minobot.core.KeyboardMonitor;
import fr.minobot.core.WindowManager;
import fr.minobot.ui.FakeOverlayView;
import fr.minobot.win32.FakeWindowApi;
import fr.minobot.win32.Rect;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Where the panel goes, what it shows, and what an edit through it changes. */
class OverlayControllerTest {

    private final FakeWindowApi api = new FakeWindowApi()
            .withWindow(1, "Alpha - Dofus Retro")
            .withWindow(2, "Bravo - Dofus Retro")
            .withWindow(3, "Charlie - Dofus Retro")
            .withWindow(9, "Untitled - Notepad");

    private final FakeOverlayView view = new FakeOverlayView();
    private final Settings settings = TestConfigs.settings(Map.of());

    private OverlayController controller() {
        return new OverlayController(api, new WindowManager(api, settings), settings,
                new KeyboardMonitor(api), _ -> view);
    }

    @Nested
    @DisplayName("where it goes")
    class Placing {

        @Test
        @DisplayName("it fills the game, and stops short of the title bar Windows draws above it")
        void coversTheClientAreaOnly() {
            // The fake models the frame as an 8px border and a 30px title bar.
            api.withForeground(2).withBounds(2, new Rect(-1920, 0, -1120, 600));

            controller().toggle();

            assertThat(view.isVisible()).isTrue();
            assertThat(view.bounds())
                    .as("the panel covers Bravo's game — not the minimize and close buttons above it — "
                            + "and it does so on a monitor left of the main one")
                    .contains(new Rect(-1912, 30, -1128, 592));
        }

        @Test
        @DisplayName("pressing the hotkey outside the game does nothing at all")
        void ignoresTheHotkeyOutsideTheGame() {
            api.withForeground(9); // the player is in their browser

            controller().toggle();

            assertThat(view.isVisible())
                    .as("there is no character for the panel to belong to: it must not appear anywhere")
                    .isFalse();
            assertThat(view.timesDrawn()).isZero();
        }

        @Test
        @DisplayName("with no window in the foreground at all, it stays away")
        void ignoresTheHotkeyWithNoForegroundWindow() {
            controller().toggle();

            assertThat(view.isVisible()).isFalse();
        }

        @Test
        @DisplayName("a second press takes it away")
        void hidesItOnTheSecondPress() {
            api.withForeground(1);
            final var controller = controller();

            controller.toggle();
            controller.toggle();

            assertThat(view.isVisible()).isFalse();
        }

        @Test
        @DisplayName("hiding it works even from a browser: the panel is up, and the hotkey is the switch")
        void hidesItFromAnywhere() {
            api.withForeground(1);
            final var controller = controller();
            controller.toggle();

            api.withForeground(9); // the player has clicked into their browser; the panel is still up
            controller.toggle();

            assertThat(view.isVisible())
                    .as("the rule about game windows guards showing it, not taking it away")
                    .isFalse();
        }
    }

    @Nested
    @DisplayName("following its character")
    class Following {

        /** The follower polls every 30 ms; this is room for several turns of it. */
        private void settle() throws InterruptedException {
            Thread.sleep(250);
        }

        @Test
        @DisplayName("the player drags their window, and the panel goes with it")
        void followsTheWindowWhenItMoves() throws InterruptedException {
            api.withForeground(1).withBounds(1, new Rect(0, 0, 800, 600));
            controller().toggle();

            api.moveTo(1, new Rect(500, 200, 1300, 800));
            settle();

            assertThat(view.bounds()).contains(new Rect(508, 230, 1292, 792));
        }

        @Test
        @DisplayName("and when they resize it")
        void followsTheWindowWhenItIsResized() throws InterruptedException {
            api.withForeground(1).withBounds(1, new Rect(0, 0, 800, 600));
            controller().toggle();

            api.moveTo(1, new Rect(0, 0, 1200, 900));
            settle();

            assertThat(view.bounds()).contains(new Rect(8, 30, 1192, 892));
        }

        @Test
        @DisplayName("a character who is minimized takes their panel with them")
        void takesThePanelDownWhenTheCharacterIsMinimized() throws InterruptedException {
            api.withForeground(1);
            controller().toggle();
            assertThat(view.isVisible()).isTrue();

            api.minimize(1);
            settle();

            assertThat(view.isVisible())
                    .as("a panel floating over a game nobody is playing is a panel nobody can dismiss")
                    .isFalse();
        }

        @Test
        @DisplayName("the follower stops with the panel, rather than polling forever")
        void stopsFollowingOnceThePanelIsDown() throws InterruptedException {
            api.withForeground(1).withBounds(1, new Rect(0, 0, 800, 600));
            final var controller = controller();
            controller.toggle();
            controller.toggle();

            api.moveTo(1, new Rect(500, 500, 1300, 1100));
            settle();

            assertThat(view.bounds())
                    .as("the window moved, but nothing is up to follow it")
                    .contains(new Rect(8, 30, 792, 592));
        }
    }

    @Nested
    @DisplayName("what it shows")
    class Listing {

        @Test
        @DisplayName("the characters, in the order they are cycled in")
        void listsTheCharactersInCycleOrder() {
            api.withForeground(1);
            settings.update(config -> config.withWindowCycleOrder(List.of("Charlie", "Alpha")));

            controller().toggle();

            assertThat(view.content()).isPresent();
            assertThat(view.content().orElseThrow().characters())
                    .containsExactly("Charlie", "Alpha", "Bravo");
        }

        @Test
        @DisplayName("a window with no character loaded yet is listed under a clean label, not its bare title")
        void labelsANotYetLoggedInWindow() {
            api.withForeground(1)
                    .withWindow(10, "Dofus Retro v1.48.18") // a login screen: the game's own title, no name
                    .runningAs(10, "C:\\Users\\me\\AppData\\Local\\Ankama\\Retro\\Dofus Retro.exe");

            controller().toggle();

            assertThat(view.content().orElseThrow().characters())
                    .as("the login window is shown, but under a label rather than the raw game title")
                    .contains("(connecting…)")
                    .doesNotContain("Dofus Retro v1.48.18");
        }

        @Test
        @DisplayName("every feature's key, and a blank one for those the player has turned off")
        void listsTheHotkeys() {
            api.withForeground(1);
            settings.update(config -> config.withHotkey(Feature.WINDOW_REORDER, ""));

            controller().toggle();

            final var hotkeys = view.content().orElseThrow().hotkeys();
            assertThat(hotkeys.get(Feature.MULTICLICK)).isEqualTo("x1");
            assertThat(hotkeys.get(Feature.GROUP_INVITE)).isEqualTo("F8");
            assertThat(hotkeys.get(Feature.WINDOW_REORDER)).as("turned off").isEmpty();
        }
    }

    @Nested
    @DisplayName("what an edit changes")
    class Editing {

        @Test
        @DisplayName("a drag & drop is in effect at once, and the panel redraws where it stands")
        void reordersTheCharacters() {
            api.withForeground(1).withBounds(1, new Rect(0, 0, 800, 600));
            final var controller = controller();
            controller.toggle();

            controller.reorder(List.of("Charlie", "Bravo", "Alpha"));

            assertThat(settings.get().windowCycleOrder()).containsExactly("Charlie", "Bravo", "Alpha");
            assertThat(view.content().orElseThrow().characters())
                    .as("the panel shows the new order without being asked again")
                    .containsExactly("Charlie", "Bravo", "Alpha");
            assertThat(view.bounds())
                    .as("and it has not moved off its character")
                    .contains(new Rect(8, 30, 792, 592));
        }

        @Test
        @DisplayName("reload re-reads the desktop: a character opened since the panel went up is picked up")
        void reloadsTheCharacterList() {
            api.withForeground(1);
            final var controller = controller();
            controller.toggle();
            assertThat(view.content().orElseThrow().characters())
                    .containsExactly("Alpha", "Bravo", "Charlie");

            api.withWindow(4, "Delta - Dofus Retro"); // a fourth account, opened after the panel went up
            controller.reload();

            assertThat(view.content().orElseThrow().characters())
                    .as("the panel shows the new character without waiting for the 30s sweep")
                    .containsExactly("Alpha", "Bravo", "Charlie", "Delta");
        }

        @Test
        @DisplayName("the login label never lands in the saved order: it has no character to cycle by")
        void keepsTheLoginLabelOutOfTheOrder() {
            api.withForeground(1);
            final var controller = controller();
            controller.toggle();

            controller.reorder(List.of("Charlie", "(connecting…)", "Alpha", "Bravo"));

            assertThat(settings.get().windowCycleOrder())
                    .as("the placeholder is dropped; the real characters keep the order the player set")
                    .containsExactly("Charlie", "Alpha", "Bravo");
        }

        @Test
        @DisplayName("a rebind lands in the live configuration")
        void rebindsAFeature() {
            api.withForeground(1);
            final var controller = controller();
            controller.toggle();

            controller.rebind(Feature.GROUP_INVITE, "shift+F7");

            assertThat(Feature.GROUP_INVITE.hotkeyIn(settings.get())).isEqualTo("shift+F7");
            assertThat(view.content().orElseThrow().hotkeys().get(Feature.GROUP_INVITE))
                    .isEqualTo("shift+F7");
        }

        @Test
        @DisplayName("a blank combination turns the feature off — there is no enabled flag to clear")
        void turnsAFeatureOff() {
            api.withForeground(1);
            final var controller = controller();
            controller.toggle();

            controller.rebind(Feature.WINDOW_REORDER, "");

            assertThat(Feature.WINDOW_REORDER.hotkeyIn(settings.get())).isEmpty();
        }

        @Test
        @DisplayName("the slider lands in the live configuration, and the panel redraws at its new size")
        void rescalesThePanel() {
            api.withForeground(1).withBounds(1, new Rect(0, 0, 800, 600));
            final var controller = controller();
            controller.toggle();

            controller.rescale(2.0);

            assertThat(settings.get().overlayScale()).isEqualTo(2.0);
            assertThat(view.content().orElseThrow().scale())
                    .as("the view is told at once: nothing else would redraw it")
                    .isEqualTo(2.0);
            assertThat(view.bounds())
                    .as("a bigger panel still covers the game exactly, and nothing more")
                    .contains(new Rect(8, 30, 792, 592));
        }

        @Test
        @DisplayName("a scale beyond what the slider offers is brought back inside it")
        void clampsAnImpossibleScale() {
            api.withForeground(1);
            final var controller = controller();
            controller.toggle();

            controller.rescale(0.0); // what a hand-written config.json, or a bug, could ask for

            assertThat(settings.get().overlayScale())
                    .as("a panel drawn at zero is a panel with nothing in it")
                    .isEqualTo(Config.MIN_OVERLAY_SCALE);
        }

        @Test
        @DisplayName("the auto-pass switch lands in the live configuration, and the panel shows it")
        void togglesAutoPassTurn() {
            api.withForeground(1);
            final var controller = controller();
            controller.toggle();
            assertThat(view.content().orElseThrow().autoPassTurn()).as("off by default").isFalse();

            controller.toggleAutoPassTurn(true);

            assertThat(settings.get().autoPassTurn()).isTrue();
            assertThat(view.content().orElseThrow().autoPassTurn())
                    .as("the view is told at once: nothing else would redraw it")
                    .isTrue();
        }

        @Test
        @DisplayName("the auto-accept switch lands in the live configuration, and the panel shows it")
        void togglesAutoAcceptTrade() {
            api.withForeground(1);
            final var controller = controller();
            controller.toggle();
            assertThat(view.content().orElseThrow().autoAcceptTrade()).as("on by default").isTrue();

            controller.toggleAutoAcceptTrade(false);

            assertThat(settings.get().autoAcceptTrade()).isFalse();
            assertThat(view.content().orElseThrow().autoAcceptTrade())
                    .as("the view is told at once")
                    .isFalse();
        }

        @Test
        @DisplayName("nothing is drawn while the panel is down")
        void doesNotRedrawAHiddenPanel() {
            controller().reorder(List.of("Bravo"));

            assertThat(settings.get().windowCycleOrder()).containsExactly("Bravo");
            assertThat(view.timesDrawn()).isZero();
        }
    }
}
