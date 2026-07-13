package fr.minobot.core.domain;

/** A detected game window: its handle and its full title bar text. */
public record GameWindow(long hwnd, String title) {
}
