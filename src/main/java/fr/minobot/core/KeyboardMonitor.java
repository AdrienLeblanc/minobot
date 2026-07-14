package fr.minobot.core;

import fr.minobot.win32.Point;
import fr.minobot.win32.Win32;
import fr.minobot.win32.WindowApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

    /** Ordered, so a captured combination always reads "ctrl+shift+alt+F7" and never some other way. */
    private static final Map<String, Integer> MODIFIER_KEYS = modifierKeys();

    private final WindowApi api;
    private final ExecutorService callbackExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /** What has been asked for, in the order it was asked. Guarded by {@code this}. */
    private final List<Binding> bindings = new ArrayList<>();

    /**
     * Virtual key code -> its handlers, most specific (most modifiers) first.
     *
     * <p><strong>Replaced whole, never mutated.</strong> The polling thread reads this fifty times a
     * second while the overlay may rebind a key at any moment; a map edited under it is a map it can
     * see half-built. {@link #rebuild()} assembles the next one aside and swaps it in — which is safe
     * only because what it swaps in is immutable, hence the {@code Map.copyOf} rather than a bare
     * {@code volatile} on a map anyone could still be filling.
     */
    private final AtomicReference<Map<Integer, List<Hotkey>>> table = new AtomicReference<>(Map.of());

    /** Written by the polling thread, and reseeded by {@link #rebuild()} — hence the concurrent map. */
    private final Map<Integer, Boolean> keyStates = new ConcurrentHashMap<>();

    private final AtomicReference<Thread> thread = new AtomicReference<>();

    private volatile boolean monitoring;
    private volatile long lastTypingNanos;
    private volatile boolean everTyped;

    /** While a key is being named through {@link #captureNext}, no hotkey fires. */
    private volatile boolean capturing;

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
        add(Binding.of(combination, cooldown, action));
    }

    /** Same, but the action receives the cursor position captured at the moment the key went down. */
    public void registerHotkeyWithCursor(String combination, Duration cooldown, Consumer<Point> action) {
        add(Binding.withCursor(combination, cooldown, action));
    }

    /**
     * Replaces every hotkey at once — how a rebind from the overlay lands, while the loop is running.
     *
     * <p>One swap, so no press can fall between the old set being dropped and the new one being in
     * place. The cooldowns start again from zero, which costs nothing: the player has just let go of a
     * mouse button in a dialog, not fired a feature.
     */
    public synchronized void rebind(List<Binding> newBindings) {
        bindings.clear();
        bindings.addAll(newBindings);
        rebuild();
    }

    private synchronized void add(Binding binding) {
        bindings.add(binding);
        rebuild();
    }

    /** Assembles the next table and swaps it in. Called under the lock, never by the polling thread. */
    private void rebuild() {
        final var rebuilt = new LinkedHashMap<Integer, List<Hotkey>>();
        for (final var binding : bindings) {
            parse(binding).ifPresent(hotkey ->
                    rebuilt.computeIfAbsent(hotkey.virtualKey, key -> new ArrayList<>()).add(hotkey));
        }

        final var next = new HashMap<Integer, List<Hotkey>>();
        rebuilt.forEach((virtualKey, handlers) -> {
            // Most specific first: "shift+x2" must win over "x2" when both are registered on X2.
            handlers.sort(Comparator.comparingInt((Hotkey hotkey) -> hotkey.modifiers.size()).reversed());
            next.put(virtualKey, List.copyOf(handlers));
        });

        seedKeyStates(next.keySet());
        table.set(Map.copyOf(next));
    }

    /**
     * Takes each bound key's state from the keyboard as it <em>is</em>, so that a key already held is
     * not mistaken for a fresh press.
     *
     * <p>Both moments that need this are the same moment, seen twice: the player has just pressed a key
     * to <em>name</em> it, and it is still down. Without the seeding, the loop's next tick sees a key
     * that was up and is now down — an edge — and fires the very feature they were only naming.
     */
    private void seedKeyStates(Set<Integer> virtualKeys) {
        virtualKeys.forEach(virtualKey -> keyStates.put(virtualKey, api.isKeyDown(virtualKey)));
    }

    /** Empty when the combination is blank (the feature is off) or names a key we cannot poll. */
    private Optional<Hotkey> parse(Binding binding) {
        final var combination = binding.combination();
        if (combination == null || combination.isBlank()) {
            return Optional.empty();
        }

        final var parts = combination.split("\\+");
        final var mainKey = parts[parts.length - 1].strip().toUpperCase(Locale.ROOT);

        final var virtualKey = MAIN_KEYS.get(mainKey);
        if (virtualKey == null) {
            log.error("Hotkey main key '{}' is not supported.", mainKey);
            return Optional.empty();
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

        log.debug("Registered hotkey '{}'.", combination);
        return Optional.of(new Hotkey(combination, virtualKey, List.copyOf(modifiers),
                binding.cooldown().toNanos(), binding.needsCursor(), binding.action()));
    }

    /**
     * One hotkey as the player expresses it: a combination, and what it does. It is parsed at every
     * rebuild rather than held parsed, so a rebind is one list to swap rather than a table to patch.
     */
    public record Binding(String combination, Duration cooldown, boolean needsCursor, Consumer<Point> action) {

        public static Binding of(String combination, Duration cooldown, Runnable action) {
            return new Binding(combination, cooldown, false, _ -> action.run());
        }

        /** The action receives the cursor position captured at the moment the key went down. */
        public static Binding withCursor(String combination, Duration cooldown, Consumer<Point> action) {
            return new Binding(combination, cooldown, true, action);
        }
    }

    /**
     * Waits for the player to press a bindable key, and reads it back — {@code "shift+F7"}.
     *
     * <p>This is how the overlay asks "which key do you want?", and it is why the panel never needs the
     * keyboard focus: the key is polled from the keyboard itself, not typed into a text field. The
     * player can be looking at their game while they press it.
     *
     * <p>The registered hotkeys are <strong>silenced</strong> for the duration. A key being named is
     * not a key being used, and pressing F9 to bind it must not also rebuild the taskbar.
     *
     * <p>Only the keys a hotkey can be built from are captured, so a combination that comes out of here
     * is one that {@link #rebind} can always take back.
     *
     * @return the combination, or empty if nothing was pressed before the timeout ran out
     */
    public Optional<String> captureNext(Duration timeout) {
        capturing = true;
        try {
            // A key already down when the capture opens was pressed for something else — the click on the
            // "change" button may still be travelling. It only counts once it has been let go and pressed.
            final var stale = new HashSet<Integer>();
            MAIN_KEYS.values().stream().filter(api::isKeyDown).forEach(stale::add);

            final var deadline = System.nanoTime() + timeout.toNanos();
            while (System.nanoTime() < deadline) {
                final var pressed = firstKeyDown(stale);
                if (pressed.isPresent()) {
                    return pressed.map(this::combinationOf);
                }
                Thread.sleep(POLL_INTERVAL);
            }

            log.debug("Nothing was pressed within {}: the capture is off.", timeout);
            return Optional.empty();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            // The key that closed the capture is still held. Take every bound key's state before the
            // hotkeys come back, or the polling loop's next tick reads that held key as a press and
            // fires whatever it is bound to — the taskbar rebuilt because the player named F9.
            seedKeyStates(table.get().keySet());
            capturing = false;
        }
    }

    /** The first bindable key that has gone down since the capture opened. Empty while none has. */
    private Optional<String> firstKeyDown(Set<Integer> stale) {
        for (final var key : MAIN_KEYS.entrySet()) {
            if (!api.isKeyDown(key.getValue())) {
                stale.remove(key.getValue()); // let go: from now on, pressing it counts
                continue;
            }
            if (!stale.contains(key.getValue())) {
                return Optional.of(key.getKey());
            }
        }
        return Optional.empty();
    }

    /** The main key, prefixed by whichever modifiers are held at this very moment. */
    private String combinationOf(String mainKey) {
        final var combination = new StringBuilder();
        MODIFIER_KEYS.forEach((name, virtualKey) -> {
            if (api.isKeyDown(virtualKey)) {
                combination.append(name.toLowerCase(Locale.ROOT)).append('+');
            }
        });
        return combination.append(mainKey).toString();
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
        // One read: a rebind landing mid-sweep must not be seen by half of it.
        for (final var entry : table.get().entrySet()) {
            final var virtualKey = entry.getKey();

            final var isDown = api.isKeyDown(virtualKey);
            final var wasDown = keyStates.getOrDefault(virtualKey, false);
            keyStates.put(virtualKey, isDown);

            if (!isDown || wasDown) {
                continue; // hotkeys fire on the edge, not while the key is held
            }

            // The key states above are still tracked while a key is being named: a hotkey held through
            // the whole capture must not look like a fresh press the moment the capture closes.
            if (capturing) {
                continue; // the player is naming a key, not using one
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

    /** One parsed hotkey. Its trigger time is only ever touched by the polling thread. */
    private static final class Hotkey {
        private final String combination;
        private final int virtualKey;
        private final List<Integer> modifiers;
        private final long cooldownNanos;
        private final boolean needsCursor;
        private final Consumer<Point> action;

        private long lastTriggerNanos;
        private boolean everTriggered;

        Hotkey(String combination, int virtualKey, List<Integer> modifiers, long cooldownNanos,
               boolean needsCursor, Consumer<Point> action) {
            this.combination = combination;
            this.virtualKey = virtualKey;
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

    private static Map<String, Integer> modifierKeys() {
        final var keys = new LinkedHashMap<String, Integer>();
        keys.put("CTRL", Win32.VK_CONTROL);
        keys.put("SHIFT", Win32.VK_SHIFT);
        keys.put("ALT", Win32.VK_MENU);
        return Collections.unmodifiableMap(keys);
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
