package fr.minobot.feature.notification;

import fr.minobot.app.Settings;
import fr.minobot.core.FocusManager;
import fr.minobot.core.NotificationManager;
import fr.minobot.core.WindowManager;
import fr.minobot.core.domain.GameWindow;
import fr.minobot.core.domain.Notification;
import fr.minobot.core.input.Input;
import fr.minobot.win32.Win32;
import fr.minobot.win32.WindowApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Accepts a trade one of the player's own characters asks of another — the feature the overlay's
 * <em>Auto-accept trades</em> switch drives.
 *
 * <p>When character A opens an exchange with character B, it is <em>B</em> that the game raises a toast
 * for — {@code Alpha te propose de faire un échange} — and that toast <strong>names the one who
 * asked</strong>. That name is the whole trick: the message carries the asker's name, so the exchange
 * is <em>internal</em> when that name is one of the player's own windows. If it is a stranger's, the
 * request is left exactly as it was — the notification auto-focus takes the player to the receiving
 * window and does nothing else, which is what a real trade offer should do.
 *
 * <p><strong>The player did not ask to change windows, so this tries not to.</strong> But sending a
 * keystroke to a window means the window must hold the keyboard, and the reliable way to give it the
 * keyboard is to focus it. So the accept is a blink: {@link FocusManager#takeOver()} to keep the
 * auto-focus out, focus the receiver, press the accept key, and hand the screen straight back to where
 * the player was. If a truly background keystroke (a posted message) is ever shown to work against the
 * game, {@link #accept} is the one method that would change.
 */
public final class ExchangeAccepter {

    private static final Logger log = LoggerFactory.getLogger(ExchangeAccepter.class);

    /**
     * The key that accepts an exchange in Dofus Retro — {@code ENTER}. Dictated by the game, so it is a
     * constant here and not a setting. Pressed only once the receiver holds the foreground and only for a
     * request the game actually raised: an ENTER into a window with no offer on screen opens the chat,
     * the very trap the invitation relay guards against.
     */
    private static final int ACCEPT_KEY = KeyEvent.VK_ENTER;

    /** What the offer toast says, SECONDARY stripped so a de-SECONDARYed message still matches. */
    private static final List<String> EXCHANGE_KEYWORDS = List.of("échange", "echange");

    private final WindowApi api;
    private final WindowManager windows;
    private final Input input;
    private final FocusManager focus;
    private final Settings settings;

    /** One accept at a time: the focus-press-restore must not interleave with the next toast's. */
    private final ReentrantLock accepting = new ReentrantLock();

    public ExchangeAccepter(WindowApi api, WindowManager windows, Input input, FocusManager focus,
                            NotificationManager notifications, Settings settings) {
        this.api = api;
        this.windows = windows;
        this.input = input;
        this.focus = focus;
        this.settings = settings;

        notifications.register(this::onNotification);
    }

    /** Runs on a virtual thread, one per notification, from {@link NotificationManager}. */
    void onNotification(Notification notification) {
        if (!settings.get().autoAcceptTrade()) {
            return;
        }

        final var receiver = internalTradeReceiver(notification);
        if (receiver.isEmpty()) {
            // Not a trade, or a stranger's: the auto-focus takes it, and that is all it should do.
            return;
        }

        windows.findWindow(receiver.get()).ifPresentOrElse(
                this::accept,
                () -> log.warn("'{}' was asked to trade, but no window was found for them.", receiver.get()));
    }

    /**
     * Whether this listener will answer the toast in place — read by {@link NotificationListener} so it
     * does not also jump to the receiver. A pure function of the toast, the switch and the windows on
     * screen, so both features decide the same without coordinating.
     */
    public boolean claims(Notification notification) {
        return settings.get().autoAcceptTrade() && internalTradeReceiver(notification).isPresent();
    }

    /**
     * The receiver of a trade asked by one of the player's <em>own</em> characters, or empty otherwise.
     *
     * <p>The receiver is in the title, as every toast is written; the asker is named in the message
     * ({@code Alpha te propose…}), without quotes. So a trade is internal when the message carries the
     * name of one of our windows — the receiver's own name excepted, since it is one of ours too. The
     * switch is the caller's business, not this method's.
     *
     * <p>The name is matched as a <strong>whole word</strong>, not a substring: a stranger named
     * {@code SuperAlpha} is not our {@code Alpha}, and their trade is a stranger's.
     */
    private Optional<String> internalTradeReceiver(Notification notification) {
        final var title = notification.title();
        if (title == null || title.isBlank() || !WindowManager.isGameTitle(title)) {
            return Optional.empty();
        }

        final var message = notification.message();
        if (message == null || message.isBlank()) {
            return Optional.empty();
        }
        if (EXCHANGE_KEYWORDS.stream().noneMatch(message.toLowerCase(Locale.ROOT)::contains)) {
            return Optional.empty();
        }

        final var receiver = GameWindow.nameIn(title);
        final var askedByOneOfOurs = windows.orderedWindows().stream()
                .filter(window -> !window.name().equalsIgnoreCase(receiver))
                .anyMatch(window -> mentionsAsAWord(message, window.name()));

        return askedByOneOfOurs ? Optional.of(receiver) : Optional.empty();
    }

    /**
     * Whether the message names the character as a word of its own, not as part of a longer name.
     *
     * <p>The boundaries are letters and digits (Unicode, so an SECONDARYed name is matched whole too), so
     * {@code Alpha} is found in {@code Alpha te propose} but not inside {@code SuperAlpha}. The name is
     * quoted, so a character that happened to hold a regex metacharacter would still match literally.
     */
    private static boolean mentionsAsAWord(String message, String name) {
        final var word = Pattern.compile(
                "(?<![\\p{L}\\p{N}])" + Pattern.quote(name) + "(?![\\p{L}\\p{N}])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return word.matcher(message).find();
    }

    /**
     * Accepts the exchange, then gives the screen back.
     *
     * <p>Serialized against the next toast, and holding the foreground across the whole blink: the
     * auto-focus answers the same toast, and left free it would land between the focus and the keypress.
     * The window the player was on is read before any of this and restored after, so the accept is, from
     * the player's seat, a flicker and nothing more.
     */
    private void accept(GameWindow receiver) {
        accepting.lock();
        try (final var _ = focus.takeOver()) {
            final var previous = api.foregroundWindow();

            if (!focus.focus(receiver.hwnd())) {
                log.warn("Could not focus '{}': its trade was not accepted.", receiver.name());
                return;
            }

            input.pressKey(ACCEPT_KEY);
            log.info("Accepted the trade on '{}'.", receiver.name());

            // Hand the screen back: the player never asked to leave the window they were on.
            if (previous != Win32.NULL_HANDLE && previous != receiver.hwnd()) {
                focus.focus(previous);
            }
        } finally {
            accepting.unlock();
        }
    }
}
