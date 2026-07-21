package fr.minobot.feature;

import fr.minobot.core.domain.Notification;
import fr.minobot.win32.FakeWindowApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Which toasts pull the focus, and which are none of our business. */
class NotificationListenerTest {

    private FakeWindowApi desktop() {
        return new FakeWindowApi()
                .withWindow(1, "Alpha - Dofus Retro v1.48")
                .withWindow(2, "Bravo - Dofus Retro v1.48")
                .withForeground(1);
    }

    @Test
    @DisplayName("focuses the character the toast is about")
    void focusesTheCharacter() {
        final var api = desktop();

        new Features(api, Map.of()).notificationListener()
                .onNotification(new Notification("Bravo - Dofus Retro", "Vous avez recu une invitation"));

        assertThat(api.foregroundWindow()).isEqualTo(2);
    }

    @Test
    @DisplayName("a toast from another application is ignored")
    void ignoresForeignNotifications() {
        final var api = desktop();

        new Features(api, Map.of()).notificationListener()
                .onNotification(new Notification("Slack - Bravo", "Hello"));

        assertThat(api.focusedWindows()).isEmpty();
    }

    @Test
    @DisplayName("a character with no window on screen is reported, not focused")
    void ignoresAnUnknownCharacter() {
        final var api = desktop();

        new Features(api, Map.of()).notificationListener()
                .onNotification(new Notification("Ghost - Dofus Retro", "Vous avez recu une invitation"));

        assertThat(api.focusedWindows()).isEmpty();
    }

    @Test
    @DisplayName("it stands aside for a whisper the toaster answers with a card, rather than focusing")
    void standsAsideForAWhisper() {
        final var api = desktop();
        final var features = new Features(api, Map.of());
        final var toaster = features.whisperToaster();
        final var listener = features.notificationListener(toaster::claims);

        // A whisper for Bravo: the toaster shows a card, so the auto-focus must not pull the screen over.
        listener.onNotification(new Notification("Bravo - Dofus Retro", "de Alpha : Bonjour"));

        assertThat(api.focusedWindows())
                .as("a whisper is a card at the edge, never a jump")
                .isEmpty();
    }

    @Test
    @DisplayName("a non-whisper game toast still pulls the focus, whisper toaster or not")
    void stillFocusesANonWhisperToast() {
        final var api = desktop();
        final var features = new Features(api, Map.of());
        final var toaster = features.whisperToaster();
        final var listener = features.notificationListener(toaster::claims);

        listener.onNotification(new Notification("Bravo - Dofus Retro", "Vous êtes attaqué !"));

        assertThat(api.foregroundWindow()).as("an attack is still a jump").isEqualTo(2);
    }
}
