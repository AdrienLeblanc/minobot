package fr.minobot.core;

import fr.minobot.app.Config;
import fr.minobot.core.domain.GameWindow;
import fr.minobot.win32.Win32;
import fr.minobot.win32.WindowApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Detects and tracks the game windows — the counterpart of {@code window_manager.py}.
 *
 * <p>Everything it knows about Windows comes through {@link WindowApi}, so the ordering, the
 * monitor filtering and the character-name extraction are all testable on any OS.
 */
public final class WindowManager {

    private static final Logger log = LoggerFactory.getLogger(WindowManager.class);

    /** How far past the configured order an unlisted window is pushed. */
    private static final int UNRANKED_OFFSET = 1000;

    private final WindowApi api;
    private final Config config;

    /**
     * The last refresh, or {@code null} until the first one.
     *
     * <p>The window list and the time it was taken are one indivisible fact: held apart, a reader
     * could pair a fresh list with an old timestamp and refresh for nothing — or worse, the other way
     * round.
     */
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>();

    public WindowManager(WindowApi api, Config config) {
        this.api = api;
        this.config = config;
    }

    /** Re-enumerates the desktop and keeps the windows whose title carries a game keyword. */
    public synchronized void refresh() {
        final var found = new ArrayList<GameWindow>();
        for (final var hwnd : api.topLevelWindows()) {
            if (!api.isWindowVisible(hwnd)) {
                continue;
            }
            final var title = api.windowText(hwnd);
            if (isGameWindow(title)) {
                found.add(new GameWindow(hwnd, title));
            }
        }

        final var fresh = List.copyOf(found);
        snapshot.set(new Snapshot(fresh, System.nanoTime()));

        log.debug("Detected {} game window(s).", fresh.size());
        for (final var window : fresh) {
            log.debug("  -> Found: '{}' (HWND: {})", window.title(), window.hwnd());
        }
    }

    private boolean isGameWindow(String title) {
        return config.gameKeywords().stream().anyMatch(title::contains);
    }

    /** Refreshes only if the list has gone stale, per {@code window_refresh_interval}. */
    public void ensureFresh() {
        final var last = snapshot.get();
        if (last == null) {
            refresh();
            return;
        }
        if (System.nanoTime() - last.takenNanos() > config.windowRefreshInterval().toNanos()) {
            log.info("Window list is stale, refreshing...");
            refresh();
        }
    }

    /** The windows detected at the last refresh, in enumeration order. */
    public List<GameWindow> windows() {
        final var last = snapshot.get();
        return last == null ? List.of() : last.windows();
    }

    /**
     * The character name a title carries, cut at the first configured separator.
     *
     * <p>{@code "Bravo - Dofus Retro v1.48.18"} yields {@code "Bravo"}.
     */
    public String extractCharacterName(String title) {
        for (final var separator : config.characterSeparators()) {
            final var index = title.indexOf(separator);
            if (index >= 0) {
                return title.substring(0, index).strip();
            }
        }
        return title.strip();
    }

    /** Finds a character's window: first by exact name, then by a substring of the whole title. */
    public Optional<GameWindow> findWindow(String characterName) {
        ensureFresh();
        final var wanted = characterName.toLowerCase(Locale.ROOT);
        final var candidates = windows();

        for (final var window : candidates) {
            if (extractCharacterName(window.title()).toLowerCase(Locale.ROOT).equals(wanted)) {
                log.debug("Found exact match for '{}': '{}'", characterName, window.title());
                return Optional.of(window);
            }
        }

        for (final var window : candidates) {
            if (window.title().toLowerCase(Locale.ROOT).contains(wanted)) {
                log.debug("Found partial match for '{}': '{}'", characterName, window.title());
                return Optional.of(window);
            }
        }

        log.warn("Could not find any window for character '{}'.", characterName);
        return Optional.empty();
    }

    public List<GameWindow> orderedWindows() {
        return orderedWindows(false);
    }

    /**
     * The game windows in the order of {@code window_cycle_order}, unlisted ones last.
     *
     * <p>Sorted alphabetically first, then stably by rank: windows sharing a rank — and all the
     * unlisted ones do — stay in alphabetical order instead of in the arbitrary order Windows
     * enumerated them in. Reversing flips the ranks only, so the alphabetical tie-break holds.
     */
    public List<GameWindow> orderedWindows(boolean reverse) {
        ensureFresh();

        final var ordered = new ArrayList<>(windows());
        ordered.sort(Comparator.comparing(GameWindow::title));

        final var byConfiguredRank = Comparator.comparingInt(this::rank);
        ordered.sort(reverse ? byConfiguredRank.reversed() : byConfiguredRank);

        return List.copyOf(ordered);
    }

    private int rank(GameWindow window) {
        final var cycleOrder = config.windowCycleOrder();
        final var title = window.title().toLowerCase(Locale.ROOT);

        for (var i = 0; i < cycleOrder.size(); i++) {
            if (title.contains(cycleOrder.get(i).toLowerCase(Locale.ROOT))) {
                return i;
            }
        }
        return cycleOrder.size() + UNRANKED_OFFSET;
    }

    /** The ordered windows that are not minimized. */
    public List<GameWindow> activeOrderedWindows() {
        return orderedWindows().stream()
                .filter(window -> !api.isIconic(window.hwnd()))
                .toList();
    }

    /** Two windows sit on the same screen exactly when this returns the same handle for both. */
    public long monitorOf(long hwnd) {
        return api.monitorFromWindow(hwnd);
    }

    /** The active windows sharing a monitor with whatever currently holds the focus. */
    public List<GameWindow> windowsOnCurrentMonitor() {
        final var foreground = api.foregroundWindow();
        if (foreground == Win32.NULL_HANDLE) {
            return activeOrderedWindows();
        }

        final var currentMonitor = monitorOf(foreground);
        final var sameMonitor = activeOrderedWindows().stream()
                .filter(window -> monitorOf(window.hwnd()) == currentMonitor)
                .toList();

        if (sameMonitor.isEmpty()) {
            log.debug("No game windows found on the current monitor.");
        }
        return sameMonitor;
    }

    /** One refresh: the windows it found, and when. {@code windows} is always an immutable list. */
    private record Snapshot(List<GameWindow> windows, long takenNanos) {
    }
}
