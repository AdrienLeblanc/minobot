package fr.minobot.ui.components.buttons;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Scale;

/**
 * The panel's strongest action, in the ember: the sex that is chosen, the switch that turns a running
 * feature off. Light text on {@link Theme#ACCENT}, the same fill wherever it sits.
 *
 * <p>There is one of these on a card at a time, or the accent stops meaning anything — see {@link Theme}.
 */
public final class PrimaryButton extends FlatButton {

    public PrimaryButton(Scale scale, String text) {
        super(scale, text, Theme.TEXT, Theme.ACCENT, Theme.ACCENT_HOVER, null);
    }
}
