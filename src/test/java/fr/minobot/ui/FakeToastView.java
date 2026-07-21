package fr.minobot.ui;

import fr.minobot.win32.Rect;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The whisper stack, without a screen — the twin of {@link FakeOverlayView}.
 *
 * <p>What the controller decides — which messages become cards, when the stack moves and when it hides,
 * what a click does — is all visible here, and none of it needs Swing, a monitor, or a game. It keeps
 * the {@link ToastActions} it was built with, so a test can play a click on a card or its cross.
 */
public final class FakeToastView implements ToastView {

    private final ToastActions actions;

    private final List<ToastContent> drawn = new CopyOnWriteArrayList<>();
    private final List<Rect> anchors = new CopyOnWriteArrayList<>();

    private volatile boolean visible;

    public FakeToastView(ToastActions actions) {
        this.actions = actions;
    }

    @Override
    public void show(ToastContent content, Rect anchor) {
        visible = true;
        drawn.add(content);
        anchors.add(anchor);
    }

    @Override
    public void moveTo(Rect anchor) {
        anchors.add(anchor);
    }

    @Override
    public void hide() {
        visible = false;
    }

    @Override
    public boolean isVisible() {
        return visible;
    }

    /** What the stack was last handed to draw, empty if it was never shown. */
    public Optional<ToastContent> content() {
        return drawn.isEmpty() ? Optional.empty() : Optional.of(drawn.getLast());
    }

    /** The cards on the last drawn stack — empty if it was never shown. */
    public List<ToastContent.Card> cards() {
        return content().map(ToastContent::cards).orElseGet(List::of);
    }

    /** Where the stack now hangs — placed by a {@code show}, or moved there by the follower. */
    public Optional<Rect> anchor() {
        return anchors.isEmpty() ? Optional.empty() : Optional.of(anchors.getLast());
    }

    public int timesDrawn() {
        return drawn.size();
    }

    /** Plays a click on a card, as the mouse would — the whole card, not its cross. */
    public void clickCard(String id) {
        actions.open(id);
    }

    /** Plays a click on a card's close cross. */
    public void clickClose(String id) {
        actions.dismiss(id);
    }
}
