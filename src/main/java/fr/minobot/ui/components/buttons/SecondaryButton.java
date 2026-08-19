package fr.minobot.ui.components.buttons;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Scale;

/**
 * The quieter action: the keybinds drawer, the sex not chosen — the everyday click that need not
 * shout. A raised tile with a hairline edge, so it reads as a control without spending the ember on one.
 */
public final class SecondaryButton extends FlatButton {

    public SecondaryButton(Scale scale, String text) {
        super(scale, text, Theme.TEXT_SOFT, Theme.RAISED, Theme.HOVER, Theme.EDGE);
    }
}
