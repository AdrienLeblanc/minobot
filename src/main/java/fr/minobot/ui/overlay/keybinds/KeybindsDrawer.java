package fr.minobot.ui.overlay.keybinds;

import fr.minobot.app.Feature;
import fr.minobot.ui.OverlayActions;
import fr.minobot.ui.OverlayContent;
import fr.minobot.ui.Theme;
import fr.minobot.ui.components.buttons.PrimaryButton;
import fr.minobot.ui.components.buttons.SecondaryButton;
import fr.minobot.ui.components.buttons.TertiaryButton;
import fr.minobot.ui.components.containers.Card;
import fr.minobot.ui.components.labels.Hint;
import fr.minobot.ui.components.labels.SectionHeader;
import fr.minobot.ui.utils.Metrics;
import fr.minobot.ui.utils.Scale;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Every feature's key, in a card of its own beside the panel.
 *
 * <p>They are a list of rows the player edits once and then forgets, and inline they made the panel twice
 * as tall as what it is actually for. Folded away, they cost the card nothing; unfolded, they cost it no
 * room either — the drawer opens beside it, not inside it. The orchestrator owns whether it is open.
 */
public final class KeybindsDrawer {

    /** What a feature with no key shows, and what the player clicks to give it one back. */
    private static final String UNBOUND = "—";

    private final OverlayActions actions;

    /** Redraws the panel after a capture that came to nothing — nothing else would, and the button is
     * still saying "press…". */
    private final Runnable redraw;

    private Scale scale;

    public KeybindsDrawer(OverlayActions actions, Runnable redraw) {
        this.actions = actions;
        this.redraw = redraw;
    }

    public JPanel build(Scale scale, OverlayContent content) {
        this.scale = scale;

        // The lighter surface, so the drawer reads as a card set on the panel rather than part of it.
        final var drawer = Card.column(scale, Theme.SURFACE);
        drawer.setBorder(new EmptyBorder(scale.px(Metrics.PADDING), scale.px(Metrics.PADDING),
                scale.px(Metrics.PADDING), scale.px(Metrics.PADDING)));

        final var rows = Card.plainColumn();
        for (final var feature : Feature.values()) {
            rows.add(hotkeyRow(feature, content.hotkeys().getOrDefault(feature, "")));
        }

        drawer.add(new SectionHeader(scale, "Keybinds", null));
        drawer.add(rows);
        drawer.add(Box.createVerticalStrut(scale.px(Metrics.GAP)));
        drawer.add(new Hint(scale, "click a key to change it, × to turn it off"));
        return drawer;
    }

    private JPanel hotkeyRow(Feature feature, String hotkey) {
        final var row = new JPanel(new BorderLayout(scale.px(Metrics.GAP), 0));
        row.setOpaque(false);
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, scale.px(Metrics.ROW)));

        final var name = new JLabel(feature.label());
        name.setForeground(hotkey.isBlank() ? Theme.MUTED : Theme.TEXT);
        name.setFont(scale.font(Metrics.SMALL, Metrics.PLAIN));

        // A bound key is the emphasised one; an unbound key is the quiet invitation to give it one.
        final JButton  key = hotkey.isBlank()
                ? new SecondaryButton(scale, UNBOUND)
                : new TertiaryButton(scale, hotkey);
        key.addActionListener(_ -> capture(feature, key));

        final var clear = new SecondaryButton(scale, "×");
        clear.setEnabled(!hotkey.isBlank());
        clear.addActionListener(_ -> actions.rebind(feature, ""));

        final var buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, scale.px(4), 0));
        buttons.setOpaque(false);
        buttons.add(key);
        buttons.add(clear);

        row.add(name, BorderLayout.WEST);
        row.add(buttons, BorderLayout.EAST);
        return row;
    }

    /**
     * Waits for the player to press the key they want — <strong>off the event dispatch thread</strong>.
     *
     * <p>The wait is for a human, so it is seconds long. On the EDT it would freeze the very panel the
     * player is pressing keys at, and Swing would repaint nothing until they were done.
     */
    private void capture(Feature feature, JButton key) {
        key.setText("press…");
        key.setEnabled(false);

        Thread.ofVirtual().name("overlay-capture").start(() -> {
            final var captured = actions.captureHotkey();

            SwingUtilities.invokeLater(() -> captured.ifPresentOrElse(
                    combination -> actions.rebind(feature, combination),
                    // Nothing was pressed. The configuration did not change, so nothing will redraw the
                    // panel for us — and the button is still saying "press…".
                    redraw));
        });
    }
}
