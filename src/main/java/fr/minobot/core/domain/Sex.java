package fr.minobot.core.domain;

/**
 * A character's sex — the second half of what picks their icon, next to their {@link DofusClass}.
 *
 * <p>Like the class, it is the player's to set and the game window says nothing of it, so it too is
 * carried in the configuration by character name and persisted with it. It exists because the class
 * icons come in two, one per sex: the {@link #suffix()} is what tells {@code iop_m.svg} from
 * {@code iop_f.svg}.
 */
public enum Sex {

    MALE("m", "M"),
    FEMALE("f", "F");

    private final String suffix;
    private final String label;

    Sex(String suffix, String label) {
        this.suffix = suffix;
        this.label = label;
    }

    /** What the sex adds to an icon's file name — {@code "m"} in {@code iop_m.svg}. */
    public String suffix() {
        return suffix;
    }

    /** The single letter the picker's toggle shows for this sex. */
    public String label() {
        return label;
    }
}
