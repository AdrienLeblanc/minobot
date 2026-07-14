package fr.minobot.app;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigLoaderTest {

    @Test
    void createsTheFileWithDefaultsWhenNoneExists(@TempDir Path dir) {
        Path configPath = dir.resolve("config.json");

        Config config = ConfigLoader.load(configPath);

        assertThat(Files.exists(configPath)).as("config.json should have been generated").isTrue();
        assertThat(config).isEqualTo(Config.defaults());
    }

    @Test
    void generatedFileReloadsToTheSameConfig(@TempDir Path dir) {
        Path configPath = dir.resolve("config.json");
        ConfigLoader.load(configPath);

        assertThat(ConfigLoader.load(configPath)).isEqualTo(Config.defaults());
    }

    @Test
    void keysAbsentFromTheFileKeepTheirDefault(@TempDir Path dir) throws IOException {
        Path configPath = dir.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "log_level": "DEBUG",
                  "window_cycle_order": ["Bravo", "Echo"]
                }
                """);

        Config config = ConfigLoader.load(configPath);

        assertThat(config.logLevel()).isEqualTo("DEBUG");
        assertThat(config.windowCycleOrder()).isEqualTo(List.of("Bravo", "Echo"));
        // Untouched keys must not collapse to 0 / false / null.
        assertThat(config.pollIntervalSeconds()).isEqualTo(0.5);
        assertThat(config.notificationBatchSize()).isEqualTo(10);
        assertThat(config.multiclickEnabled()).isTrue();
        assertThat(config.gameKeywords()).isEqualTo(List.of("Dofus"));
    }

    @Test
    void malformedFileFallsBackToDefaults(@TempDir Path dir) throws IOException {
        Path configPath = dir.resolve("config.json");
        Files.writeString(configPath, "{ not json");

        assertThat(ConfigLoader.load(configPath)).isEqualTo(Config.defaults());
    }

    @Test
    void fractionalSecondsBecomeDurations() {
        Config config = Config.defaults();

        assertThat(config.pollInterval()).isEqualTo(Duration.ofMillis(500));
        assertThat(config.focusCooldown()).isEqualTo(Duration.ofMillis(100));
        assertThat(config.multiclickDelay()).isEqualTo(Duration.ofMillis(10));
    }
}
