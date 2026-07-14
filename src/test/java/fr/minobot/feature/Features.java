package fr.minobot.feature;

import fr.minobot.app.Config;
import fr.minobot.app.TestConfigs;
import fr.minobot.core.input.FakeInput;
import fr.minobot.core.FocusManager;
import fr.minobot.core.NotificationManager;
import fr.minobot.core.WindowManager;
import fr.minobot.win32.FakeWindowApi;

import java.nio.file.Path;
import java.util.Map;

/**
 * Wires a feature onto an in-memory desktop, the way {@code MinobotApp} wires it onto the real one.
 *
 * <p>The {@link FocusManager} is the real one: it drives {@link FakeWindowApi} and a {@link FakeInput},
 * so a test observes what a feature actually focused rather than that it merely asked to.
 */
final class Features {

    private final FakeWindowApi api;
    private final Config config;
    private final FakeInput input = new FakeInput();
    private final WindowManager windows;
    private final FocusManager focus;

    /** @param overrides keys as they appear in {@code config.json} */
    Features(FakeWindowApi api, Map<String, Object> overrides) {
        this.api = api;
        this.config = TestConfigs.with(overrides);
        this.windows = new WindowManager(api, config);
        // No keyboard monitor: the smart focus is off by construction, which is what every feature
        // but the notification listener asks for anyway.
        this.focus = new FocusManager(api, config, input, windows, null);
    }

    FakeInput input() {
        return input;
    }

    WindowCycler cycler() {
        return new WindowCycler(api, windows, focus);
    }

    WindowReorder reorder() {
        return new WindowReorder(api, windows, focus);
    }

    MultiWindowClicker clicker() {
        return new MultiWindowClicker(api, windows, focus, config);
    }

    GroupManager groupManager() {
        return new GroupManager(windows, input, focus, notifications());
    }

    NotificationListener notificationListener() {
        return new NotificationListener(config, windows, focus, notifications());
    }

    /** Never started: the tests hand the notifications to the feature themselves. */
    private NotificationManager notifications() {
        return new NotificationManager(config, Path.of("no-such-database.db"));
    }
}
