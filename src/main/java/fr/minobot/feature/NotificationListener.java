package fr.minobot.feature;

import fr.minobot.core.FocusManager;
import fr.minobot.core.NotificationManager;
import fr.minobot.core.WindowManager;
import fr.minobot.core.domain.GameWindow;
import fr.minobot.core.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Takes the player to the character who needs them — the counterpart of {@code notification_listener.py}.
 *
 * <p>The game raises a Windows toast whenever a background character is attacked, messaged or invited.
 * This turns that toast into a move: the character who called out gets the screen, without the player
 * having to look for them.
 *
 * <p>It is a move that <em>yields</em>: it never takes the screen from under a player who is in the
 * middle of something, and never from under one of the scripted sequences.
 */
public final class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final WindowManager windows;
    private final FocusManager focus;

    public NotificationListener(WindowManager windows, FocusManager focus,
                                NotificationManager notifications) {
        this.windows = windows;
        this.focus = focus;

        notifications.register(this::onNotification);
    }

    /** Runs on a virtual thread, one per notification, from {@link NotificationManager}. */
    void onNotification(Notification notification) {
        final var title = notification.title();
        if (title == null || title.isBlank() || !WindowManager.isGameTitle(title)) {
            return; // some other application's toast
        }

        log.debug("The game is calling out: {} - {}", title, notification.message());

        final var character = GameWindow.nameIn(title);
        if (character.isBlank()) {
            log.warn("Could not tell which character '{}' is about.", title);
            return;
        }

        windows.findWindow(character).ifPresentOrElse(
                this::goTo,
                () -> log.warn("No window found for the character '{}'.", character));
    }

    /**
     * Hands the screen to the character, unless someone else is using it.
     *
     * <p>A toast is a convenience, and the group invitation relay waits on these very toasts: taking
     * the screen in the middle of one would send the rest of its {@code /invite} to this character's
     * window.
     */
    private void goTo(GameWindow character) {
        focus.focusIfIdle(character.hwnd());
    }
}
