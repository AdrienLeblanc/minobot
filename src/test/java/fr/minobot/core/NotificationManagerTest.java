package fr.minobot.core;

import fr.minobot.core.domain.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** The toast parsing, on the payloads Windows actually stores. */
class NotificationManagerTest {

    /** Captured verbatim from wpndatabase.db during the phase 0 spike. */
    private static final String REAL_TOAST = """
            <toast><visual><binding template="ToastText02">\
            <text id="1">Lenore - Dofus Retro</text>\
            <text id="2">Vous avez recu une invitation de groupe</text>\
            </binding></visual></toast>""";

    @Test
    @DisplayName("the first text is the title, the second the message")
    void parsesARealToast() {
        final var notification = NotificationManager.parseToast(REAL_TOAST);

        assertThat(notification).contains(new Notification(
                "Lenore - Dofus Retro",
                "Vous avez recu une invitation de groupe"));
    }

    @Test
    void parsesAToastThatCarriesOnlyATitle() {
        final var payload = "<toast><visual><binding><text>Lenore - Dofus</text></binding></visual></toast>";

        assertThat(NotificationManager.parseToast(payload)).contains(new Notification("Lenore - Dofus", ""));
    }

    @Test
    @DisplayName("Windows stores tiles and badges in the same table — those are not ours")
    void ignoresAPayloadThatIsNotAToast() {
        assertThat(NotificationManager.parseToast("<tile><visual/></tile>")).isEmpty();
        assertThat(NotificationManager.parseToast("")).isEmpty();
        assertThat(NotificationManager.parseToast(null)).isEmpty();
    }

    @Test
    void ignoresAToastWithoutText() {
        assertThat(NotificationManager.parseToast("<toast><visual><binding/></visual></toast>")).isEmpty();
    }

    @Test
    void ignoresAMalformedPayload() {
        assertThat(NotificationManager.parseToast("<toast><text>truncated")).isEmpty();
    }

    @Test
    @DisplayName("the payload comes from outside the app: a DTD must not be honoured")
    void refusesAnExternalEntity() {
        final var xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE toast [<!ENTITY secret SYSTEM "file:///c:/windows/win.ini">]>
                <toast><visual><binding><text>&secret;</text></binding></visual></toast>""";

        assertThat(NotificationManager.parseToast(xxe).isEmpty()).as("the DOCTYPE must be rejected outright").isTrue();
    }
}
