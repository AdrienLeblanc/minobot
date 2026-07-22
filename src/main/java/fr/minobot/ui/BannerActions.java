package fr.minobot.ui;

/**
 * What the player can ask for through the auto-pass banner — the mirror of {@link ToastActions}, and just
 * as narrow. Implemented by the controller, so the view itself never touches the game, the focus or the
 * switch.
 *
 * <p>There is one thing to do: take the banner down. It runs on whichever thread the view calls it from —
 * Swing's — and does not block.
 */
public interface BannerActions {

    /**
     * The player clicked the banner's close cross: take it down. This <strong>only hides</strong> the
     * banner — the turn-passer keeps running. Stopping the feature is the overlay switch's or the hotkey's
     * job, never the cross's.
     */
    void dismiss();
}
