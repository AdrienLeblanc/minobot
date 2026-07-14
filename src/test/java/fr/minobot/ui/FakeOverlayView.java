package fr.minobot.ui;

import fr.minobot.win32.Rect;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The panel, without a screen.
 *
 * <p>What the controller decides — which character the panel belongs to, what it lists, when it moves
 * and when it merely redraws — is all visible here, and none of it needs Swing, a monitor, or a game.
 */
public final class FakeOverlayView implements OverlayView {

    private final List<Rect> placements = new CopyOnWriteArrayList<>();
    private final List<OverlayContent> drawn = new CopyOnWriteArrayList<>();

    private volatile boolean visible;

    @Override
    public void show(OverlayContent content, Rect bounds) {
        visible = true;
        drawn.add(content);
        placements.add(bounds);
    }

    @Override
    public void moveTo(Rect bounds) {
        placements.add(bounds);
    }

    @Override
    public void hide() {
        visible = false;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    /** What the panel was last handed to draw, empty if it was never shown. */
    public Optional<OverlayContent> content() {
        return drawn.isEmpty() ? Optional.empty() : Optional.of(drawn.getLast());
    }

    /** Where the panel now is — placed by a {@code show}, or moved there by the follower. */
    public Optional<Rect> bounds() {
        return placements.isEmpty() ? Optional.empty() : Optional.of(placements.getLast());
    }

    /** How many times it was drawn — a redraw after an edit is one more. A move is not a draw. */
    public int timesDrawn() {
        return drawn.size();
    }
}
