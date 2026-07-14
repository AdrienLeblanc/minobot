package fr.minobot.core;

import fr.minobot.win32.Point;
import fr.minobot.win32.Win32;
import fr.minobot.win32.WindowApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Polls the keyboard and the mouse buttons, and fires the registered hotkeys — the counterpart of
 * {@code keyboard_monitor.py}.
 *
 * <p>The polling loop runs on a dedicated <em>platform</em> thread, not a virtual one: it is a 50 Hz
 * real-time loop made of native calls, exactly the case where a virtual thread buys nothing and
 * pinning its carrier would be a trap. The callbacks, on the other hand, each get a virtual thread,
 * which is what {@code asyncio.create_task} did — a long {@code invite_all} must not deafen the
 * other hotkeys.
 */
public final class KeyboardMonitor {

    private static final Logger log = LoggerFactory.getLogger(KeyboardMonitor.class);

    private static final Duration POLL_INTERVAL = Duration.ofMillis(20);

    /** The keys whose use means "the user is typing", for {@link FocusManager}'s smart focus. */
    private static final List<Integer> TYPING_KEYS = typingKeys();

    private static final Map<String, Integer> MAIN_KEYS = mainKeys();
    private static final Map<String, Integer> MODIFIER_KEYS = Map.of(
            "CTRL", Win32.VK_CONTROL,
            "SHIFT", Win32.VK_SHIFT,
            "ALT", Win32.VK_MENU);

    private final WindowApi api;
    private final ExecutorService callbackExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /** Virtual key code -> its handlers, most specific (most modifiers) first. */
    private final Map<Integer, List<Hotkey>> hotkeys = new LinkedHashMap<>();
    private final Map<Integer, Boolean> keyStates = new HashMap<>();

    private final AtomicReference<Thread> thread = new AtomicReference<>();

    private volatile boolean monitoring;
    private volatile long lastTypingNanos;
    private volatile boolean everTyped;

    public KeyboardMonitor(WindowApi api) {
        this.api = api;
    }

    /**
     * Registers a hotkey such as {@code "F8"}, {@code "x2"} or {@code "shift+x1"}.
     *
     * <p>An unknown key is logged and ignored rather than fatal, so one bad line in {@code
     * config.json} cannot take the application down.
     */
    public void registerHotkey(String combination, Duration cooldown, Runnable action) {
        register(combination, cooldown, false, cursor -> action.run());
    }

    /** Same, but the action receives the cursor position captured at the moment the key went down. */
    public void registerHotkeyWithCursor(String combination, Duration cooldown, Consumer<Point> action) {
        register(combination, cooldown, true, action);
    }

    private void register(String combination, Duration cooldown, boolean needsCursor, Consumer<Point> action) {
        if (combination == null || combination.isBlank()) {
            return;
        }

        final var parts = combination.split("\\+");
        final var mainKey = parts[parts.length - 1].strip().toUpperCase(Locale.ROOT);

        final var virtualKey = MAIN_KEYS.get(mainKey);
        if (virtualKey == null) {
            log.error("Hotkey main key '{}' is not supported.", mainKey);
            return;
        }

        final var modifiers = new ArrayList<Integer>();
        for (var i = 0; i < parts.length - 1; i++) {
            final var name = parts[i].strip().toUpperCase(Locale.ROOT);
            final var modifier = MODIFIER_KEYS.get(name);
            if (modifier == null) {
                log.warn("Unknown modifier '{}' in '{}'", name, combination);
                continue;
            }
            modifiers.add(modifier);
        }

        final var handlers = hotkeys.computeIfAbsent(virtualKey, key -> new ArrayList<>());
        handlers.add(new Hotkey(combination, List.copyOf(modifiers), cooldown.toNanos(), needsCursor, action));
        // Most specific first: "shift+x2" must win over "x2" when both are registered on X2.
        handlers.sort(Comparator.comparingInt((Hotkey hotkey) -> hotkey.modifiers.size()).reversed());
        keyStates.putIfAbsent(virtualKey, false);

        log.debug("Registered hotkey '{}'.", combination);
    }

    /** Whether the user typed within the given window — the basis of the smart focus. */
    public boolean typedWithin(Duration window) {
        if (!everTyped) {
            return false;
        }
        return System.nanoTime() - lastTypingNanos < window.toNanos();
    }

    /** Starts the polling loop on its own daemon thread and returns immediately. */
    public void start() {
        if (monitoring) {
            log.warn("Keyboard monitor is already running.");
            return;
        }
        monitoring = true;
        thread.set(Thread.ofPlatform()
                .name("keyboard-monitor")
                .daemon(true)
                .start(this::poll));
        log.info("Keyboard/Mouse monitor started.");
    }

    public void stop() {
        if (!monitoring) {
            return;
        }
        monitoring = false;

        final var current = thread.getAndSet(null);
        if (current != null) {
            current.interrupt();
        }
        callbackExecutor.shutdownNow();
        log.info("Keyboard monitor stopped.");
    }

    private void poll() {
        while (monitoring) {
            try {
                long now = System.nanoTime();
                detectTyping(now);
                fireHotkeys(now);

                Thread.sleep(POLL_INTERVAL);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                // A stray failure must not kill the loop: every hotkey would go silent for good.
                log.error("Error in the keyboard polling loop.", e);
            }
        }
    }

    private void detectTyping(long now) {
        for (final var key : TYPING_KEYS) {
            if (api.isKeyDown(key)) {
                lastTypingNanos = now;
                everTyped = true;
                return;
            }
        }
    }

    private void fireHotkeys(long now) {
        for (final var entry : hotkeys.entrySet()) {
            final var virtualKey = entry.getKey();

            final var isDown = api.isKeyDown(virtualKey);
            final var wasDown = keyStates.getOrDefault(virtualKey, false);
            keyStates.put(virtualKey, isDown);

            if (!isDown || wasDown) {
                continue; // hotkeys fire on the edge, not while the key is held
            }

            for (final var hotkey : entry.getValue()) {
                if (!modifiersDown(hotkey.modifiers)) {
                    continue;
                }
                // The first handler whose modifiers match owns this key press, cooldown or not.
                if (hotkey.isReady(now)) {
                    hotkey.markTriggered(now);
                    trigger(hotkey);
                }
                break;
            }
        }
    }

    private boolean modifiersDown(List<Integer> modifiers) {
        return modifiers.stream().allMatch(api::isKeyDown);
    }

    private void trigger(Hotkey hotkey) {
        final var cursor = hotkey.needsCursor ? api.cursorPosition().orElse(null) : null;
        if (hotkey.needsCursor && cursor == null) {
            log.warn("Cannot read the cursor position; '{}' skipped.", hotkey.combination);
            return;
        }

        callbackExecutor.execute(() -> {
            try {
                hotkey.action.accept(cursor);
            } catch (Exception e) {
                log.error("Error in the '{}' hotkey callback.", hotkey.combination, e);
            }
        });
    }

    /** One registered hotkey. Its trigger time is only ever touched by the polling thread. */
    private static final class Hotkey {
        private final String combination;
        private final List<Integer> modifiers;
        private final long cooldownNanos;
        private final boolean needsCursor;
        private final Consumer<Point> action;

        private long lastTriggerNanos;
        private boolean everTriggered;

        Hotkey(String combination, List<Integer> modifiers, long cooldownNanos,
               boolean needsCursor, Consumer<Point> action) {
            this.combination = combination;
            this.modifiers = modifiers;
            this.cooldownNanos = cooldownNanos;
            this.needsCursor = needsCursor;
            this.action = action;
        }

        boolean isReady(long now) {
            return !everTriggered || now - lastTriggerNanos > cooldownNanos;
        }

        void markTriggered(long now) {
            lastTriggerNanos = now;
            everTriggered = true;
        }
    }

    private static Map<String, Integer> mainKeys() {
        final var keys = new LinkedHashMap<String, Integer>();
        for (var i = 1; i <= 12; i++) {
            keys.put("F" + i, Win32.functionKey(i));
        }
        keys.put("LEFT", Win32.VK_LBUTTON);
        keys.put("RIGHT", Win32.VK_RBUTTON);
        keys.put("MIDDLE", Win32.VK_MBUTTON);
        keys.put("X1", Win32.VK_XBUTTON1);
        keys.put("X2", Win32.VK_XBUTTON2);
        return Map.copyOf(keys);
    }

    private static List<Integer> typingKeys() {
        final var keys = new ArrayList<Integer>();
        for (var key = Win32.VK_0; key <= Win32.VK_9; key++) {
            keys.add(key);
        }
        for (var key = Win32.VK_A; key <= Win32.VK_Z; key++) {
            keys.add(key);
        }
        keys.add(Win32.VK_SPACE);
        keys.add(Win32.VK_RETURN);
        keys.add(Win32.VK_BACK);
        keys.add(Win32.VK_TAB);
        return List.copyOf(keys);
    }
}
