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
        @JsonProperty("window_reorder_hotkey") String windowReorderHotkey,
        @JsonProperty("overlay_hotkey") String overlayHotkey,
        @JsonProperty("overlay_scale") double overlayScale,

        /**
         * Whether the turn-passer ends each character's turn on its own. Every other feature is turned
         * off by a blank hotkey; this one has no hotkey to blank — it is a switch on the overlay, not a
         * key — so it earns the one explicit flag in this record. The overlay flips it, and a restart
         * forgets it: unlike the order and the keybinds, this switch is not persisted, deliberately.
         */
        @JsonProperty("auto_pass_turn") boolean autoPassTurn,

        /**
         * Whether an exchange one of the player's own characters asks of another is accepted for them.
         * The same kind of switch as {@link #autoPassTurn}: no hotkey, flipped on the overlay, and not
         * persisted — forgotten on restart.
         */
        @JsonProperty("auto_accept_trade") boolean autoAcceptTrade
) {

    /**
     * The ends of the overlay's slider, and the bounds a hand-written {@code config.json} is held to.
     *
     * <p>The panel is drawn at this many times its natural size, so a zero would be a panel with
     * nothing in it — which is why the value is clamped rather than trusted.
     */
    public static final double MIN_OVERLAY_SCALE = 1.0;
    public static final double MAX_OVERLAY_SCALE = 2.0;

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
                "F9",
                "shift+space",
                // Swing's natural sizes were laid out for a 96-DPI desktop, and the game is played on a
                // screen twice that. Unscaled, the panel is legible and nobody wants to read it.
                1.5,
                // Off by default: it plays the characters for the player, which is a thing they switch
                // on deliberately, never a thing they should find already running.
                false,
                // On by default: passing items between one's own accounts is the bread and butter of
                // multi-boxing, and it only ever fires on a trade the player's own character asked for.
                true
        );
    }

    public Config {
        multiclickExclude = copyOrEmpty(multiclickExclude);
        windowCycleOrder = copyOrEmpty(windowCycleOrder);
        overlayScale = Math.clamp(overlayScale, MIN_OVERLAY_SCALE, MAX_OVERLAY_SCALE);
    }

    /**
     * The same configuration with one hotkey changed — how the overlay rebinds a key.
     *
     * <p>A blank combination turns the feature off, which is what makes this the toggle too.
     */
    public Config withHotkey(Feature feature, String combination) {
        return new Config(
                logLevel,
                hotkeyOf(Feature.MULTICLICK, feature, combination),
                multiclickExclude,
                hotkeyOf(Feature.RESET_WINDOWS, feature, combination),
                hotkeyOf(Feature.GROUP_INVITE, feature, combination),
                windowCycleOrder,
                hotkeyOf(Feature.WINDOW_CYCLE_NEXT, feature, combination),
                hotkeyOf(Feature.WINDOW_CYCLE_PREV, feature, combination),
                hotkeyOf(Feature.WINDOW_REORDER, feature, combination),
                hotkeyOf(Feature.OVERLAY, feature, combination),
                overlayScale,
                autoPassTurn,
                autoAcceptTrade);
    }

    /** The new combination for the feature being rebound, the current one for every other. */
    private String hotkeyOf(Feature slot, Feature rebound, String combination) {
        return slot == rebound ? combination : slot.hotkeyIn(this);
    }

    /** The same configuration in a new character order — how the overlay's drag & drop lands. */
    public Config withWindowCycleOrder(List<String> order) {
        return new Config(
                logLevel, multiclickHotkey, multiclickExclude, resetWindowsHotkey, groupInviteHotkey,
                order, windowCycleNextHotkey, windowCyclePrevHotkey, windowReorderHotkey, overlayHotkey,
                overlayScale, autoPassTurn, autoAcceptTrade);
    }

    /** The same configuration with a different set of characters left out of the multi-click. */
    public Config withMulticlickExclude(List<String> excluded) {
        return new Config(
                logLevel, multiclickHotkey, excluded, resetWindowsHotkey, groupInviteHotkey,
                windowCycleOrder, windowCycleNextHotkey, windowCyclePrevHotkey, windowReorderHotkey,
                overlayHotkey, overlayScale, autoPassTurn, autoAcceptTrade);
    }

    /** The same configuration with the panel drawn bigger or smaller — how its slider lands. */
    public Config withOverlayScale(double scale) {
        return new Config(
                logLevel, multiclickHotkey, multiclickExclude, resetWindowsHotkey, groupInviteHotkey,
                windowCycleOrder, windowCycleNextHotkey, windowCyclePrevHotkey, windowReorderHotkey,
                overlayHotkey, scale, autoPassTurn, autoAcceptTrade);
    }

    /** The same configuration with the turn-passer switched on or off — how the overlay's toggle lands. */
    public Config withAutoPassTurn(boolean enabled) {
        return new Config(
                logLevel, multiclickHotkey, multiclickExclude, resetWindowsHotkey, groupInviteHotkey,
                windowCycleOrder, windowCycleNextHotkey, windowCyclePrevHotkey, windowReorderHotkey,
                overlayHotkey, overlayScale, enabled, autoAcceptTrade);
    }

    /** The same configuration with the trade-accepter switched on or off — the overlay's other toggle. */
    public Config withAutoAcceptTrade(boolean enabled) {
        return new Config(
                logLevel, multiclickHotkey, multiclickExclude, resetWindowsHotkey, groupInviteHotkey,
                windowCycleOrder, windowCycleNextHotkey, windowCyclePrevHotkey, windowReorderHotkey,
                overlayHotkey, overlayScale, autoPassTurn, enabled);
    }

    private static List<String> copyOrEmpty(List<String> value) {
        return value == null ? List.of() : List.copyOf(value);
    }
}
