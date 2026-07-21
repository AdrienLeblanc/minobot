package fr.minobot.ui.overlay.preferences;

import fr.minobot.ui.OverlayActions;
import fr.minobot.ui.OverlayContent;
import fr.minobot.ui.components.buttons.Toggle;
import fr.minobot.ui.components.labels.Hint;
import fr.minobot.ui.components.labels.SectionHeader;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import javax.swing.*;

/**
 * The two switches that act on the game for the player: <em>Auto-pass turns</em> and
 * <em>Auto-accept trades</em>. They are the panel's <em>states</em> rather than keys — an explicit ON/OFF
 * pill, said in a word and a colour both, because a control that quietly ends every combat turn or accepts
 * a trade has to be unmistakable.
 *
 * <p>It {@link #addTo adds its rows straight to the card} rather than returning them wrapped: the switches
 * are siblings of everything else on the card, and it is the card's own column that stretches each
 * heading full width and right-aligns its switch.
 */
public final class Preferences {

    private final OverlayActions actions;

    public Preferences(OverlayActions actions) {
        this.actions = actions;
    }

    /** Adds the two switch sections — heading, switch and one-line hint each — to the given card. */
    public void addTo(JPanel card, Scale scale, OverlayContent content) {
        card.add(Box.createVerticalStrut(scale.px(Metrics.PADDING)));
        final var autoPass = content.autoPassTurn();
        card.add(new SectionHeader(scale, "Auto-pass turns",
                Toggle.control(scale, autoPass, actions::toggleAutoPassTurn)));
        card.add(new Hint(scale, autoPass
                ? "on — every character ends its own turn"
                : "off — turns are yours to play"));

        card.add(Box.createVerticalStrut(scale.px(Metrics.GAP)));
        final var autoAccept = content.autoAcceptTrade();
        card.add(new SectionHeader(scale, "Auto-accept trades",
                Toggle.control(scale, autoAccept, actions::toggleAutoAcceptTrade)));
        card.add(new Hint(scale, autoAccept
                ? "on — my characters' trades accept themselves"
                : "off"));
    }
}
