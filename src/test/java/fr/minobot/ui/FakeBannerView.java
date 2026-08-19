package fr.minobot.ui;

import fr.minobot.win32.Rect;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The auto-pass banner, without a screen — the twin of {@link FakeToastView} and {@link FakeOverlayView}.
 *
 * <p>What the controller decides — when the banner shows, when it follows, when it hides, what its one
 * button does — is all visible here, and none of it needs Swing, a monitor, or a game. It keeps the
 * {@link BannerActions} it was built with, so a test can play a press on <em>Turn off</em>.
 */
public final class FakeBannerView implements BannerView {

    private final BannerActions actions;

    private final List<BannerContent> drawn = new CopyOnWriteArrayList<>();
    private final List<Rect> anchors = new CopyOnWriteArrayList<>();

    private volatile boolean visible;

    public FakeBannerView(BannerActions actions) {
        this.actions = actions;
    }

    @Override
    public void show(BannerContent content, Rect anchor) {
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

    /** What the banner was last handed to draw, empty if it was never shown. */
    public Optional<BannerContent> content() {
        return drawn.isEmpty() ? Optional.empty() : Optional.of(drawn.getLast());
    }

    /** Where the banner now hangs — placed by a {@code show}, or moved there by the follower. */
    public Optional<Rect> anchor() {
        return anchors.isEmpty() ? Optional.empty() : Optional.of(anchors.getLast());
    }

    public int timesDrawn() {
        return drawn.size();
    }

    /** Plays a press on the banner's <em>Turn off</em>, as the mouse would. */
    public void clickTurnOff() {
        actions.turnOff();
    }
}
