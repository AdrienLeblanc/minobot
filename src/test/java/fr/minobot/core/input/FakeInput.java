package fr.minobot.core.input;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A keyboard and a mouse that record instead of acting.
 *
 * <p>The real {@link InputSimulator} needs a {@link java.awt.Robot}: it cannot be constructed
 * headless, and its calls would press keys on the machine running the tests.
 *
 * <p>Each action is recorded with the window that held the foreground when it was taken — because
 * for anything that types, <em>what</em> was typed is only half the question. A command sent to the
 * wrong window is the way the group invitation relay breaks.
 */
public final class FakeInput implements Input {

    private final List<Action> actions = new CopyOnWriteArrayList<>();
    private final LongSupplier foreground;

    /** Which pastes fail, standing in for a clipboard another application raced us for. */
    private volatile Predicate<String> pasteFails = text -> false;

    /** @param foreground the window an action lands in, i.e. {@code FakeWindowApi::foregroundWindow} */
    public FakeInput(LongSupplier foreground) {
        this.foreground = foreground;
    }

    /** Makes any paste of {@code text} return {@code false}, as if the clipboard had been clobbered. */
    public void failPasteOf(String text) {
        this.pasteFails = text::equals;
    }

    /** Every action taken, in order: {@code "key:ENTER"}, {@code "paste:/invite Charlie"}. */
    public List<String> actions() {
        return actions.stream().map(Action::action).toList();
    }

    /** Only the strings pasted, which is what the group invitation is really made of. */
    public List<String> pasted() {
        return pastes().map(Action::action)
                .map(action -> action.substring("paste:".length()))
                .toList();
    }

    /** The window each pasted string landed in, in the same order — {@code 0} for none. */
    public List<Long> pastedInto() {
        return pastes().map(Action::window).toList();
    }

    private Stream<Action> pastes() {
        return actions.stream().filter(action -> action.action().startsWith("paste:"));
    }

    @Override
    public void pressKey(int keyCode) {
        record("key:" + KeyEvent.getKeyText(keyCode));
    }

    @Override
    public void typeString(String text) {
        record("type:" + text);
    }

    @Override
    public boolean pasteString(String text) {
        if (pasteFails.test(text)) {
            return false; // clobbered: nothing reached the clipboard, so nothing was pasted
        }
        record("paste:" + text);
        return true;
    }

    @Override
    public void click(int x, int y) {
        record("click:" + x + "," + y);
    }

    private void record(String action) {
        actions.add(new Action(action, foreground.getAsLong()));
    }

    private record Action(String action, long window) {
    }
}
