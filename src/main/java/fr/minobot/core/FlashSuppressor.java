package fr.minobot.core;

import fr.minobot.win32.WindowApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the taskbar buttons of the multi-clicked windows from turning orange.
 *
 * <p>Nothing we do lights them up: <em>the game</em> does. A click delivered to a window that does not
 * hold the foreground makes its client ask to be activated; Windows refuses — it refuses
 * {@code SetForegroundWindow} to any process that does not already own the foreground, the very rule
 * {@link FocusManager} works around with its ALT press — and the consolation prize it hands the
 * refused application is a taskbar button that blinks, then stays orange until the player visits it.
 *
 * <p>Those are <em>two</em> states, and it takes one lever each. Both were measured against the game;
 * neither is guesswork, and neither alone is enough:
 *
 * <ul>
 *   <li><b>The blinking</b> cannot be cut short — {@code FlashWindowEx(FLASHW_STOP)} is ignored while
 *   the shell is playing the animation, however fast it is swept. All that can be done is to make the
 *   animation short: Windows exposes how many blinks it plays on a refusal (seven by default), and the
 *   lowest that goes is <strong>one</strong>. <em>Zero is not silence</em> — like {@code uCount} in
 *   {@code FlashWindowEx}, zero means "blink until the window is activated", and setting it makes
 *   things worse. That is a session-wide setting, so {@link #suppress()} borrows it at startup and
 *   {@link #restore()} hands it back.</li>
 *   <li><b>The orange left behind</b> is what {@code FlashWindowEx(FLASHW_STOP)} does clear — but only
 *   once the blinking is over. So {@link #watch(Collection)} sweeps the clicked windows for a few
 *   seconds afterwards, rather than at the moment of the click, where it would land inside the
 *   animation and be ignored.</li>
 * </ul>
 *
 * <p>Neither lever ever touches the focus, which is the whole point of the multi-window click.
 */
public final class FlashSuppressor {

    private static final Logger log = LoggerFactory.getLogger(FlashSuppressor.class);

    /** One blink, the quietest Windows goes. Zero would blink forever — see the class comment. */
    private static final int ONE_BLINK = 1;

    /**
     * How long a clicked window is swept.
     *
     * <p>It has to outlast two things it cannot see: the game's own latency before it asks for the
     * foreground, and the blink Windows plays in answer — during which the sweep is ignored.
     */
    private static final Duration WATCH = Duration.ofSeconds(3);

    /** Frequent enough that the orange goes out the moment the blinking stops being sacred. */
    private static final int SWEEP_MILLIS = 100;

    private final WindowApi api;

    /** The player's own setting, held from {@link #suppress()} until it is given back. */
    private final AtomicReference<OptionalInt> saved = new AtomicReference<>(OptionalInt.empty());

    /** The windows being swept, each until its own deadline (a nanoTime instant). */
    private final Map<Long, Long> watched = new ConcurrentHashMap<>();

    private final AtomicBoolean sweeping = new AtomicBoolean();

    public FlashSuppressor(WindowApi api) {
        this.api = api;
    }

    /** Clears the orange of these windows, once the game has had its blink. */
    public void watch(Collection<Long> hwnds) {
        if (hwnds.isEmpty()) {
            return;
        }

        final var deadline = System.nanoTime() + WATCH.toNanos();
        for (final var hwnd : hwnds) {
            watched.put(hwnd, deadline);
        }

        startSweeping();
    }

    /**
     * One sweeper at a time, on a virtual thread: the click must not wait for any of this. A click
     * landing while a sweep is under way simply pushes back the deadlines it is already watching.
     */
    private void startSweeping() {
        if (sweeping.compareAndSet(false, true)) {
            Thread.ofVirtual().name("flash-suppressor").start(this::sweep);
        }
    }

    private void sweep() {
        try {
            while (dropExpired()) {
                for (final var hwnd : watched.keySet()) {
                    api.stopFlashing(hwnd);
                }
                Thread.sleep(SWEEP_MILLIS);
            }
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            log.error("Unexpected error while clearing the taskbar attention state.", e);
        } finally {
            sweeping.set(false);

            // A click may have landed as we were giving up: it found a sweeper still running, and
            // started none of its own.
            if (!watched.isEmpty()) {
                startSweeping();
            }
        }
    }

    /** @return whether any window is still worth sweeping */
    private boolean dropExpired() {
        final var now = System.nanoTime();
        watched.values().removeIf(deadline -> deadline - now <= 0);
        return !watched.isEmpty();
    }

    /** Cuts the taskbar flashing down to a single blink for this session. */
    public void suppress() {
        final var current = api.foregroundFlashCount();
        if (current.isEmpty()) {
            log.warn("Cannot read the taskbar flash setting; leaving it alone. "
                    + "The game windows will flash when the multi-click reaches them.");
            return;
        }
        if (current.getAsInt() == ONE_BLINK) {
            log.debug("Windows already flashes once; leaving the setting alone.");
            return;
        }

        if (!api.setForegroundFlashCount(ONE_BLINK)) {
            log.warn("Windows refused to change the taskbar flash setting; the game windows will flash.");
            return;
        }

        saved.set(current);
        log.info("Taskbar flashing cut down to a single blink for this session (it was {}).",
                current.getAsInt());
    }

    /** Gives the player their setting back. Idempotent: the tray's Quit and the shutdown hook both land here. */
    public void restore() {
        final var original = saved.getAndSet(OptionalInt.empty());
        if (original.isEmpty()) {
            return; // never suppressed, or already restored
        }

        if (api.setForegroundFlashCount(original.getAsInt())) {
            log.info("Taskbar flashing restored to {}.", original.getAsInt());
        } else {
            log.warn("Could not restore the taskbar flash setting to {}; logging out will.",
                    original.getAsInt());
        }
    }
}
