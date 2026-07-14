package fr.minobot.core;

import fr.minobot.win32.FakeWindowApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two halves of the orange taskbar button: the blink, and what it leaves behind.
 *
 * <p>The blink is shortened with a setting of the user's whole Windows <em>session</em>, so what
 * matters as much as borrowing it is giving it back, whatever happened in between. The leftover orange
 * is cleared window by window, and only <em>after</em> the blink — Windows ignores the request during.
 */
class FlashSuppressorTest {

    @Test
    @DisplayName("a clicked window is swept well past the click, never only at it")
    void keepsClearingTheOrangeAfterTheClick() throws InterruptedException {
        final var api = new FakeWindowApi().withWindow(1, "Alpha - Dofus");
        final var suppressor = new FlashSuppressor(api);

        suppressor.watch(List.of(1L));

        final var atClickTime = api.flashStopsFor(1);
        Thread.sleep(300);

        // Windows only listens once its blink is over; a sweep that stops before then clears nothing.
        assertThat(api.flashStopsFor(1)).isGreaterThan(atClickTime);
    }

    @Test
    @DisplayName("nothing clicked, nothing swept")
    void sweepsNothingWhenNothingWasClicked() throws InterruptedException {
        final var api = new FakeWindowApi().withWindow(1, "Alpha - Dofus");

        new FlashSuppressor(api).watch(List.of());
        Thread.sleep(300);

        assertThat(api.flashStopsFor(1)).isZero();
    }

    @Test
    @DisplayName("Windows blinks once while Minobot runs, and gets its setting back on the way out")
    void suppressesTheFlashingAndGivesTheSettingBack() {
        final var api = new FakeWindowApi();
        final var suppressor = new FlashSuppressor(api);

        suppressor.suppress();
        // One, never zero: zero means "flash until the window is activated", not "do not flash".
        assertThat(api.foregroundFlashCount()).hasValue(1);

        suppressor.restore();
        assertThat(api.foregroundFlashCount()).hasValue(7); // the player's own, as we found it
    }

    @Test
    @DisplayName("restoring twice does not restore something we never took")
    void restoresOnlyOnce() {
        final var api = new FakeWindowApi();
        final var suppressor = new FlashSuppressor(api);

        suppressor.suppress();
        suppressor.restore();

        // The tray's Quit and the shutdown hook both land here; the second must be a no-op, and not
        // put back a "7" over a setting the player has changed in the meantime.
        api.setForegroundFlashCount(4);
        suppressor.restore();

        assertThat(api.foregroundFlashCount()).hasValue(4);
    }

    @Test
    @DisplayName("a setting that cannot be read is a setting we do not touch")
    void leavesAnUnreadableSettingAlone() {
        final var api = new FakeWindowApi().withUnreadableFlashCount();
        final var suppressor = new FlashSuppressor(api);

        suppressor.suppress();
        suppressor.restore();

        assertThat(api.foregroundFlashCount()).isEqualTo(OptionalInt.empty());
    }

    @Test
    @DisplayName("a session already down to one blink is left alone")
    void leavesAnAlreadyQuietSettingAlone() {
        final var api = new FakeWindowApi();
        api.setForegroundFlashCount(1);

        final var suppressor = new FlashSuppressor(api);
        suppressor.suppress();
        suppressor.restore();

        // Nothing was borrowed, so nothing is given back.
        assertThat(api.foregroundFlashCount()).hasValue(1);
    }

    @Test
    @DisplayName("a player whose taskbar flashes forever gets that back, however odd it is")
    void restoresEvenAnEndlessFlash() {
        final var api = new FakeWindowApi();
        api.setForegroundFlashCount(0); // Windows' way of saying "flash until I look at it"

        final var suppressor = new FlashSuppressor(api);
        suppressor.suppress();
        assertThat(api.foregroundFlashCount()).hasValue(1);

        suppressor.restore();
        assertThat(api.foregroundFlashCount()).hasValue(0);
    }
}
