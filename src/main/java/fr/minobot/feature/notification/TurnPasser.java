package fr.minobot.feature.notification;

import fr.minobot.app.Settings;
import fr.minobot.core.FocusManager;
import fr.minobot.core.NotificationManager;
import fr.minobot.core.WindowManager;
import fr.minobot.core.domain.GameWindow;
import fr.minobot.core.domain.Notification;
import fr.minobot.core.input.Input;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Ends the turn of a character the game says it is the turn of — the feature the overlay's
 * <em>Auto-pass turns</em> switch drives.
 *
 * <p>In a fight the game raises a Windows toast for whoever it is the turn of: {@code C'est à "Bravo"
 * de jouer}. When the switch is on, this turns that toast into a keypress — it brings the character up
 * and presses the game's end-turn key — so a table of alts plays itself while the player watches.
 *
 * <p>It passes <em>every</em> character's turn, the one in the foreground included. The switch is the
 * player saying they have stepped away from the table, not that they are playing one of the seats:
 * there is no turn to leave to them. That is why there is no "except the character I am on" here, and
 * why it is off by default.
 *
 * <p>Like the group invitation, it focuses a window and then types into it, so it holds the foreground
 * for the length of that ({@link FocusManager#takeOver()}). Without it the notification auto-focus,
 * which answers the very same toast, would land between the focus and the keypress and press the
 * end-turn key into another character's window. It is a combat feature, and the group invitation is
 * done before the fight: the two are not meant to run at once.
 */
public final class TurnPasser {

    private static final Logger log = LoggerFactory.getLogger(TurnPasser.class);

    /**
     * The game's end-turn key. Dictated by the game and not the player, so it is a constant here rather
     * than a setting — {@code F1} is what ends a turn in Dofus Retro.
     */
    private static final int END_TURN_KEY = KeyEvent.VK_F1;

    /**
     * What the turn toast says: {@code C'est à "Bravo" de jouer}. Matched in the message, the character
     * taken from the title the way every game toast is written. A list so the game's other languages
     * can be added without touching the logic, as the invitation relay's keywords are.
     */
    private static final List<String> TURN_KEYWORDS = List.of("jouer");

    private final WindowManager windows;
    private final Input input;
    private final FocusManager focus;
    private final Settings settings;

    /** One turn at a time: a focus-then-press must finish before the next toast's begins, or the key
     *  meant for one window is pressed into another. */
    private final ReentrantLock passing = new ReentrantLock();

    public TurnPasser(WindowManager windows, Input input, FocusManager focus,
                      NotificationManager notifications, Settings settings) {
        this.windows = windows;
        this.input = input;
        this.focus = focus;
        this.settings = settings;

        notifications.register(this::onNotification);
    }

    /** Runs on a virtual thread, one per notification, from {@link NotificationManager}. */
    void onNotification(Notification notification) {
        // Read at every toast, never held: the overlay flips this switch while a fight is under way.
        if (!settings.get().autoPassTurn()) {
            return;
        }

        final var title = notification.title();
        if (title == null || title.isBlank() || !WindowManager.isGameTitle(title)) {
            return; // some other application's toast
        }
        if (!isTurnToast(notification.message())) {
            return; // a game toast, but not "it is your turn" — an invitation, an attack, a message
        }

        final var character = GameWindow.nameIn(title);
        windows.findWindow(character).ifPresentOrElse(
                this::passTurn,
                () -> log.warn("It is '{}'s turn, but no window was found for them.", character));
    }

    private static boolean isTurnToast(String message) {
        final var text = message.toLowerCase(Locale.ROOT);
        return TURN_KEYWORDS.stream().anyMatch(text::contains);
    }

    /**
     * Brings the character up and ends its turn.
     *
     * <p>The focus and the keypress are one act: nothing may fall between them — not the auto-focus
     * answering the same toast, not the next character's turn — or the key ends a turn in the wrong
     * window. {@link FocusManager#takeOver()} keeps the auto-focus out; {@link #passing} keeps the next
     * toast out.
     */
    private void passTurn(GameWindow character) {
        passing.lock();
        try (final var _ = focus.takeOver()) {
            if (focus.focus(character.hwnd())) {
                input.pressKey(END_TURN_KEY);
                log.info("Passed '{}'s turn.", character.name());
            } else {
                log.warn("Could not focus '{}': its turn was not passed.", character.name());
            }
        } finally {
            passing.unlock();
        }
    }
}
