package fr.minobot.feature;

import fr.minobot.app.Feature;
import fr.minobot.app.Settings;
import fr.minobot.core.KeyboardMonitor;
import fr.minobot.core.WindowManager;
import fr.minobot.core.domain.GameWindow;
import fr.minobot.ui.OverlayActions;
import fr.minobot.ui.OverlayContent;
import fr.minobot.ui.OverlayView;
import fr.minobot.win32.Rect;
import fr.minobot.win32.WindowApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * The control panel: the characters Minobot has found, the order they are cycled in, and the key each
 * feature answers to — all of it editable, on top of the game.
 *
 * <p><strong>A reorder and a rebind are kept; the rest is not.</strong> The character order and the
 * keybinds go through {@link Settings} and are persisted to {@code overlay.json} (see {@link
 * fr.minobot.app.OverlayState}), so they survive a restart. The scale and the two switches also go
 * through {@link Settings}, but nothing persists them: they live for the session only, the switches
 * deliberately — an {@code Auto-pass} found already on after a restart would end turns nobody asked it
 * to. The controller does not know which of the two happens; it just calls {@code settings.update}.
 *
 * <p>The panel <em>belongs to a character</em>. It covers their game — the client area exactly, never
 * the title bar Windows draws above it — and it <em>follows</em> it: move the window, resize it, and
 * the panel goes with it. Pressing the hotkey anywhere but on a game window does nothing at all. Not
 * "shown somewhere by default": outside the game there is no character for it to belong to, and a panel
 * that appears over a window the player was not looking at is a panel they did not ask for.
 *
 * <p>Once up it <em>stays</em> up, whatever takes the foreground next: a toast pulling the focus to
 * another character, the player clicking into another application. The panel is always-on-top, and
 * always-on-top owes nothing to the focus. The hotkey is the only switch — except that a character who
 * leaves takes their panel with them: minimized, or closed, and it goes.
 *
 * <p>It never takes the foreground itself — see {@code OverlayView}. It must not: {@link
 * fr.minobot.core.FocusManager} is what keeps the features from fighting over the one screen, and a
 * panel that stole the focus would land in the middle of the invitation relay's keystrokes.
 */
public final class OverlayController implements OverlayActions {

    private static final Logger log = LoggerFactory.getLogger(OverlayController.class);

    /** Long enough for a player to reach for the key they meant, short enough to give up on its own. */
    private static final Duration CAPTURE_TIMEOUT = Duration.ofSeconds(5);

    /**
     * What the panel shows for a game window with no character loaded yet — a login or selection screen.
     *
     * <p>The window is the game's, so the panel opens on it and lists it; but it has no character name
     * to show, and none to be cycled by. So it is drawn under this label and left out of the saved
     * order — see {@link #reorder}.
     */
    private static final String LOGGING_IN = "(connecting…)";

    /**
     * How often the panel checks that it is still on top of its character.
     *
     * <p>Fast enough that a dragged window does not visibly outrun it, and one native call is all it
     * costs — the same call the multi-click makes several of, several times a second.
     */
    private static final Duration FOLLOW_INTERVAL = Duration.ofMillis(30);

    private final WindowApi api;
    private final WindowManager windows;
    private final Settings settings;
    private final KeyboardMonitor keyboard;
    private final OverlayView view;

    /** Where the panel last was, so an edit can redraw it without asking Windows again. */
    private final AtomicReference<Rect> bounds = new AtomicReference<>();

    /**
     * @param viewFactory hands the view its way back in, so neither has to be built before the other
     */
    public OverlayController(WindowApi api, WindowManager windows, Settings settings,
                             KeyboardMonitor keyboard, Function<OverlayActions, OverlayView> viewFactory) {
        this.api = api;
        this.windows = windows;
        this.settings = settings;
        this.keyboard = keyboard;
        this.view = viewFactory.apply(this);
    }

    /** Shows the panel over the character the player is on, or takes it away if it is already up. */
    public void toggle() {
        if (view.isVisible()) {
            log.debug("Hiding the overlay.");
            view.hide();
            return;
        }

        final var character = characterInForeground();
        if (character.isEmpty()) {
            log.debug("The overlay hotkey was pressed outside the game: nothing to show it on.");
            return;
        }

        final var area = api.clientArea(character.get().hwnd());
        if (area.isEmpty()) {
            log.warn("'{}' went away before the overlay could be placed on it.", character.get().name());
            return;
        }

        log.info("Showing the overlay on '{}'.", character.get().name());
        bounds.set(area.get());
        view.show(content(), area.get());

        follow(character.get());
    }

    /**
     * Keeps the panel on its character while the player drags or resizes the window.
     *
     * <p>A thread of its own, because there is nothing to react to: Windows will not tell us a window
     * moved unless we hook its messages, and hooking them would mean a message pump and a native
     * callback for something one poll answers. It lives exactly as long as the panel does.
     */
    private void follow(GameWindow character) {
        Thread.ofVirtual().name("overlay-follow").start(() -> {
            while (view.isVisible()) {
                final var area = api.clientArea(character.hwnd());

                // The character has gone: minimized, or closed. Their panel goes with them — leaving it
                // floating over the game they are no longer playing would be a panel nobody can dismiss.
                if (area.isEmpty() || api.isIconic(character.hwnd())) {
                    log.debug("'{}' is no longer on screen: taking the overlay down.", character.name());
                    view.hide();
                    return;
                }

                if (!area.get().equals(bounds.get())) {
                    bounds.set(area.get());
                    view.moveTo(area.get());
                }

                if (!sleep()) {
                    return;
                }
            }
        });
    }

    /** @return whether the follower may go on; {@code false} means the thread was interrupted */
    private static boolean sleep() {
        try {
            Thread.sleep(FOLLOW_INTERVAL);
            return true;
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void reorder(List<String> characters) {
        // A not-yet-logged-in window rides in the list under its label, but has no name to be cycled by:
        // it must not land in the order, where it would be a dead entry until the character loads.
        final var named = characters.stream().filter(name -> !name.equals(LOGGING_IN)).toList();
        log.info("New character order: {}", named);
        settings.update(config -> config.withWindowCycleOrder(named));
        redraw();
    }

    @Override
    public void reload() {
        log.info("Reloading the character list on the player's request.");
        windows.refresh();
        redraw();
    }

    @Override
    public void rebind(Feature feature, String combination) {
        if (combination.isBlank()) {
            log.info("Feature '{}' turned off.", feature.label());
        } else {
            log.info("Feature '{}' rebound to '{}'.", feature.label(), combination);
        }

        settings.update(config -> config.withHotkey(feature, combination));
        redraw();
    }

    @Override
    public void rescale(double scale) {
        log.info("Overlay scale set to {}%.", Math.round(scale * 100));
        settings.update(config -> config.withOverlayScale(scale));
        redraw();
    }

    @Override
    public void toggleAutoPassTurn(boolean on) {
        log.info("Auto-pass turns switched {}.", on ? "on" : "off");
        settings.update(config -> config.withAutoPassTurn(on));
        redraw();
    }

    @Override
    public void toggleAutoAcceptTrade(boolean on) {
        log.info("Auto-accept trades switched {}.", on ? "on" : "off");
        settings.update(config -> config.withAutoAcceptTrade(on));
        redraw();
    }

    @Override
    public Optional<String> captureHotkey() {
        return keyboard.captureNext(CAPTURE_TIMEOUT);
    }

    /**
     * The character whose window holds the screen right now, if it is one of ours.
     *
     * <p>Decided from the foreground window's own process, not from the tracked list: a login window
     * the 30-second sweep has not caught is still the game, and the panel must open on it. Empty for
     * anything else — a browser, the desktop. It may carry no character name yet (a login screen); the
     * panel handles that with its {@link #LOGGING_IN} label.
     */
    private Optional<GameWindow> characterInForeground() {
        return windows.foregroundGameWindow();
    }

    /** What the panel shows, read fresh from the live configuration and the windows on screen. */
    private OverlayContent content() {
        final var config = settings.get();

        final var hotkeys = new EnumMap<Feature, String>(Feature.class);
        for (final var feature : Feature.values()) {
            hotkeys.put(feature, feature.hotkeyIn(config));
        }

        final var characters = windows.orderedWindows().stream()
                .map(this::label)
                .toList();

        return new OverlayContent(characters, hotkeys, config.overlayScale(), config.autoPassTurn(),
                config.autoAcceptTrade());
    }

    /** The character's name, or the login label for a window with none loaded yet. */
    private String label(GameWindow window) {
        return window.hasCharacterName() ? window.name() : LOGGING_IN;
    }

    /** Shows the change the player just made, without moving the panel off its character. */
    private void redraw() {
        final var placed = bounds.get();
        if (!view.isVisible() || placed == null) {
            return;
        }
        view.show(content(), placed);
    }
}
