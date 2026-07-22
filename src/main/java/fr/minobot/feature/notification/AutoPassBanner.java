package fr.minobot.feature.notification;

import fr.minobot.app.Config;
import fr.minobot.app.Settings;
import fr.minobot.core.WindowManager;
import fr.minobot.ui.BannerActions;
import fr.minobot.ui.BannerContent;
import fr.minobot.ui.BannerView;
import fr.minobot.win32.Rect;
import fr.minobot.win32.WindowApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Shows a standing banner over the game while the turn-passer is on — the visible half of a feature that
 * would otherwise leave no sign it is running but the turns ending by themselves.
 *
 * <p>It answers no toast, unlike its neighbours here: {@link TurnPasser} is what ends the turns, reading
 * the {@code auto_pass_turn} switch at each one. This class watches that same switch — through
 * {@link Settings#onChange} — and, while it is on, hangs a card at the top of the foreground game reading
 * that auto-pass is enabled. The card carries a close cross, but the cross <strong>only hides</strong> it:
 * stopping the feature is the overlay switch's or the hotkey's job. Once hidden it stays hidden until the
 * switch is turned off and on again — a deliberate re-enable brings it back.
 *
 * <p>Like the panel and the whisper stack it <em>follows</em>: a single virtual thread keeps the banner
 * pinned to the foreground game window, or takes it away while the player is out of the game. The loop
 * runs only while the banner is wanted (the switch on and the card not dismissed); virtual threads are
 * daemons, so a banner still up at shutdown holds nothing up.
 *
 * <p>Its UI surface is cut on the same interface/impl seam as the overlay and the whisper stack
 * ({@link BannerView} and {@link BannerActions}), so all the deciding stays testable with no screen.
 */
public final class AutoPassBanner implements BannerActions {

    private static final Logger log = LoggerFactory.getLogger(AutoPassBanner.class);

    /** What the banner says. UI text, so it is the controller's — the view draws what it is handed. */
    static final String MESSAGE = "Auto-pass turn enabled.";

    /** How often the banner checks it is still on the foreground game window. */
    private static final Duration FOLLOW_INTERVAL = Duration.ofMillis(30);

    private final WindowApi api;
    private final WindowManager windows;
    private final Settings settings;
    private final BannerView view;

    /** The switch as this last saw it, so a change listener can tell an off→on edge from any other edit. */
    private volatile boolean enabled;

    /** Whether the player closed the card this time round — reset when the switch is turned on afresh. */
    private volatile boolean dismissed;

    /** Whether the follow loop is running — one at a time, whoever started it. */
    private final AtomicBoolean following = new AtomicBoolean();

    public AutoPassBanner(WindowApi api, WindowManager windows, Settings settings,
                          Function<BannerActions, BannerView> viewFactory) {
        this.api = api;
        this.windows = windows;
        this.settings = settings;
        this.view = viewFactory.apply(this);

        settings.onChange(this::onChange);
    }

    /**
     * Watches the switch. Runs on whichever thread made the change, for every change — so it does only
     * what the edge demands: on an off→on it clears any earlier dismissal and starts following; on an
     * on→off the follow loop sees {@link #enabled} drop and takes the banner down itself.
     */
    void onChange(Config config) {
        final var was = enabled;
        enabled = config.autoPassTurn();
        if (enabled && !was) {
            dismissed = false;
            startFollowing();
        }
    }

    /**
     * The player clicked the close cross. This only hides the banner — {@link TurnPasser} keeps passing
     * turns. The follow loop sees {@link #dismissed} and steps out until the switch is turned on again.
     */
    @Override
    public void dismiss() {
        dismissed = true;
    }

    private void startFollowing() {
        if (following.compareAndSet(false, true)) {
            Thread.ofVirtual().name("auto-pass-banner-follow").start(this::follow);
        }
    }

    /**
     * Keeps the banner pinned to the foreground game window while it is wanted, and takes it down when it
     * is not. A thread of its own, exactly as the panel's and the whisper stack's followers are, and for
     * the same reason: Windows will not say a window moved without a message hook.
     */
    private void follow() {
        BannerContent lastContent = null;
        Rect lastAnchor = null;
        try {
            while (enabled && !dismissed) {
                final var area = foregroundGameArea();
                if (area.isEmpty()) {
                    // Out of the game, or minimized: the banner waits offscreen for the player to return.
                    if (view.isVisible()) {
                        view.hide();
                        lastContent = null;
                        lastAnchor = null;
                    }
                } else {
                    final var content = content();
                    if (!view.isVisible() || !content.equals(lastContent)) {
                        view.show(content, area.get());
                    } else if (!area.get().equals(lastAnchor)) {
                        view.moveTo(area.get());
                    }
                    lastContent = content;
                    lastAnchor = area.get();
                }

                if (!sleep()) {
                    return;
                }
            }
        } finally {
            following.set(false);
            if (view.isVisible()) {
                view.hide();
            }
            // A re-enable may have landed between the loop's guard and here: do not strand the banner.
            // startFollowing uses a CAS, so if that re-enable already restarted the loop this is a no-op.
            if (enabled && !dismissed) {
                startFollowing();
            }
        }
    }

    /** The foreground game's client area to hang the banner on — empty when it is not the game, or minimized. */
    private Optional<Rect> foregroundGameArea() {
        return windows.foregroundGameWindow()
                .flatMap(window -> api.isIconic(window.hwnd()) ? Optional.empty() : api.clientArea(window.hwnd()));
    }

    /** What the banner shows, read fresh: the message, at the scale the panel is drawn at. */
    private BannerContent content() {
        return new BannerContent(settings.get().overlayScale(), MESSAGE);
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
}
