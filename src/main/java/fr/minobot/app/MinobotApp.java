package fr.minobot.app;

import fr.minobot.core.FocusManager;
import fr.minobot.core.input.InputSimulator;
import fr.minobot.core.KeyboardMonitor;
import fr.minobot.core.NotificationManager;
import fr.minobot.core.SystemTrayManager;
import fr.minobot.core.WindowManager;
import fr.minobot.feature.GroupManager;
import fr.minobot.feature.MultiWindowClicker;
import fr.minobot.feature.NotificationListener;
import fr.minobot.feature.WindowCycler;
import fr.minobot.feature.WindowReorder;
import fr.minobot.win32.Point;
import fr.minobot.win32.User32;
import fr.minobot.win32.WindowApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Orchestrates the components and the features — the counterpart of {@code app.py}.
 *
 * <p>The features hold no state of their own beyond a running flag: they are wired here, onto the
 * hotkeys of the {@link KeyboardMonitor} and the callbacks of the {@link NotificationManager}, and
 * everything they touch goes through the core.
 */
public final class MinobotApp {

    private static final Logger log = LoggerFactory.getLogger(MinobotApp.class);

    private static final Duration RESET_WINDOWS_COOLDOWN = Duration.ofSeconds(1);
    private static final Duration GROUP_INVITE_COOLDOWN = Duration.ofSeconds(5);
    private static final Duration WINDOW_CYCLE_COOLDOWN = Duration.ofMillis(100);
    private static final Duration WINDOW_REORDER_COOLDOWN = Duration.ofSeconds(5);

    private final Config config;

    private final SystemTrayManager systemTray;
    private final WindowManager windowManager;
    private final InputSimulator inputSimulator;
    private final KeyboardMonitor keyboardMonitor;
    private final FocusManager focusManager;
    private final NotificationManager notificationManager;

    private final MultiWindowClicker multiClicker;
    private final WindowCycler windowCycler;
    private final WindowReorder windowReorder;
    private final GroupManager groupManager;

    private final CountDownLatch stopped = new CountDownLatch(1);
    private final AtomicBoolean stopping = new AtomicBoolean();

    public MinobotApp() {
        this(User32.instance());
    }

    public MinobotApp(WindowApi api) {
        final var baseDirectory = ConfigLoader.baseDirectory();
        final var configPath = baseDirectory.resolve("config.json");

        this.config = ConfigLoader.load(configPath);
        LoggerSetup.configure(config, baseDirectory);

        log.info("=== Minobot starting (app dir: {}) ===", baseDirectory);
        log.info("Using config: {}", configPath);

        this.systemTray = new SystemTrayManager(this::stop);
        this.windowManager = new WindowManager(api, config);
        this.inputSimulator = new InputSimulator();
        this.keyboardMonitor = new KeyboardMonitor(api);
        this.focusManager = new FocusManager(api, config, inputSimulator, windowManager, keyboardMonitor);
        this.notificationManager = new NotificationManager(config);

        this.multiClicker = new MultiWindowClicker(api, windowManager, focusManager, config);
        this.windowCycler = new WindowCycler(api, windowManager, focusManager);
        this.windowReorder = new WindowReorder(api, windowManager, focusManager);
        this.groupManager = new GroupManager(windowManager, inputSimulator, focusManager, notificationManager);

        // Registers itself with the notification manager; it has no hotkey of its own.
        new NotificationListener(config, windowManager, focusManager, notificationManager);

        registerHotkeys();
    }

    private void registerHotkeys() {
        if (config.multiclickEnabled()) {
            onHotkeyWithCursor("Multi-window click", config.multiclickHotkey(),
                    config.multiclickCooldown(), multiClicker::clickAllWindows);
            onHotkey("Reset windows state", config.resetWindowsHotkey(),
                    RESET_WINDOWS_COOLDOWN, multiClicker::resetWindowsAttentionState);
        }
        if (config.groupInviteEnabled()) {
            onHotkey("Group invitation", config.groupInviteHotkey(),
                    GROUP_INVITE_COOLDOWN, groupManager::inviteAll);
        }
        if (config.windowCycleEnabled()) {
            onHotkey("Window cycler (next)", config.windowCycleNextHotkey(),
                    WINDOW_CYCLE_COOLDOWN, windowCycler::cycleNext);
            onHotkey("Window cycler (previous)", config.windowCyclePrevHotkey(),
                    WINDOW_CYCLE_COOLDOWN, windowCycler::cyclePrev);
        }
        if (config.windowReorderEnabled()) {
            onHotkey("Window reorder", config.windowReorderHotkey(),
                    WINDOW_REORDER_COOLDOWN, windowReorder::reorderTaskbar);
        }
    }

    /** An empty hotkey in the config disables the feature without disabling the others. */
    private void onHotkey(String feature, String hotkey, Duration cooldown, Runnable action) {
        if (hotkey == null || hotkey.isBlank()) {
            return;
        }
        keyboardMonitor.registerHotkey(hotkey, cooldown, action);
        log.info("Feature '{}' enabled on hotkey '{}'.", feature, hotkey);
    }

    private void onHotkeyWithCursor(String feature, String hotkey, Duration cooldown, Consumer<Point> action) {
        if (hotkey == null || hotkey.isBlank()) {
            return;
        }
        keyboardMonitor.registerHotkeyWithCursor(hotkey, cooldown, action);
        log.info("Feature '{}' enabled on hotkey '{}'.", feature, hotkey);
    }

    public Config config() {
        return config;
    }

    public WindowManager windowManager() {
        return windowManager;
    }

    public FocusManager focusManager() {
        return focusManager;
    }

    public InputSimulator inputSimulator() {
        return inputSimulator;
    }

    public KeyboardMonitor keyboardMonitor() {
        return keyboardMonitor;
    }

    public NotificationManager notificationManager() {
        return notificationManager;
    }

    /** Starts every service, then blocks until {@link #stop()} is called. */
    public void run() throws InterruptedException {
        systemTray.start();
        windowManager.refresh();

        notificationManager.start();
        keyboardMonitor.start();

        log.info("=== Application services ready ===");

        stopped.await();
    }

    /** Idempotent: the tray's Quit item and the JVM shutdown hook both land here. */
    public void stop() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }

        log.info("=== Minobot stopping ===");
        keyboardMonitor.stop();
        notificationManager.stop();
        systemTray.stop();

        stopped.countDown();
    }
}
