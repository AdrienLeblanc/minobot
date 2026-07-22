package fr.minobot.feature.notification;

import fr.minobot.app.Settings;
import fr.minobot.app.TestConfigs;
import fr.minobot.core.WindowManager;
import fr.minobot.ui.BannerContent;
import fr.minobot.ui.FakeBannerView;
import fr.minobot.win32.FakeWindowApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When the auto-pass banner is up, where it hangs, and what its close cross does.
 *
 * <p>The banner is drawn by a follow thread on a 30&nbsp;ms poll, so a test lets it settle before reading
 * the view. It watches the {@code auto_pass_turn} switch through {@link Settings#onChange}, so a test flips
 * the switch the way the overlay or the hotkey does — with {@code settings.update}.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class AutoPassBannerTest {

    private final FakeWindowApi api = new FakeWindowApi()
            .withWindow(1, "Alpha - Dofus Retro v1.48")
            .withWindow(2, "Slack")
            .withForeground(1); // the player is on a game window

    private final Settings settings = TestConfigs.settings(Map.of());

    private FakeBannerView view;

    private AutoPassBanner banner() {
        final var windows = new WindowManager(api, settings);
        return new AutoPassBanner(api, windows, settings, actions -> view = new FakeBannerView(actions));
    }

    private void on() {
        settings.update(config -> config.withAutoPassTurn(true));
    }

    private void off() {
        settings.update(config -> config.withAutoPassTurn(false));
    }

    /** The follow loop polls every 30 ms; this is room for several turns of it. */
    private void settle() throws InterruptedException {
        Thread.sleep(250);
    }

    @Test
    @DisplayName("nothing shows while the switch is off — which is where it starts")
    void offByDefault() throws InterruptedException {
        banner();
        settle();

        assertThat(view.isVisible()).isFalse();
        assertThat(view.timesDrawn()).isZero();
    }

    @Test
    @DisplayName("switching auto-pass on raises the banner over the foreground game, with the message")
    void showsWhenSwitchedOn() throws InterruptedException {
        banner();

        on();
        settle();

        assertThat(view.isVisible()).isTrue();
        assertThat(view.content()).map(BannerContent::message).contains(AutoPassBanner.MESSAGE);
        assertThat(view.anchor()).contains(api.clientArea(1).orElseThrow());
    }

    @Test
    @DisplayName("switching auto-pass off takes the banner down")
    void hidesWhenSwitchedOff() throws InterruptedException {
        banner();
        on();
        settle();
        assertThat(view.isVisible()).isTrue();

        off();
        settle();

        assertThat(view.isVisible()).isFalse();
    }

    @Test
    @DisplayName("the close cross only hides it: the switch stays on and it does not come back on its own")
    void crossHidesButLeavesTheFeatureRunning() throws InterruptedException {
        banner();
        on();
        settle();

        view.clickClose();
        settle();

        assertThat(view.isVisible()).as("the banner is gone").isFalse();
        assertThat(settings.get().autoPassTurn()).as("but auto-pass is still on").isTrue();

        settle(); // give the follow loop every chance to redraw it
        assertThat(view.isVisible()).as("and it stays gone while still enabled").isFalse();
    }

    @Test
    @DisplayName("turning the switch off and on again brings a dismissed banner back")
    void reappearsOnReEnable() throws InterruptedException {
        banner();
        on();
        settle();
        view.clickClose();
        settle();
        assertThat(view.isVisible()).isFalse();

        off();
        on();
        settle();

        assertThat(view.isVisible()).as("a deliberate re-enable shows it again").isTrue();
    }

    @Test
    @DisplayName("outside the game there is nothing to hang the banner on")
    void notShownOutsideTheGame() throws InterruptedException {
        api.withForeground(2); // Slack, not a game window
        banner();

        on();
        settle();

        assertThat(view.isVisible()).isFalse();
        assertThat(view.timesDrawn()).isZero();
    }
}
