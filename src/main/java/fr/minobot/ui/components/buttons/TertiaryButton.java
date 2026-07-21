package fr.minobot.ui.components.buttons;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Scale;

/**
 * The quieter action, in the soft blue: reload, an unbound key, an × that turns one off, the sex not
 * chosen — the everyday click that need not shout. Dark text on {@link Theme#SECONDARY}, the same fill
 * wherever it sits.
 */
public final class TertiaryButton extends FlatButton {

    public TertiaryButton(Scale scale, String text) {
        super(scale, text, Theme.BACKGROUND, Theme.TERTIARY, Theme.TERTIARY_HOVER);
    }
}
