package fr.minobot.feature;

import fr.minobot.core.FocusManager;
import fr.minobot.core.domain.GameWindow;
import fr.minobot.core.input.Input;
import fr.minobot.core.domain.Notification;
import fr.minobot.core.NotificationManager;
import fr.minobot.core.WindowManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Chain-invites every character into one group — the counterpart of {@code group_manager.py}.
 *
 * <p>A relay: the first character invites the second, the second accepts and invites the third, and
 * so on. Each step needs the previous one to have actually landed in the game, which is where the
 * Windows toast comes in: the game raises one when a character is invited, and the sequence waits for
 * it rather than guessing a delay. The {@code asyncio.Event} becomes a
 * {@link CountDownLatch}; the five-second timeout is the same, and it proceeds anyway on expiry —
 * the toast may simply be disabled.
 */
public final class GroupManager {

    private static final Logger log = LoggerFactory.getLogger(GroupManager.class);

    private static final long NOTIFICATION_TIMEOUT_SECONDS = 5;

    /** What an invitation toast says, across the game's languages. */
    private static final List<String> INVITE_KEYWORDS = List.of("invite", "groupe", "group");

    private static final int AFTER_CHAT_OPEN_MILLIS = 25;
    private static final int BEFORE_ACCEPT_MILLIS = 25;

    private final WindowManager windows;
    private final Input input;
    private final FocusManager focus;

    private final AtomicBoolean running = new AtomicBoolean();

    /** The step {@link #inviteAll()} is currently waiting on, or {@code null} between two steps. */
    private final AtomicReference<PendingInvite> pending = new AtomicReference<>();

    public GroupManager(WindowManager windows, Input input, FocusManager focus,
                        NotificationManager notifications) {
        this.windows = windows;
        this.input = input;
        this.focus = focus;

        notifications.register(this::onNotification);
    }

    /** Runs on a notification's virtual thread, and releases the step waiting in {@link #inviteAll()}. */
    void onNotification(Notification notification) {
        // One read: the awaited name and its latch must come from the same step, or a toast landing
        // between two steps could release the latch of the next one, which would then not wait at all.
        final var step = pending.get();
        if (step == null) {
            return; // no sequence running, or between two steps
        }

        if (!notification.title().contains(step.invitee()) || !isInvitation(notification)) {
            return;
        }

        log.info("Invitation toast received for '{}'.", step.invitee());
        step.received().countDown();
    }

    private static boolean isInvitation(Notification notification) {
        final var message = notification.message().toLowerCase(Locale.ROOT);
        return INVITE_KEYWORDS.stream().anyMatch(message::contains);
    }

    /** Invites every character into the first one's group, then hands the focus back to it. */
    public void inviteAll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("The group invitation sequence is already running.");
            return;
        }

        try {
            windows.refresh();
            final var characters = windows.orderedWindows();
            if (characters.size() < 2) {
                log.warn("Only {} character(s) found; at least 2 are needed to form a group.",
                        characters.size());
                return;
            }

            final var leader = characters.getFirst();
            final var leaderName = nameOf(leader);
            log.info("Starting the group invitation sequence, led by '{}'.", leaderName);

            for (var step = 0; step < characters.size() - 1; step++) {
                invite(characters.get(step), characters.get(step + 1), step + 1);
            }

            log.info("Group invitation complete; returning the focus to '{}'.", leaderName);
            focus.focus(leader.hwnd());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            log.warn("The group invitation sequence was interrupted.");
        } catch (RuntimeException e) {
            log.error("Error during the group invitation sequence.", e);
        } finally {
            pending.set(null);
            running.set(false);
        }
    }

    private void invite(GameWindow inviter, GameWindow invitee, int step) throws InterruptedException {
        final var inviteeName = nameOf(invitee);
        final var inviterName = nameOf(inviter);
        log.info("Step {}: '{}' is inviting '{}'...", step, inviterName, inviteeName);

        // Armed before the command is sent: the toast can arrive before we get to the wait below.
        final var latch = new CountDownLatch(1);
        pending.set(new PendingInvite(inviteeName, latch));

        focus.focus(inviter.hwnd());
        input.pressKey(KeyEvent.VK_ENTER); // opens the chat
        Thread.sleep(AFTER_CHAT_OPEN_MILLIS);
        input.pasteString("/invite " + inviteeName);
        input.pressKey(KeyEvent.VK_ENTER); // sends it

        if (!latch.await(NOTIFICATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            log.warn("No invitation toast for '{}' within {}s; proceeding anyway.",
                    inviteeName, NOTIFICATION_TIMEOUT_SECONDS);
        }

        focus.focus(invitee.hwnd());
        Thread.sleep(BEFORE_ACCEPT_MILLIS); // the window must be ready to take the keystroke
        input.pressKey(KeyEvent.VK_ENTER); // accepts
        log.debug("'{}' joined the group.", inviteeName);
    }

    private String nameOf(GameWindow window) {
        return windows.extractCharacterName(window.title());
    }

    /** One step of the relay: the invited character, and the latch its toast releases. */
    private record PendingInvite(String invitee, CountDownLatch received) {
    }
}
