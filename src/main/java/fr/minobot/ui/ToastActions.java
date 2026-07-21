package fr.minobot.ui;

/**
 * What the player can ask for through the whisper stack — the mirror of {@link OverlayActions}, and
 * just as narrow. Implemented by the controller, so the view itself never touches the game or the
 * focus.
 *
 * <p>There is nothing to type here: a whisper is read, gone to, or dismissed, all with the mouse. Both
 * calls run on whichever thread the view calls them from — Swing's — and neither blocks: {@link #open}
 * hands the focus off to a thread of its own, because the focus sequence sleeps.
 */
public interface ToastActions {

    /**
     * The player clicked a card: bring its receiver to the foreground so they can answer, and take the
     * card down — it has been read, and it now sits over the very window it named.
     */
    void open(String id);

    /** The player clicked a card's close cross: take it down, and let the ones above it settle. */
    void dismiss(String id);
}
