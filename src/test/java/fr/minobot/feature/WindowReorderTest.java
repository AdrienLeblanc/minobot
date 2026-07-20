package fr.minobot.feature;

import fr.minobot.app.TestConfigs;
import fr.minobot.win32.FakeWindowApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The hide-then-show sequence that rebuilds the taskbar order. */
class WindowReorderTest {

    private static final Map<String, Object> CYCLE_ORDER =
            Map.of("characters", TestConfigs.characters("Delta", "Bravo", "Alpha"));

    private FakeWindowApi desktop() {
        return new FakeWindowApi()
                .withWindow(1, "Alpha - Dofus")
                .withWindow(2, "Bravo - Dofus")
                .withWindow(3, "Delta - Dofus");
    }

    @Test
    @DisplayName("shows the windows back in the configured order, and leaves none hidden")
    void rebuildsTheTaskbarOrder() {
        final var api = desktop();

        new Features(api, CYCLE_ORDER).reorder().reorderTaskbar();

        assertThat(api.shownWindows()).containsExactly(3L, 2L, 1L);
        assertThat(api.isHidden(1)).isFalse();
        assertThat(api.isHidden(2)).isFalse();
        assertThat(api.isHidden(3)).isFalse();
    }

    @Test
    @DisplayName("brings every character back maximized, not merely visible")
    void maximizesEveryWindow() {
        final var api = desktop().minimize(2);

        new Features(api, CYCLE_ORDER).reorder().reorderTaskbar();

        assertThat(api.isMaximized(1)).isTrue();
        assertThat(api.isMaximized(2)).isTrue();
        assertThat(api.isMaximized(3)).isTrue();
    }

    @Test
    @DisplayName("the first window of the order ends up focused")
    void focusesTheFirstWindow() {
        final var api = desktop();

        new Features(api, CYCLE_ORDER).reorder().reorderTaskbar();

        assertThat(api.foregroundWindow()).isEqualTo(3);
    }

    @Test
    @DisplayName("a minimized window is restored on the way, not left out of the taskbar")
    void restoresTheMinimizedWindows() {
        final var api = desktop().minimize(2);

        new Features(api, CYCLE_ORDER).reorder().reorderTaskbar();

        assertThat(api.shownWindows()).contains(2L);
        assertThat(api.isIconic(2)).isFalse();
    }

    @Test
    void doesNothingWithoutGameWindows() {
        final var api = new FakeWindowApi().withWindow(9, "Untitled - Notepad");

        new Features(api, CYCLE_ORDER).reorder().reorderTaskbar();

        assertThat(api.shownWindows()).isEmpty();
        assertThat(api.isHidden(9)).isFalse();
    }
}
