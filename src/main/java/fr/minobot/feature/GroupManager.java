package fr.minobot.feature;

import fr.minobot.core.FocusManager;
import fr.minobot.core.NotificationManager;
import fr.minobot.core.WindowManager;
import fr.minobot.core.domain.GameWindow;
import fr.minobot.core.domain.Notification;
import fr.minobot.core.input.Input;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.KeyEvent;
import java.time.Duration;
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
 * it rather than guessing a delay.
 *
 * <p>The relay types into the game, so it holds the foreground from end to end
 * ({@link FocusManager#takeOver()}). It has to: the toast it waits for at every step is the same one
 * the notification auto-focus reacts to, and that focus, left free, arrives in the middle of a
 * {@code /invite} and sends the rest of it to another character's window.
 */
public final class GroupManager {

    private static final Logger log = LoggerFactory.getLogger(GroupManager.class);

    /** How long the game is given to confirm an invitation with its toast. */
    private static final Duration CONFIRMATION_TIMEOUT = Duration.ofSeconds(5);

    /** What an invitation toast says, across the game's languages. */
    private static final List<String> INVITE_KEYWORDS = List.of("invite", "groupe", "group");

    /** Time the chat line takes to appear, before the command can be pasted into it. */
    private static final int CHAT_OPEN_MILLIS = 25;

    private final WindowManager windows;
    private final Input input;
    private final FocusManager focus;

    private final AtomicBoolean running = new AtomicBoolean();

    /** The toast the relay is waiting on, or {@code null} outside a step. */
    private final AtomicReference<Confirmation> awaited = new AtomicReference<>();

    public GroupManager(WindowManager windows, Input input, FocusManager focus,
                        NotificationManager notifications) {
        this.windows = windows;
        this.input = input;
        this.focus = focus;

        notifications.register(this::onNotification);
    }

    /** Invites every character into the first one's group, then hands the focus back to it. */
    public void inviteAll() {
        if (!running.compareAndSet(false, true)) {
            log.warn("The group invitation sequence is already running.");
            return;
        }

        try (final var _ = focus.takeOver()) {
            windows.refresh();
            final var characters = windows.orderedWindows();
            if (characters.size() < 2) {
                log.warn("Only {} character(s) found; at least 2 are needed to form a group.",
                        characters.size());
                return;
            }

            final var leader = characters.getFirst();
            log.info("Inviting {} characters into the group of '{}'.", characters.size(), leader.name());

            relay(characters);

            log.info("Returning the focus to '{}'.", leader.name());
            focus.focus(leader.hwnd());
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            log.warn("The group invitation sequence was interrupted.");
        } catch (RuntimeException e) {
            log.error("Error during the group invitation sequence.", e);
        } finally {
            awaited.set(null);
            running.set(false);
        }
    }

    /** Each character invites the next one, who accepts and becomes the inviter of the one after. */
    private void relay(List<GameWindow> characters) throws InterruptedException {
        for (var step = 0; step < characters.size() - 1; step++) {
            final var invitee = characters.get(step + 1);

            if (!inviteAndAccept(characters.get(step), invitee)) {
                log.error("The relay stops at '{}'; the characters after it stay out of the group.",
                        invitee.name());
                return;
            }
        }

        log.info("Every character is in the group.");
    }

    /** One link of the relay: the inviter sends the command, the invitee accepts what it gets. */
    private boolean inviteAndAccept(GameWindow inviter, GameWindow invitee) throws InterruptedException {
        log.info("'{}' is inviting '{}'...", inviter.name(), invitee.name());

        // Armed before the command goes out: the game's toast can beat us to the wait below.
        final var confirmation = expectInvitationFor(invitee.name());

        return sendInvite(inviter, invitee.name())
                && confirmed(confirmation)
                && accept(invitee);
    }

    /** Types {@code /invite Name} in the inviter's chat. */
    private boolean sendInvite(GameWindow inviter, String invitee) throws InterruptedException {
        if (!focus.focus(inviter.hwnd())) {
            log.error("Could not focus '{}': its invitation would have been typed elsewhere.", inviter.name());
            return false;
        }

        input.pressKey(KeyEvent.VK_ENTER); // opens the chat
        Thread.sleep(CHAT_OPEN_MILLIS);
        input.pasteString("/invite " + invitee);
        input.pressKey(KeyEvent.VK_ENTER); // sends it

        return true;
    }

    /** Waits for the game to confirm, with its toast, that the invitation reached the character. */
    private boolean confirmed(Confirmation confirmation) throws InterruptedException {
        if (confirmation.arrivedWithin(CONFIRMATION_TIMEOUT)) {
            return true;
        }

        // Accepting anyway would press ENTER in a window with no invitation on screen, where it does
        // not accept anything: it opens the chat, and the next step's command is typed into the game.
        log.warn("No invitation toast for '{}' within {}s; the game may not have received the command, "
                        + "or its Windows notifications may be off — the relay needs them to pace itself.",
                confirmation.character(), CONFIRMATION_TIMEOUT.toSeconds());
        return false;
    }

    /** Presses ENTER on the invitation the game is showing the invited character. */
    private boolean accept(GameWindow invitee) {
        if (!focus.focus(invitee.hwnd())) {
            log.error("Could not focus '{}': its invitation stays on screen, unaccepted.", invitee.name());
            return false;
        }

        input.pressKey(KeyEvent.VK_ENTER);
        log.debug("'{}' joined the group.", invitee.name());
        return true;
    }

    private Confirmation expectInvitationFor(String character) {
        final var confirmation = new Confirmation(character, new CountDownLatch(1));
        awaited.set(confirmation);
        return confirmation;
    }

    /** Runs on a notification's virtual thread, and releases the step waiting in {@link #inviteAll()}. */
    void onNotification(Notification notification) {
        // One read: the awaited name and its latch must come from the same step, or a toast landing
        // between two steps could release the latch of the next one, which would then not wait at all.
        final var confirmation = awaited.get();
        if (confirmation == null || !confirmation.matches(notification)) {
            return;
        }

        log.info("Invitation toast received for '{}'.", confirmation.character());
        confirmation.arrived();
    }

    /** The toast that tells one step of the relay it has landed: the invited character, and the wait. */
    private record Confirmation(String character, CountDownLatch received) {

        boolean matches(Notification toast) {
            final var message = toast.message().toLowerCase(Locale.ROOT);
            return toast.title().contains(character)
                    && INVITE_KEYWORDS.stream().anyMatch(message::contains);
        }

        void arrived() {
            received.countDown();
        }

        boolean arrivedWithin(Duration timeout) throws InterruptedException {
            return received.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }
    }
}
