package fr.minobot.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The one part of the overlay's edits that outlives the process: the character order and the seven
 * keybinds, kept in {@code overlay.json} next to {@code config.json}.
 *
 * <p>There are two tiers now. {@code config.json} is the player's <strong>read-only defaults</strong> —
 * generated once, edited by hand, never rewritten by the application. {@code overlay.json} is what the
 * overlay <em>persisted</em>, and it overrides {@code config.json} at load. It holds <em>only</em> the
 * persisted subset, so a default the player never touched still shows through from {@code config.json}.
 *
 * <p>Everything else the overlay changes — the scale, the two switches — stays session-only, on purpose:
 * an <em>Auto-pass</em> that came back on by itself after a restart would end combat turns nobody asked
 * it to. Those keep dying with the process; see {@link Settings}.
 */
public final class OverlayState {

    private static final Logger log = LoggerFactory.getLogger(OverlayState.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** The order's key in {@code config.json} — the same string as {@link Config}'s {@code @JsonProperty}. */
    private static final String WINDOW_CYCLE_ORDER = "window_cycle_order";

    /**
     * The keys {@code overlay.json} carries: the character order, and every feature's hotkey. The
     * hotkeys are <em>walked</em> from {@link Feature}, not listed here by hand — so a feature added to
     * the enum persists its keybind for free, and can never be silently left out.
     */
    private static final Set<String> PERSISTED_KEYS = persistedKeys();

    /** Serializes the writes: {@code Settings.onChange} listeners can fire on concurrent threads. */
    private static final Object WRITE_LOCK = new Object();

    private OverlayState() {
    }

    private static Set<String> persistedKeys() {
        final var keys = new LinkedHashSet<String>();
        keys.add(WINDOW_CYCLE_ORDER);
        for (final var feature : Feature.values()) {
            keys.add(feature.configKey());
        }
        return keys;
    }

    /**
     * Lays the persisted overrides on top of {@code base} (the defaults already merged with
     * {@code config.json}). A missing file is normal — the player has changed nothing yet. A malformed
     * one is <em>ignored</em>, not fatal: we keep {@code base} rather than throwing away the player's
     * {@code config.json} for a stray character.
     */
    public static void overlay(ObjectNode base, Path overlayPath) {
        if (!Files.exists(overlayPath)) {
            return;
        }

        try {
            final var persisted = MAPPER.readTree(Files.readString(overlayPath));
            if (!persisted.isObject()) {
                log.warn("{} is not a JSON object. Ignoring the persisted overlay state.", overlayPath);
                return;
            }

            // Only the keys we own — a stale key left in the file by a previous version does not leak
            // into the config, and neither does anything a hand-edit slipped in.
            for (final var key : PERSISTED_KEYS) {
                if (persisted.has(key)) {
                    base.set(key, persisted.get(key));
                }
            }
            log.info("Overlay state loaded from {}", overlayPath);
        } catch (IOException e) {
            log.error("Could not read the overlay state: {}", e.getMessage());
        }
    }

    /**
     * Writes the persisted subset of {@code config} to {@code overlay.json}. Called on whichever thread
     * made the change — a virtual thread from an overlay callback — so file I/O here is free.
     *
     * <p>Writes through a temp file and an atomic move, so a reader (the next startup) never sees a
     * half-written file; and under a lock, so two overlay edits at once cannot interleave.
     */
    public static void save(Path overlayPath, Config config) {
        final var full = MAPPER.<ObjectNode>valueToTree(config);
        final var persisted = MAPPER.createObjectNode();
        for (final var key : PERSISTED_KEYS) {
            persisted.set(key, full.get(key));
        }

        synchronized (WRITE_LOCK) {
            try {
                final var parent = overlayPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                final var tmp = Files.createTempFile(parent, "overlay", ".json.tmp");
                MAPPER.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(), persisted);
                Files.move(tmp, overlayPath,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                log.debug("Overlay state saved to {}", overlayPath);
            } catch (IOException e) {
                log.error("Could not save the overlay state: {}", e.getMessage());
            }
        }
    }
}
