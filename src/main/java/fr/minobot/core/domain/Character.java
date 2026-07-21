package fr.minobot.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A character the player runs: their name, and what they have pinned to it on the overlay — a class and
 * a sex, and whatever a later feature gives a character to carry. One entity for the whole of it, so a
 * new thing a character owns is a field here, not another map keyed by name somewhere else.
 *
 * <p>Everything is keyed by {@link #name()}, because a {@link GameWindow} is a window and a window is
 * not a choice: the same character keeps its class through a reorder, a relaunch, a fresh handle. It is
 * the player's part of a character — the game window tells us the name, the player tells us the rest.
 *
 * <p>{@link #clazz()} and {@link #sex()} are {@code null} until the player picks them: a character with
 * no class shows an invitation to choose one, and one with no sex is drawn as {@link Sex#MALE}. They are
 * left out of the JSON entirely while unset ({@code @JsonInclude(NON_NULL)}), so an untouched character
 * is one clean {@code {"name": …}} line.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Character(
        @JsonProperty("name") String name,
        @JsonProperty("class") DofusClass clazz,
        @JsonProperty("sex") Sex sex) {

    /** A character the player has named but pinned nothing else to yet. */
    public Character(String name) {
        this(name, null, null);
    }

    /** The same character with a class pinned — the picker's landing, keeping their sex. */
    public Character withClass(DofusClass clazz) {
        return new Character(name, clazz, sex);
    }

    /** The same character with a sex pinned — the picker's other half, keeping their class. */
    public Character withSex(Sex sex) {
        return new Character(name, clazz, sex);
    }

    /** The sex to draw them in: the one they were given, or {@link Sex#MALE} until they are. */
    public Sex sexOrDefault() {
        return sex == null ? Sex.MALE : sex;
    }

    /**
     * Whether the player has pinned anything to this character — a class, or a sex.
     *
     * <p>A character with nothing pinned is a bare name: the panel shows it only while its window is
     * open, and forgets it the moment the character logs out. One the player has configured is kept,
     * shown greyed-out while disconnected, until they forget it by hand.
     *
     * <p>{@code @JsonIgnore}: it is derived from the class and sex, not a field of its own — Jackson would
     * otherwise write a {@code "pinned"} key that no constructor reads back.
     */
    @JsonIgnore
    public boolean isPinned() {
        return clazz != null || sex != null;
    }
}
