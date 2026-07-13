package fr.minobot.app;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.List;

/**
 * Application settings.
 *
 * <p>Any field absent from the user's {@code config.json} keeps the value given here, which
 * reproduces the {@code DEFAULT_CONFIG.update(user_config)} merge of {@code config_loader.py}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Config(
        @JsonProperty("poll_interval") double pollIntervalSeconds,
        @JsonProperty("notification_batch_size") int notificationBatchSize,
        @JsonProperty("focus_cooldown") double focusCooldownSeconds,
        @JsonProperty("window_refresh_interval") double windowRefreshIntervalSeconds,
        @JsonProperty("game_keywords") List<String> gameKeywords,
        @JsonProperty("character_separators") List<String> characterSeparators,

        @JsonProperty("log_level") String logLevel,
        @JsonProperty("log_to_file") boolean logToFile,
        @JsonProperty("log_file_path") String logFilePath,

        @JsonProperty("multiclick_enabled") boolean multiclickEnabled,
        @JsonProperty("multiclick_hotkey") String multiclickHotkey,
        @JsonProperty("multiclick_combination") String multiclickCombination,
        @JsonProperty("multiclick_button") String multiclickButton,
        @JsonProperty("multiclick_delay") double multiclickDelaySeconds,
        @JsonProperty("multiclick_cooldown") double multiclickCooldownSeconds,
        @JsonProperty("multiclick_restore_focus") boolean multiclickRestoreFocus,
        @JsonProperty("multiclick_dry_run") boolean multiclickDryRun,
        @JsonProperty("multiclick_exclude") List<String> multiclickExclude,
        @JsonProperty("reset_windows_hotkey") String resetWindowsHotkey,

        @JsonProperty("group_invite_enabled") boolean groupInviteEnabled,
        @JsonProperty("group_invite_hotkey") String groupInviteHotkey,

        @JsonProperty("window_cycle_enabled") boolean windowCycleEnabled,
        @JsonProperty("window_cycle_order") List<String> windowCycleOrder,
        @JsonProperty("window_cycle_next_hotkey") String windowCycleNextHotkey,
        @JsonProperty("window_cycle_prev_hotkey") String windowCyclePrevHotkey,

        @JsonProperty("window_reorder_enabled") boolean windowReorderEnabled,
        @JsonProperty("window_reorder_hotkey") String windowReorderHotkey,

        @JsonProperty("smart_focus_enabled") boolean smartFocusEnabled,
        @JsonProperty("smart_focus_threshold") double smartFocusThresholdSeconds
) {

    /** The values written to disk when no {@code config.json} exists yet. */
    public static Config defaults() {
        return new Config(
                0.5,
                10,
                0.1,
                30,
                List.of("Dofus"),
                List.of(" - ", ": ", " | "),

                "INFO",
                true,
                "logs/minobot.log",

                true,
                "x1",
                "",
                "left",
                0.01,
                0.1,
                false,
                false,
                List.of(),
                "shift+x1",

                true,
                "F8",

                true,
                List.of(),
                "x2",
                "shift+x2",

                true,
                "F9",

                true,
                2.0
        );
    }

    public Config {
        gameKeywords = copyOrDefault(gameKeywords, List.of("Dofus"));
        characterSeparators = copyOrDefault(characterSeparators, List.of(" - ", ": ", " | "));
        multiclickExclude = copyOrDefault(multiclickExclude, List.of());
        windowCycleOrder = copyOrDefault(windowCycleOrder, List.of());
    }

    private static List<String> copyOrDefault(List<String> value, List<String> fallback) {
        return value == null ? fallback : List.copyOf(value);
    }

    @JsonIgnore
    public Duration pollInterval() {
        return seconds(pollIntervalSeconds);
    }

    @JsonIgnore
    public Duration focusCooldown() {
        return seconds(focusCooldownSeconds);
    }

    @JsonIgnore
    public Duration windowRefreshInterval() {
        return seconds(windowRefreshIntervalSeconds);
    }

    @JsonIgnore
    public Duration multiclickDelay() {
        return seconds(multiclickDelaySeconds);
    }

    @JsonIgnore
    public Duration multiclickCooldown() {
        return seconds(multiclickCooldownSeconds);
    }

    @JsonIgnore
    public Duration smartFocusThreshold() {
        return seconds(smartFocusThresholdSeconds);
    }

    private static Duration seconds(double value) {
        return Duration.ofNanos(Math.round(value * 1_000_000_000d));
    }
}
