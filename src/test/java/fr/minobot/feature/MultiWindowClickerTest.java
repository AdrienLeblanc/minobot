package fr.minobot.feature;

import fr.minobot.win32.FakeWindowApi;
import fr.minobot.win32.FakeWindowApi.PostedMessage;
import fr.minobot.win32.Point;
import fr.minobot.win32.Win32;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the multi-clicker actually posts, and where.
 *
 * <p>The fake models a window's client area as the screen shifted by {@code hwnd * 10}: a click at
 * screen (100, 100) is client (90, 90) in window 1 and client (80, 80) in window 2. So a test can
 * tell the two translations apart — sending the <em>same client</em> position to every window (right)
 * from sending the <em>same screen</em> position (wrong, the windows are not stacked on top of each
 * other).
 */
class MultiWindowClickerTest {

    private static final Point CURSOR = new Point(100, 100);

    /** Alphabetical by default: Alpha (1), Bravo (2), Delta (3). */
    private FakeWindowApi desktop() {
        return new FakeWindowApi()
                .withWindow(1, "Alpha - Dofus")
                .withWindow(2, "Bravo - Dofus")
                .withWindow(3, "Delta - Dofus")
                .withCursor(CURSOR);
    }

    /** The down/up pair of a left click at a client position. */
    private static List<PostedMessage> leftClickOn(long hwnd, int x, int y) {
        final var lparam = Win32.makeLParam(x, y);
        return List.of(
                new PostedMessage(hwnd, Win32.WM_LBUTTONDOWN, Win32.MK_LBUTTON, lparam),
                new PostedMessage(hwnd, Win32.WM_LBUTTONUP, 0, lparam));
    }

    @Nested
    class Positioning {

        @Test
        @DisplayName("every window is clicked at the same position in its own client area")
        void replaysTheClickAtTheEquivalentClientPosition() {
            final var api = desktop().withForeground(1).withWindowUnderCursor(1);

            new Features(api, Map.of()).clicker().clickAllWindows(CURSOR);

            // The cursor is at client (90, 90) of window 1; the others are clicked at their own
            // client (90, 90) — not at screen (100, 100), which would land somewhere else in them.
            assertThat(api.postedMessages()).containsExactlyElementsOf(
                    concat(leftClickOn(1, 90, 90), leftClickOn(2, 90, 90), leftClickOn(3, 90, 90)));
        }

        @Test
        @DisplayName("a click on the game's child window resolves to the game window")
        void walksUpFromAChildWindow() {
            final var api = desktop()
                    .withForeground(2)
                    .withWindowUnderCursor(77) // the render surface, not a top-level window
                    .withParent(77, 2);

            new Features(api, Map.of()).clicker().clickAllWindows(CURSOR);

            // Resolved to window 2, whose client position for the cursor is (80, 80).
            assertThat(api.postedMessages()).containsExactlyElementsOf(
                    concat(leftClickOn(1, 80, 80), leftClickOn(2, 80, 80), leftClickOn(3, 80, 80)));
        }

        @Test
        @DisplayName("a click outside the game is replayed nowhere")
        void postsNothingWhenTheClickDidNotLandOnAGameWindow() {
            final var api = desktop().withWindowUnderCursor(Win32.NULL_HANDLE);

            new Features(api, Map.of()).clicker().clickAllWindows(CURSOR);

            // There is no in-game spot to replay: the player clicked on the desktop or on another
            // application. Translating the screen point per window would fire every character at
            // whatever sits there in their own view, which the player never aimed at.
            assertThat(api.postedMessages()).isEmpty();
        }
    }

    @Nested
    class Targets {

        @Test
        @DisplayName("the foreground window is clicked too: the hotkey is X1, not a click")
        void clicksTheForegroundWindowAsWell() {
            final var api = desktop().withForeground(2).withWindowUnderCursor(2);

            new Features(api, Map.of()).clicker().clickAllWindows(CURSOR);

            assertThat(api.postedMessages()).extracting(PostedMessage::hwnd).contains(2L);
        }

        @Test
        void skipsTheMinimizedWindows() {
            final var api = desktop().minimize(3).withForeground(1).withWindowUnderCursor(1);

            new Features(api, Map.of()).clicker().clickAllWindows(CURSOR);

            assertThat(api.postedMessages()).extracting(PostedMessage::hwnd).containsOnly(1L, 2L);
        }

        @Test
        @DisplayName("multiclick_exclude keeps a character out of the click")
        void skipsTheExcludedWindows() {
            final var api = desktop().withForeground(1).withWindowUnderCursor(1);

            new Features(api, Map.of("multiclick_exclude", List.of("delta")))
                    .clicker().clickAllWindows(CURSOR);

            assertThat(api.postedMessages()).extracting(PostedMessage::hwnd).containsOnly(1L, 2L);
        }

        @Test
        @DisplayName("the player's window keeps the focus: no window is ever raised")
        void neverStealsTheFocus() {
            final var api = desktop().withForeground(1).withWindowUnderCursor(1);

            new Features(api, Map.of()).clicker().clickAllWindows(CURSOR);

            assertThat(api.foregroundWindow()).isEqualTo(1);
        }
    }

    @Nested
    class AttentionReset {

        @Test
        @DisplayName("visits every window back to front, then hands the focus back to the leader")
        void visitsEveryWindowAndReturnsToTheLeader() {
            final var api = desktop().withForeground(1);

            new Features(api, Map.of("window_cycle_order", List.of("Delta", "Bravo", "Alpha")))
                    .clicker().resetWindowsAttentionState();

            // Configured order is Delta (3), Bravo (2), Alpha (1): visited back to front, then the
            // focus goes back to the leader, Delta — which is where the player was playing.
            assertThat(api.focusedWindows()).containsExactly(1L, 2L, 3L, 3L);
            assertThat(api.foregroundWindow()).isEqualTo(3);
        }

        @Test
        @DisplayName("with no configured order, the leader is still the first window, not the last")
        void picksTheLeaderFromTheConfiguredOrder() {
            final var api = desktop().withForeground(3);

            new Features(api, Map.of()).clicker().resetWindowsAttentionState();

            // Alphabetical for want of a configured order: the leader is Alpha, and the reversal is a
            // real one — sorting by rank would leave the list untouched, every window sharing a rank.
            assertThat(api.focusedWindows()).containsExactly(3L, 2L, 1L, 1L);
            assertThat(api.foregroundWindow()).isEqualTo(1);
        }
    }

    @SafeVarargs
    private static List<PostedMessage> concat(List<PostedMessage>... clicks) {
        return Stream.of(clicks).flatMap(List::stream).toList();
    }
}
