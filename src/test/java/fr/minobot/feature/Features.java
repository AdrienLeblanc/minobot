package fr.minobot.feature;

import fr.minobot.app.Settings;
import fr.minobot.app.TestConfigs;
import fr.minobot.core.FlashSuppressor;
import fr.minobot.core.FocusManager;
import fr.minobot.core.NotificationManager;
import fr.minobot.core.WindowManager;
import fr.minobot.core.input.FakeInput;
import fr.minobot.ui.FakeToastView;
import fr.minobot.win32.FakeWindowApi;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.function.Predicate;

import fr.minobot.core.domain.Notification;

/**
 * Wires a feature onto an in-memory desktop, the way {@code MinobotApp} wires it onto the real one.
 *
 * <p>The {@link FocusManager} is the real one: it drives {@link FakeWindowApi} and a {@link FakeInput},
 * so a test observes what a feature actually focused rather than that it merely asked to.
 *
 * <p>The features share one {@link NotificationManager}, as they do in production — the group
 * invitation and the notification auto-focus both listen to it, and they compete for the foreground.
 */
final class Features {

    private final FakeWindowApi api;
    private final Settings settings;
    private final FakeInput input;
    private final WindowManager windows;
    private final FocusManager focus;

    /** Never started: the tests hand the notifications to the features themselves. */
    private final NotificationManager notifications = new NotificationManager(Path.of("no-such-database.db"));

    /** @param overrides keys as they appear in {@code config.json} */
    Features(FakeWindowApi api, Map<String, Object> overrides) {
        this.api = api;
        this.settings = TestConfigs.settings(overrides);
        this.input = new FakeInput(api::foregroundWindow);
        this.windows = new WindowManager(api, settings);
        this.focus = new FocusManager(api, input);
    }

    FakeInput input() {
        return input;
    }

    /** The live configuration the features read — a test changes it to change it under them. */
    Settings settings() {
        return settings;
    }

    WindowCycler cycler() {
        return new WindowCycler(api, windows, focus);
    }

    WindowReorder reorder() {
        return new WindowReorder(api, windows, focus);
    }

    MultiWindowClicker clicker() {
        return new MultiWindowClicker(api, windows, focus, new FlashSuppressor(api), settings);
    }

    GroupManager groupManager() {
        return new GroupManager(windows, input, focus, notifications);
    }

    NotificationListener notificationListener() {
        return new NotificationListener(windows, focus, notifications);
    }

    /** The auto-focus wired to stand aside for the toasts the trade-accepter answers in place. */
    NotificationListener notificationListener(ExchangeAccepter accepter) {
        return new NotificationListener(windows, focus, notifications, accepter::claims);
    }

    /** The auto-focus wired to stand aside for whatever the given predicate claims. */
    NotificationListener notificationListener(Predicate<Notification> handledSilently) {
        return new NotificationListener(windows, focus, notifications, handledSilently);
    }

    WhisperToaster whisperToaster() {
        return new WhisperToaster(api, windows, focus, settings, notifications, FakeToastView::new);
    }

    WhisperToaster whisperToaster(Duration lifetime) {
        return new WhisperToaster(api, windows, focus, settings, notifications, lifetime, FakeToastView::new);
    }

    TurnPasser turnPasser() {
        return new TurnPasser(windows, input, focus, notifications, settings);
    }

    ExchangeAccepter exchangeAccepter() {
        return new ExchangeAccepter(api, windows, input, focus, notifications, settings);
    }
}
