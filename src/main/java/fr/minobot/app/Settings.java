package fr.minobot.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * The configuration as it stands right now, and the only thing allowed to change it.
 *
 * <p>{@link Config} stays the immutable record it is: this holds the current one and swaps a whole new
 * one in on every change. A reader therefore never sees a configuration made of one field of the old
 * and one field of the new — which matters, because the readers are the features, and they run on
 * their own virtual threads while the player edits the overlay.
 *
 * <p><strong>This class writes nothing to disk.</strong> It holds the configuration and hands changes
 * to its listeners; persistence, where there is any, is one of those listeners. Some of the overlay's
 * edits — the character order, the keybinds, and each character's class — are persisted to
 * {@code overlay.json} by such a listener wired in {@code MinobotApp} (see {@link OverlayState});
 * everything else the overlay changes, the scale and the two switches, lives only for the session and a
 * restart forgets it.
 *
 * <p>A feature that must react to a change — {@code MinobotApp} re-registering the hotkeys, or saving
 * the overlay state — listens through {@link #onChange}. A feature that merely reads the current value
 * at each use — the cycler's order, the multi-click's exclusions — needs nothing: it calls {@link
 * #get()} and is always current.
 */
public final class Settings {

    private static final Logger log = LoggerFactory.getLogger(Settings.class);

    private final AtomicReference<Config> current;
    private final List<Consumer<Config>> listeners = new CopyOnWriteArrayList<>();

    public Settings(Config initial) {
        this.current = new AtomicReference<>(initial);
    }

    /** The configuration right now. Never null, never half-applied. */
    public Config get() {
        return current.get();
    }

    /**
     * Applies a change and hands the result to the listeners.
     *
     * @param change must be free of side effects: under contention it is called again
     */
    public void update(UnaryOperator<Config> change) {
        final var updated = current.updateAndGet(change);
        log.debug("Configuration changed.");

        for (final var listener : listeners) {
            listener.accept(updated);
        }
    }

    /** Called on whichever thread made the change, right after it took effect. */
    public void onChange(Consumer<Config> listener) {
        listeners.add(listener);
    }
}
