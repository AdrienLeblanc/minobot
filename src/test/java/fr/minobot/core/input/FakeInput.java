package fr.minobot.core.input;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A keyboard and a mouse that record instead of acting.
 *
 * <p>The real {@link InputSimulator} needs a {@link java.awt.Robot}: it cannot be constructed
 * headless, and its calls would press keys on the machine running the tests.
 */
public final class FakeInput implements Input {

    private final List<String> actions = new CopyOnWriteArrayList<>();

    /** Every action taken, in order: {@code "key:ENTER"}, {@code "paste:/invite Charlie"}. */
    public List<String> actions() {
        return List.copyOf(actions);
    }

    /** Only the strings pasted, which is what the group invitation is really made of. */
    public List<String> pasted() {
        final var pasted = new ArrayList<String>();
        for (final var action : actions) {
            if (action.startsWith("paste:")) {
                pasted.add(action.substring("paste:".length()));
            }
        }
        return pasted;
    }

    @Override
    public void pressKey(int keyCode) {
        actions.add("key:" + KeyEvent.getKeyText(keyCode));
    }

    @Override
    public void typeString(String text) {
        actions.add("type:" + text);
    }

    @Override
    public void pasteString(String text) {
        actions.add("paste:" + text);
    }

    @Override
    public void click(int x, int y) {
        actions.add("click:" + x + "," + y);
    }
}
