package fr.minobot.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** The one part of the overlay's edits that outlives the process: the order and the keybinds. */
class OverlayStateTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("it saves the order and the seven keybinds, and nothing else")
    void savesOnlyThePersistedSubset(@TempDir Path dir) throws IOException {
        Path overlayPath = dir.resolve("overlay.json");
        Config config = TestConfigs.with(Map.of(
                "window_cycle_order", List.of("Bravo", "Alpha"),
                "group_invite_hotkey", "F7",
                // Deliberately session-only — these must not reach the file.
                "overlay_scale", 2.0,
                "auto_pass_turn", true,
                "auto_accept_trade", false));

        OverlayState.save(overlayPath, config);

        ObjectNode written = (ObjectNode) MAPPER.readTree(Files.readString(overlayPath));
        Set<String> keys = new java.util.HashSet<>();
        written.fieldNames().forEachRemaining(keys::add);

        assertThat(keys).containsExactlyInAnyOrder(
                "window_cycle_order",
                "multiclick_hotkey", "reset_windows_hotkey", "group_invite_hotkey",
                "window_cycle_next_hotkey", "window_cycle_prev_hotkey", "window_reorder_hotkey",
                "overlay_hotkey");
        assertThat(keys).doesNotContain(
                "overlay_scale", "auto_pass_turn", "auto_accept_trade", "log_level", "multiclick_exclude");
    }

    @Test
    @DisplayName("what it saves reloads to the same order and keybinds")
    void roundTripsThroughTheLoader(@TempDir Path dir) {
        Path configPath = dir.resolve("config.json");
        Path overlayPath = dir.resolve("overlay.json");
        Config edited = TestConfigs.with(Map.of(
                "window_cycle_order", List.of("Bravo", "Alpha"),
                "group_invite_hotkey", "F7"));

        OverlayState.save(overlayPath, edited);
        Config reloaded = ConfigLoader.load(configPath, overlayPath);

        assertThat(reloaded.windowCycleOrder()).containsExactly("Bravo", "Alpha");
        assertThat(reloaded.groupInviteHotkey()).isEqualTo("F7");
    }

    @Test
    @DisplayName("the session-only fields fall back to config.json on reload, never to the saved state")
    void doesNotPersistTheSessionOnlyFields(@TempDir Path dir) throws IOException {
        Path configPath = dir.resolve("config.json");
        Path overlayPath = dir.resolve("overlay.json");
        Files.writeString(configPath, """
                { "overlay_scale": 1.2, "auto_pass_turn": false }
                """);
        Config edited = TestConfigs.with(Map.of("overlay_scale", 2.0, "auto_pass_turn", true));

        OverlayState.save(overlayPath, edited);
        Config reloaded = ConfigLoader.load(configPath, overlayPath);

        assertThat(reloaded.overlayScale()).isEqualTo(1.2);
        assertThat(reloaded.autoPassTurn()).isFalse();
    }

    @Test
    @DisplayName("concurrent saves never leave a half-written file")
    void concurrentSavesNeverCorruptTheFile(@TempDir Path dir) throws InterruptedException, IOException {
        Path overlayPath = dir.resolve("overlay.json");
        int writers = 16;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> threads = new ArrayList<>();

        for (int i : IntStream.range(0, writers).toArray()) {
            Config config = TestConfigs.with(Map.of("window_cycle_order", List.of("Char" + i)));
            Thread t = Thread.ofVirtual().unstarted(() -> {
                await(start);
                OverlayState.save(overlayPath, config);
            });
            threads.add(t);
            t.start();
        }
        start.countDown();
        for (Thread t : threads) {
            t.join();
        }

        // Whatever the winner, the file is one whole, valid JSON object with exactly one character.
        ObjectNode written = (ObjectNode) MAPPER.readTree(Files.readString(overlayPath));
        assertThat(written.get("window_cycle_order")).hasSize(1);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
