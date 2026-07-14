package fr.minobot.app;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
        // Untouched keys must not collapse to null — a blank hotkey is a disabled feature.
        assertThat(config.multiclickHotkey()).isEqualTo("x1");
        assertThat(config.groupInviteHotkey()).isEqualTo("F8");
        assertThat(config.multiclickExclude()).isEmpty();
    }

    @Test
    void malformedFileFallsBackToDefaults(@TempDir Path dir) throws IOException {
        Path configPath = dir.resolve("config.json");
        Files.writeString(configPath, "{ not json");

        assertThat(ConfigLoader.load(configPath)).isEqualTo(Config.defaults());
    }

    @Test
    @DisplayName("a setting dropped from the record is ignored, not a crash on an old config.json")
    void ignoresUnknownKeys(@TempDir Path dir) throws IOException {
        Path configPath = dir.resolve("config.json");
        Files.writeString(configPath, """
                {
                  "multiclick_button": "right",
                  "smart_focus_enabled": false
                }
                """);

        assertThat(ConfigLoader.load(configPath)).isEqualTo(Config.defaults());
    }
}
