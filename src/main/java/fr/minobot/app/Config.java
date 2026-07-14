package fr.minobot.app;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The settings a player is expected to change: their character names, their hotkeys.
 *
 * <p>Everything else the application needs is a constant living next to the code that uses it — the
 * timings, the game's window-title format, the log plumbing. They are dictated by Windows or by the
 * game, not by the player, and exposing them only invited someone to break the features by tuning
 * them.
 *
 * <p>A blank hotkey turns its feature off. Any field absent from the user's {@code config.json} keeps
 * the value given in {@link #defaults()}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Config(
        @JsonProperty("log_level") String logLevel,
        @JsonProperty("multiclick_hotkey") String multiclickHotkey,
        @JsonProperty("multiclick_exclude") List<String> multiclickExclude,
        @JsonProperty("reset_windows_hotkey") String resetWindowsHotkey,
        @JsonProperty("group_invite_hotkey") String groupInviteHotkey,
        @JsonProperty("window_cycle_order") List<String> windowCycleOrder,
        @JsonProperty("window_cycle_next_hotkey") String windowCycleNextHotkey,
        @JsonProperty("window_cycle_prev_hotkey") String windowCyclePrevHotkey,
        @JsonProperty("window_reorder_hotkey") String windowReorderHotkey
) {

    /** The values written to disk when no {@code config.json} exists yet. */
    public static Config defaults() {
        return new Config(
                "INFO",
                "x1",
                List.of(),
                "shift+x1",
                "F8",
                List.of(),
                "x2",
                "shift+x2",
                "F9"
        );
    }

    public Config {
        multiclickExclude = copyOrEmpty(multiclickExclude);
        windowCycleOrder = copyOrEmpty(windowCycleOrder);
    }

    private static List<String> copyOrEmpty(List<String> value) {
        return value == null ? List.of() : List.copyOf(value);
    }
}
