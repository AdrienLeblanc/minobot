package fr.minobot.ui.components.buttons;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Scale;

/**
 * The quieter action, in the soft blue: reload, an unbound key, an × that turns one off, the sex not
 * chosen — the everyday click that need not shout. Dark text on {@link Theme#SECONDARY}, the same fill
 * wherever it sits.
 */
public final class SecondaryButton extends FlatButton {

    public SecondaryButton(Scale scale, String text) {
        super(scale, text, Theme.BACKGROUND, Theme.SECONDARY, Theme.SECONDARY_HOVER);
    }
}
