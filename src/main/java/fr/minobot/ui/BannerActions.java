package fr.minobot.ui;

/**
 * What the player can ask for through the auto-pass banner — the mirror of {@link ToastActions}, and just
 * as narrow. Implemented by the controller, so the view itself never touches the game, the focus or the
 * switch.
 *
 * <p>There is one thing to do, and it runs on whichever thread the view calls it from — Swing's — without
 * blocking.
 */
public interface BannerActions {

    /**
     * The player pressed the banner's <em>Turn off</em>: stop passing turns.
     *
     * <p>This <strong>switches the feature off</strong>, and does not merely hide the banner. The banner
     * is the only thing on screen while auto-pass is running, so its one button is the only place a
     * player who wants it to stop is looking — a button there that hid the sign while the turns went on
     * ending would be the worst thing this panel could do. The banner then goes because the switch went,
     * not the other way round.
     */
    void turnOff();
}
