package fr.minobot.feature.group;

import fr.minobot.core.domain.Notification;
import fr.minobot.core.input.FakeInput;
import fr.minobot.feature.Features;
import fr.minobot.feature.notification.NotificationListener;
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
        // Alpha invites, Bravo accepts and invites — it already has the focus — then Delta accepts,
        // and the focus goes back to Alpha.
        assertThat(api.focusedWindows()).containsExactly(1L, 2L, 3L, 1L);
        assertThat(api.foregroundWindow()).isEqualTo(1);
    }

    @Test
    @DisplayName("every command is typed into the window of the character sending it")
    void typesEachCommandIntoItsInviter() throws InterruptedException {
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

        // Alpha's command in Alpha's window, Bravo's in Bravo's: a command typed anywhere else is a
        // command the game never sees.
        assertThat(input.pastedInto()).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("no toast takes the foreground from a relay in progress")
    void keepsTheForegroundAgainstTheNotificationListener() throws InterruptedException {
        final var api = desktop().withWindow(4, "Echo - Dofus");
        final var features = new Features(api, Map.of());
        final var input = features.input();
        final var groupManager = features.groupManager();
        final var listener = features.notificationListener();

        final var sequence = Thread.ofVirtual().start(groupManager::inviteAll);
        awaitPaste(input, "/invite Bravo");

        // Echo is attacked in the middle of Alpha's command. The auto-focus would normally jump to it —
        // here it must not: the relay is typing, and the rest of the command would go to Echo's window.
        listener.onNotification(new Notification("Echo - Dofus Retro", "Vous etes attaque !"));
        assertThat(api.foregroundWindow()).isEqualTo(1);

        // The invitation toasts reach both features, the way the notification manager dispatches them.
        dispatch(invitationFor("Bravo"), groupManager, listener);
        awaitPaste(input, "/invite Delta");
        dispatch(invitationFor("Delta"), groupManager, listener);
        awaitPaste(input, "/invite Echo");
        dispatch(invitationFor("Echo"), groupManager, listener);

        sequence.join();

        assertThat(input.pasted())
                .containsExactly("/invite Bravo", "/invite Delta", "/invite Echo");
        // Each command in the window of the character sending it, from end to end.
        assertThat(input.pastedInto()).containsExactly(1L, 2L, 3L);
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

        assertThat(api.focusedWindows()).containsExactly(1L, 2L, 3L, 1L);
    }

    @Test
    @DisplayName("an invitation the game never confirms stops the relay where it is")
    void stopsWhenTheGameDoesNotConfirm() throws InterruptedException {
        final var api = desktop();
        final var features = new Features(api, Map.of());
        final var input = features.input();
        final var groupManager = features.groupManager();

        final var sequence = Thread.ofVirtual().start(groupManager::inviteAll);
        awaitPaste(input, "/invite Bravo");

        // No toast: the command may never have reached the game. Accepting an invitation that is not
        // on screen presses ENTER into the game itself, so the relay stops rather than guess.
        sequence.join();

        assertThat(input.pasted()).containsExactly("/invite Bravo");
        // Bravo is never focused: nothing was accepted, and Delta was never invited.
        assertThat(api.focusedWindows()).containsExactly(1L);
        assertThat(api.foregroundWindow()).isEqualTo(1);
    }

    @Test
    @DisplayName("a paste that did not reach the clipboard stops the relay before it confirms")
    void stopsWhenTheClipboardWasClobbered() throws InterruptedException {
        final var api = desktop();
        final var features = new Features(api, Map.of());
        final var input = features.input();
        final var groupManager = features.groupManager();

        // Another application won the clipboard, so the command never landed there. Confirming with
        // ENTER now would send whatever did — the player's clipboard — to /say. The relay must stop.
        input.failPasteOf("/invite Bravo");

        groupManager.inviteAll();

        // Nothing was pasted, and the chat line is closed with ESCAPE rather than confirmed. Only the
        // one ENTER that opened the chat was sent — never the second that would have fired its content
        // into /say.
        assertThat(input.pasted()).isEmpty();
        assertThat(input.actions()).contains("key:Escape");
        assertThat(input.actions()).filteredOn("key:Enter"::equals).hasSize(1);
        // Bravo is never focused: the relay stopped at the first step.
        assertThat(api.focusedWindows()).containsExactly(1L);
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

    /** What the notification manager does with a toast: hands it to every feature, on its own thread. */
    private static void dispatch(Notification toast, GroupManager groupManager,
                                 NotificationListener listener) {
        Thread.ofVirtual().start(() -> groupManager.onNotification(toast));
        Thread.ofVirtual().start(() -> listener.onNotification(toast));
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
