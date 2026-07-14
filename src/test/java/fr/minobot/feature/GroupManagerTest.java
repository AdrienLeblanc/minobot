package fr.minobot.feature;

import fr.minobot.core.input.FakeInput;
import fr.minobot.core.domain.Notification;
import fr.minobot.win32.FakeWindowApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * The invitation relay, and the toast that paces it.
 *
 * <p>{@code inviteAll} blocks on each step until the game's toast arrives, so the tests run it on its
 * own thread and hand it the toasts — which is exactly what the notification manager does in
 * production, on a virtual thread of its own.
 */
@Timeout(value = 30, unit = TimeUnit.SECONDS)
class GroupManagerTest {

    /** Alphabetical by default: Alpha (1) invites Bravo (2), who invites Delta (3). */
    private FakeWindowApi desktop() {
        return new FakeWindowApi()
                .withWindow(1, "Alpha - Dofus")
                .withWindow(2, "Bravo - Dofus")
                .withWindow(3, "Delta - Dofus");
    }

    private static Notification invitationFor(String character) {
        return new Notification(character + " - Dofus Retro", "Vous avez recu une invitation de groupe");
    }

    @Test
    @DisplayName("each character invites the next, and the leader gets the focus back")
    void relaysTheInvitationDownTheOrder() throws InterruptedException {
        final var api = desktop();
        final var features = new Features(api, Map.of());
        final var input = features.input();
        final var groupManager = features.groupManager();

        final var sequence = Thread.ofVirtual().start(groupManager::inviteAll);

        awaitPaste(input, "/invite Bravo");
        groupManager.onNotification(invitationFor("Bravo"));

        awaitPaste(input, "/invite Delta");
        groupManager.onNotification(invitationFor("Delta"));

        sequence.join();

        assertThat(input.pasted()).containsExactly("/invite Bravo", "/invite Delta");
        // Alpha invites, Bravo accepts; Bravo invites, Delta accepts; the focus goes back to Alpha.
        assertThat(api.focusedWindows()).containsExactly(1L, 2L, 2L, 3L, 1L);
        assertThat(api.foregroundWindow()).isEqualTo(1);
    }

    @Test
    @DisplayName("a toast that is not an invitation does not advance the sequence")
    void waitsForTheInvitationToast() throws InterruptedException {
        final var api = desktop();
        final var features = new Features(api, Map.of());
        final var input = features.input();
        final var groupManager = features.groupManager();

        final var sequence = Thread.ofVirtual().start(groupManager::inviteAll);
        awaitPaste(input, "/invite Bravo");

        // Right character, but Bravo was attacked, not invited.
        groupManager.onNotification(new Notification("Bravo - Dofus Retro", "Vous etes attaque !"));
        Thread.sleep(300);

        // Still waiting on the inviter: had this released the step, Bravo would hold the focus.
        assertThat(api.focusedWindows()).containsExactly(1L);

        groupManager.onNotification(invitationFor("Bravo"));
        awaitPaste(input, "/invite Delta");
        groupManager.onNotification(invitationFor("Delta"));
        sequence.join();

        assertThat(api.focusedWindows()).containsExactly(1L, 2L, 2L, 3L, 1L);
    }

    @Test
    @DisplayName("a lone character is not a group")
    void refusesToInviteWithASingleCharacter() {
        final var api = new FakeWindowApi().withWindow(1, "Alpha - Dofus");
        final var features = new Features(api, Map.of());

        features.groupManager().inviteAll();

        assertThat(features.input().actions()).isEmpty();
        assertThat(api.focusedWindows()).isEmpty();
    }

    /** Waits for the sequence to reach the step that pastes this command. */
    private static void awaitPaste(FakeInput input, String command) throws InterruptedException {
        final var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            if (input.pasted().contains(command)) {
                return;
            }
            Thread.sleep(10);
        }
        fail("The sequence never pasted '%s'; it did: %s", command, List.copyOf(input.actions()));
    }
}
