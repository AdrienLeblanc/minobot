package fr.minobot.ui.components.buttons;

import fr.minobot.ui.Theme;
import fr.minobot.ui.utils.Scale;

/**
 * The panel's strongest action, in the deep blue: a chosen sex, a bound key, the drawer held open — the
 * thing that is <em>on</em> or picked. Light text on {@link Theme#PRIMARY}, the same fill wherever it sits.
 */
public final class PrimaryButton extends FlatButton {

    public PrimaryButton(Scale scale, String text) {
        super(scale, text, Theme.TEXT, Theme.PRIMARY, Theme.PRIMARY_HOVER);
    }
}
