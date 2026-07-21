package fr.minobot.core.domain;

import java.util.Locale;

/**
 * One of the twelve classes a Dofus Retro character can be.
 *
 * <p>The class is the player's to set — the game window says nothing of it — so, like the cycle order,
 * it is carried in the configuration by the character's name and persisted with it. A {@link GameWindow}
 * therefore never knows a character's class: it is a window, and a window is not a choice.
 *
 * <p>A class is the same twelve wherever it is drawn, so each carries its own {@link #label()} and the
 * path of its icon in the jar ({@link #iconResource(Sex)}) — one SVG per class <em>and sex</em>, shipped
 * on the classpath so it rides inside {@code Minobot.exe}. The picture itself is the view's to load; the
 * enum only says where it lives, the same way {@code SwingOverlay} knows where the application logo lives.
 */
public enum DofusClass {

    CRA("Crâ"),
    ENUTROF("Enutrof"),
    IOP("Iop"),
    FECA("Feca"),
    SADIDA("Sadida"),
    XELOR("Xelor"),
    OSAMODAS("Osamodas"),
    ENIRIPSA("Eniripsa"),
    SACRIEUR("Sacrieur"),
    PANDAWA("Pandawa"),
    ECAFLIP("Ecaflip"),
    SRAM("Sram");

    private final String label;

    DofusClass(String label) {
        this.label = label;
    }

    /** The class as the player reads it, SECONDARYs and all — {@code "Crâ"}. */
    public String label() {
        return label;
    }

    /**
     * Where this class's icon rides on the classpath, for a given sex: {@code "/classes/iop_m.svg"}. One
     * SVG per class and sex, its leaf the class's own name in lower case and the sex's one-letter suffix.
     * Reading it is the view's — and its absence is not an error: the panel falls back to a lettered
     * badge, the way it falls back to the wordmark for a missing logo.
     */
    public String iconResource(Sex sex) {
        return "/classes/" + name().toLowerCase(Locale.ROOT) + "_" + sex.suffix() + ".svg";
    }
}
