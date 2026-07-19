package fr.minobot.feature;

import fr.minobot.core.domain.Notification;
import fr.minobot.win32.FakeWindowApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which trades accept themselves, which are left to the player — and how the auto-focus is kept from
 * fighting the accept.
 *
 * <p>Focusing a background window makes the real {@link fr.minobot.core.FocusManager} sleep through its
 * ALT dance, so the timeout is generous, like the invitation relay's tests.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class ExchangeAccepterTest {

    private FakeWindowApi desktop() {
        return new FakeWindowApi()
                .withWindow(1, "Alpha - Dofus Retro v1.48")
                .withWindow(2, "Bravo - Dofus Retro v1.48")
                .withForeground(1);
    }

    /** B is the receiver (in the title); the asker is named in the message, no quotes, as the game writes it. */
    private static Notification tradeAsking(String receiver, String asker) {
        return new Notification(receiver + " - Dofus Retro",
                asker + " te propose de faire un échange");
    }

    private static final Map<String, Object> ON = Map.of("auto_accept_trade", true);

    @Test
    @DisplayName("a trade between two of our characters is accepted, and the screen handed back")
    void acceptsAnInternalTradeInPlace() {
        final var api = desktop().withForeground(1); // the player opened the trade from Alpha
        final var features = new Features(api, ON);

        features.exchangeAccepter().onNotification(tradeAsking("Bravo", "Alpha"));

        assertThat(features.input().actions())
                .as("Bravo is focused and accepts with ENTER")
                .contains("key:Enter");
        assertThat(api.focusedWindows())
                .as("Bravo came up to accept, then Alpha came back — a blink, not a move")
                .containsExactly(2L, 1L);
        assertThat(api.foregroundWindow())
                .as("the screen is back where the player was")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("a stranger's trade is left to the auto-focus: it brings the receiver up, nothing more")
    void leavesAStrangersTradeToTheAutoFocus() {
        final var api = desktop();
        final var features = new Features(api, ON);
        final var accepter = features.exchangeAccepter();
        final var listener = features.notificationListener(accepter);

        // Zoro is not one of our windows: a real trade offer from another player online.
        final var toast = tradeAsking("Bravo", "Zoro");
        accepter.onNotification(toast);
        listener.onNotification(toast);

        assertThat(api.foregroundWindow()).as("the receiver is brought up").isEqualTo(2);
        assertThat(features.input().actions())
                .as("but nothing is accepted on a stranger's behalf")
                .doesNotContain("key:Enter");
    }

    @Test
    @DisplayName("the auto-focus stands aside for an internal trade, so it is not focused twice")
    void theAutoFocusStandsAsideForAnInternalTrade() {
        final var api = desktop().withForeground(1);
        final var features = new Features(api, ON);
        final var accepter = features.exchangeAccepter();
        final var listener = features.notificationListener(accepter);

        final var toast = tradeAsking("Bravo", "Alpha");
        listener.onNotification(toast);   // must stand aside: it claims nothing here
        accepter.onNotification(toast);   // accepts in place, then restores Alpha

        assertThat(api.focusedWindows())
                .as("only the accept's own focus of Bravo then Alpha — the auto-focus added nothing")
                .containsExactly(2L, 1L);
        assertThat(api.foregroundWindow()).isEqualTo(1);
    }

    @Test
    @DisplayName("claims() tells the auto-focus which toasts to leave alone")
    void claimsOnlyInternalTrades() {
        final var accepter = new Features(desktop(), ON).exchangeAccepter();

        assertThat(accepter.claims(tradeAsking("Bravo", "Alpha")))
                .as("Alpha is ours: answered in place").isTrue();
        assertThat(accepter.claims(tradeAsking("Bravo", "Zoro")))
                .as("Zoro is a stranger: kept by the auto-focus").isFalse();
        assertThat(accepter.claims(new Notification("Bravo - Dofus Retro", "Vous êtes attaqué !")))
                .as("not a trade at all").isFalse();
    }

    @Test
    @DisplayName("a stranger whose name merely contains one of ours is not treated as internal")
    void matchesTheAskerAsAWholeWord() {
        final var accepter = new Features(desktop(), ON).exchangeAccepter();

        assertThat(accepter.claims(tradeAsking("Bravo", "SuperAlpha")))
                .as("SuperAlpha is not our Alpha")
                .isFalse();
        assertThat(accepter.claims(tradeAsking("Bravo", "Alpha")))
                .as("but Alpha, whole, still is")
                .isTrue();
    }

    @Test
    @DisplayName("turned off, it claims nothing and does nothing")
    void doesNothingWhenOff() {
        final var api = desktop();
        final var features = new Features(api, Map.of("auto_accept_trade", false));
        final var accepter = features.exchangeAccepter();

        accepter.onNotification(tradeAsking("Bravo", "Alpha"));

        assertThat(features.input().actions()).isEmpty();
        assertThat(api.focusedWindows()).isEmpty();
        assertThat(accepter.claims(tradeAsking("Bravo", "Alpha")))
                .as("off means the auto-focus keeps every toast")
                .isFalse();
    }

    @Test
    @DisplayName("a game toast that is not a trade offer is ignored")
    void ignoresNonTradeToasts() {
        final var api = desktop();
        final var features = new Features(api, ON);

        features.exchangeAccepter().onNotification(
                new Notification("Bravo - Dofus Retro", "Vous êtes attaqué !"));

        assertThat(features.input().actions()).isEmpty();
        assertThat(api.focusedWindows()).isEmpty();
    }
}
