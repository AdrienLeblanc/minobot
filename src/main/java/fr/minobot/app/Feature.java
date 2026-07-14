package fr.minobot.app;

import java.time.Duration;

/**
 * The features a player binds a key to, and the slot each one occupies in {@link Config}.
 *
 * <p>It exists so that the hotkeys can be enumerated rather than listed by hand: {@code MinobotApp}
 * walks it to register them, and the overlay walks it to show them. A feature added to the enum and
 * forgotten in the switch that gives it its action is a compile error, which is the point.
 *
 * <p>The cooldown is each hotkey's own minimum spacing: enough to swallow a double trigger, never
 * enough to feel laggy. It is dictated by the hardware and the feature, not by the player, which is
 * why it lives here and not in {@code config.json}.
 */
public enum Feature {

    MULTICLICK("Multi-window click", Duration.ofMillis(10)),
    RESET_WINDOWS("Character reset", Duration.ofSeconds(1)),
    GROUP_INVITE("Group invitation", Duration.ofSeconds(5)),
    WINDOW_CYCLE_NEXT("Window cycler (next)", Duration.ofMillis(100)),
    WINDOW_CYCLE_PREV("Window cycler (previous)", Duration.ofMillis(100)),
    WINDOW_REORDER("Window reorder", Duration.ofSeconds(5)),
    OVERLAY("Overlay", Duration.ofMillis(200));

    private final String label;
    private final Duration cooldown;

    Feature(String label, Duration cooldown) {
        this.label = label;
        this.cooldown = cooldown;
    }

    /** How the feature is named to the player — in the logs, and in the overlay. */
    public String label() {
        return label;
    }

    public Duration cooldown() {
        return cooldown;
    }

    /** The key this feature is bound to, or blank when the player has turned it off. */
    public String hotkeyIn(Config config) {
        final var hotkey = switch (this) {
            case MULTICLICK -> config.multiclickHotkey();
            case RESET_WINDOWS -> config.resetWindowsHotkey();
            case GROUP_INVITE -> config.groupInviteHotkey();
            case WINDOW_CYCLE_NEXT -> config.windowCycleNextHotkey();
            case WINDOW_CYCLE_PREV -> config.windowCyclePrevHotkey();
            case WINDOW_REORDER -> config.windowReorderHotkey();
            case OVERLAY -> config.overlayHotkey();
        };
        return hotkey == null ? "" : hotkey;
    }
}
