package fr.minobot.feature;

import fr.minobot.core.FocusManager;
import fr.minobot.core.NotificationManager;
import fr.minobot.core.WindowManager;
import fr.minobot.core.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Focuses the character a Windows toast is about — the counterpart of {@code notification_listener.py}.
 *
 * <p>The game raises a toast whenever a background character is attacked, messaged or invited; this
 * turns that toast into a focus. The focus is <em>smart</em>: if the player is typing at that very
 * moment, the window is not stolen from under their keystrokes.
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
            return;
        }

        log.debug("[GAME NOTIF] {} - {}", title, notification.message());

        final var character = windows.extractCharacterName(title);
        if (character.isBlank()) {
            log.warn("Could not extract a character name from '{}'.", title);
            return;
        }

        // A focus that yields: a toast is a convenience, and the group invitation relay waits on these
        // very toasts. Focusing here in the middle of one would send its next command to this window.
        windows.findWindow(character).ifPresentOrElse(
                window -> focus.focusIfIdle(window.hwnd()),
                () -> log.warn("No window found for the character '{}'.", character));
    }

}
