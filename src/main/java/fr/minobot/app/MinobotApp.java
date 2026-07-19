package fr.minobot.app;

import fr.minobot.core.*;
import fr.minobot.core.KeyboardMonitor.Binding;
import fr.minobot.core.input.InputSimulator;
import fr.minobot.feature.*;
import fr.minobot.ui.SwingOverlay;
import fr.minobot.win32.User32;
import fr.minobot.win32.WindowApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Orchestrates the components and the features — the counterpart of {@code app.py}.
 *
 * <p>The features hold no state of their own beyond a running flag: they are wired here, onto the
 * hotkeys of the {@link KeyboardMonitor} and the callbacks of the {@link NotificationManager}, and
 * everything they touch goes through the core.
 */
public final class MinobotApp {

    private static final Logger log = LoggerFactory.getLogger(MinobotApp.class);

    private final Settings settings;

    private final SystemTrayManager systemTray;
    private final WindowManager windowManager;
    private final InputSimulator inputSimulator;
    private final KeyboardMonitor keyboardMonitor;
    private final FocusManager focusManager;
    private final NotificationManager notificationManager;
    private final FlashSuppressor flashSuppressor;

    private final MultiWindowClicker multiClicker;
    private final WindowCycler windowCycler;
    private final WindowReorder windowReorder;
    private final GroupManager groupManager;
    private final OverlayController overlay;

    private final CountDownLatch stopped = new CountDownLatch(1);
    private final AtomicBoolean stopping = new AtomicBoolean();

    public MinobotApp() {
        this(User32.instance());
    }

    public MinobotApp(WindowApi api) {
        final var baseDirectory = ConfigLoader.baseDirectory();
        final var configPath = baseDirectory.resolve("config.json");

        this.settings = new Settings(ConfigLoader.load(configPath));
        LoggerSetup.configure(settings.get(), baseDirectory);

        log.info("=== Minobot starting (app dir: {}) ===", baseDirectory);
        log.info("Using config: {}", configPath);

        this.systemTray = new SystemTrayManager(this::stop);
        this.windowManager = new WindowManager(api, settings);
        this.inputSimulator = new InputSimulator();
        this.keyboardMonitor = new KeyboardMonitor(api);
        this.focusManager = new FocusManager(api, inputSimulator);
        this.notificationManager = new NotificationManager();
        this.flashSuppressor = new FlashSuppressor(api);

        this.multiClicker = new MultiWindowClicker(api, windowManager, focusManager, flashSuppressor, settings);
        this.windowCycler = new WindowCycler(api, windowManager, focusManager);
        this.windowReorder = new WindowReorder(api, windowManager, focusManager);
        this.groupManager = new GroupManager(windowManager, inputSimulator, focusManager, notificationManager);
        this.overlay = new OverlayController(api, windowManager, settings, keyboardMonitor, SwingOverlay::new);

        // Answers a trade between two of the player's own characters in place. Built before the
        // auto-focus so the auto-focus can consult it and stand aside for the toasts it takes.
        final var exchangeAccepter = new ExchangeAccepter(
                api, windowManager, inputSimulator, focusManager, notificationManager, settings);

        // Registers itself with the notification manager; it has no hotkey of its own. It stands aside
        // for a toast the trade-accepter answers silently, and takes the player everywhere else.
        new NotificationListener(windowManager, focusManager, notificationManager, exchangeAccepter::claims);

        // Also hotkey-less: the overlay's Auto-pass switch turns it on and off, and it reads that
        // switch at every toast rather than being rebound.
        new TurnPasser(windowManager, inputSimulator, focusManager, notificationManager, settings);

        // Every change goes through the whole set again: it is one swap in the monitor, and it spares
        // us the question of which field the overlay touched.
        settings.onChange(this::bindHotkeys);
        bindHotkeys(settings.get());
    }

    /**
     * Binds every feature to the key it currently has. A blank hotkey leaves the feature unbound, which
     * is how a player turns it off — and how the overlay will.
     */
    private void bindHotkeys(Config config) {
        final var bindings = new ArrayList<Binding>();

        for (final var feature : Feature.values()) {
            final var hotkey = feature.hotkeyIn(config);
            if (hotkey.isBlank()) {
                log.info("Feature '{}' is disabled: no hotkey.", feature.label());
                continue;
            }

            final var cooldown = feature.cooldown();
            bindings.add(switch (feature) {
                case MULTICLICK ->
                        Binding.withCursor(hotkey, cooldown, multiClicker::clickEveryCharacter);
                case RESET_WINDOWS -> Binding.of(hotkey, cooldown, multiClicker::resetCharacters);
                case GROUP_INVITE -> Binding.of(hotkey, cooldown, groupManager::inviteAll);
                case WINDOW_CYCLE_NEXT -> Binding.of(hotkey, cooldown, windowCycler::cycleNext);
                case WINDOW_CYCLE_PREV -> Binding.of(hotkey, cooldown, windowCycler::cyclePrev);
                case WINDOW_REORDER -> Binding.of(hotkey, cooldown, windowReorder::reorderTaskbar);
                case OVERLAY -> Binding.of(hotkey, cooldown, overlay::toggle);
            });
            log.debug("Feature '{}' enabled on hotkey '{}'.", feature.label(), hotkey);
        }

        keyboardMonitor.rebind(bindings);
    }

    /** The live configuration: what the overlay reads, and the only thing allowed to change it. */
    public Settings settings() {
        return settings;
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

        // A session-wide setting, and the only thing that keeps the multi-clicked windows from
        // flashing in the taskbar. Given back in stop().
        flashSuppressor.suppress();

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
        flashSuppressor.restore();
        systemTray.stop();

        stopped.countDown();
    }
}
