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
                .withWindow(1, "Aria - Dofus Retro v1.48")
                .withWindow(2, "Lenore - Dofus Retro v1.48")
                .withForeground(1);
    }

    @Test
    @DisplayName("focuses the character the toast is about")
    void focusesTheCharacter() {
        final var api = desktop();

        new Features(api, Map.of()).notificationListener()
                .onNotification(new Notification("Lenore - Dofus Retro", "Vous avez recu une invitation"));

        assertThat(api.foregroundWindow()).isEqualTo(2);
    }

    @Test
    @DisplayName("a toast from another application is ignored")
    void ignoresForeignNotifications() {
        final var api = desktop();

        new Features(api, Map.of()).notificationListener()
                .onNotification(new Notification("Slack - Lenore", "Hello"));

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
}
