package fr.minobot.core;

import fr.minobot.core.KeyboardMonitor.Binding;
import fr.minobot.win32.FakeWindowApi;
import fr.minobot.win32.Point;
import fr.minobot.win32.Win32;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** The hotkey matching, driven by a fake keyboard — no game and no Windows needed. */
@Timeout(value = 10, unit = TimeUnit.SECONDS)
class KeyboardMonitorTest {

    private static final Duration NO_COOLDOWN = Duration.ZERO;

    private final FakeWindowApi api = new FakeWindowApi();
    private final KeyboardMonitor monitor = new KeyboardMonitor(api);

    @AfterEach
    void stopMonitor() {
        monitor.stop();
    }

    /** Long enough for the 20 ms polling loop to see the key, and for the callback to land. */
    private static void settle() throws InterruptedException {
        Thread.sleep(250);
    }

    @Test
    @DisplayName("a hotkey fires once when the key goes down, not while it is held")
    void firesOnTheKeyDownEdge() throws InterruptedException {
        final var fired = new AtomicInteger();
        monitor.registerHotkey("F8", NO_COOLDOWN, fired::incrementAndGet);
        monitor.start();

        api.press(Win32.functionKey(8));
        settle();
        settle(); // the key is still down: this must not fire it again

        assertThat(fired.get()).isOne();
    }

    @Test
    @DisplayName("the most specific combination wins: SHIFT+X2 must not also fire the plain X2")
    void prefersTheCombinationWithTheMostModifiers() throws InterruptedException {
        final var next = new AtomicInteger();
        final var previous = new AtomicInteger();
        monitor.registerHotkey("x2", NO_COOLDOWN, next::incrementAndGet);
        monitor.registerHotkey("shift+x2", NO_COOLDOWN, previous::incrementAndGet);
        monitor.start();

        api.press(Win32.VK_SHIFT);
        api.press(Win32.VK_XBUTTON2);
        settle();

        assertThat(previous.get()).as("SHIFT+X2 should have cycled backwards").isOne();
        assertThat(next.get()).as("the unmodified X2 must not fire as well").isZero();
    }

    @Test
    @DisplayName("without its modifier, the bare key fires its own handler")
    void firesTheUnmodifiedHandlerWhenNoModifierIsHeld() throws InterruptedException {
        final var next = new AtomicInteger();
        final var previous = new AtomicInteger();
        monitor.registerHotkey("x2", NO_COOLDOWN, next::incrementAndGet);
        monitor.registerHotkey("shift+x2", NO_COOLDOWN, previous::incrementAndGet);
        monitor.start();

        api.press(Win32.VK_XBUTTON2);
        settle();

        assertThat(next.get()).isOne();
        assertThat(previous.get()).isZero();
    }

    @Test
    @DisplayName("a second press within the cooldown is swallowed")
    void honoursTheCooldown() throws InterruptedException {
        final var fired = new AtomicInteger();
        monitor.registerHotkey("F8", Duration.ofSeconds(30), fired::incrementAndGet);
        monitor.start();

        api.press(Win32.functionKey(8));
        settle();
        api.release(Win32.functionKey(8));
        settle();
        api.press(Win32.functionKey(8));
        settle();

        assertThat(fired.get()).isOne();
    }

    @Test
    @DisplayName("the cooldown does not let the press fall through to a less specific handler")
    void doesNotFallThroughToTheBareKeyWhenTheComboIsCoolingDown() throws InterruptedException {
        final var next = new AtomicInteger();
        final var previous = new AtomicInteger();
        monitor.registerHotkey("x2", NO_COOLDOWN, next::incrementAndGet);
        monitor.registerHotkey("shift+x2", Duration.ofSeconds(30), previous::incrementAndGet);
        monitor.start();

        api.press(Win32.VK_SHIFT);
        api.press(Win32.VK_XBUTTON2);
        settle();
        api.release(Win32.VK_XBUTTON2);
        settle();
        api.press(Win32.VK_XBUTTON2); // SHIFT still down, but SHIFT+X2 is cooling down
        settle();

        assertThat(previous.get()).as("the combo fired once and is on cooldown").isOne();
        assertThat(next.get()).as("cycling backwards on cooldown must stay silent, not cycle forwards instead").isZero();
    }

    @Test
    void handsTheCursorPositionToTheCallback() throws InterruptedException {
        final var fired = new CountDownLatch(1);
        final var seen = new AtomicReference<Point>();
        api.withCursor(new Point(640, 480));

        monitor.registerHotkeyWithCursor("x1", NO_COOLDOWN, cursor -> {
            seen.set(cursor);
            fired.countDown();
        });
        monitor.start();

        api.press(Win32.VK_XBUTTON1);
        assertThat(fired.await(5, TimeUnit.SECONDS)).isTrue();

        assertThat(seen.get()).isEqualTo(new Point(640, 480));
    }

    @Test
    @DisplayName("typing is what smart focus watches for — a hotkey press is not typing")
    void tracksTypingActivity() throws InterruptedException {
        monitor.start();
        assertThat(monitor.typedWithin(Duration.ofSeconds(1))).as("nothing has been typed yet").isFalse();

        api.press(Win32.VK_XBUTTON1);
        settle();
        assertThat(monitor.typedWithin(Duration.ofSeconds(1))).as("a mouse button is not typing").isFalse();

        api.press(Win32.VK_A);
        settle();
        assertThat(monitor.typedWithin(Duration.ofSeconds(1))).isTrue();
    }

    @Test
    @DisplayName("an unsupported key in config.json is ignored, not fatal")
    void ignoresAnUnknownHotkey() {
        monitor.registerHotkey("F42", NO_COOLDOWN, () -> {
        });
        monitor.registerHotkey("", NO_COOLDOWN, () -> {
        });
        monitor.start();
    }

    @Nested
    @DisplayName("capturing a key — how the overlay asks 'which one do you want?'")
    class Capturing {

        /** The overlay calls this from its own thread, as the real one will. */
        private CompletableFuture<Optional<String>> capture() {
            return CompletableFuture.supplyAsync(() -> monitor.captureNext(Duration.ofSeconds(5)));
        }

        @Test
        void readsBackThePressedKey() throws Exception {
            monitor.start();
            final var captured = capture();

            settle();
            api.press(Win32.functionKey(7));

            assertThat(captured.get(5, TimeUnit.SECONDS)).contains("F7");
        }

        @Test
        @DisplayName("the modifiers held at that moment come back with it")
        void readsBackTheModifiers() throws Exception {
            monitor.start();
            final var captured = capture();

            settle();
            api.press(Win32.VK_SHIFT);
            api.press(Win32.VK_XBUTTON1);

            assertThat(captured.get(5, TimeUnit.SECONDS)).contains("shift+X1");
        }

        @Test
        @DisplayName("naming a key must not fire what it is currently bound to")
        void firesNoHotkeyWhileCapturing() throws Exception {
            final var fired = new AtomicInteger();
            monitor.registerHotkey("F9", NO_COOLDOWN, fired::incrementAndGet);
            monitor.start();

            final var captured = capture();
            settle();
            api.press(Win32.functionKey(9)); // F9 rebuilds the taskbar — but here it is only being named

            assertThat(captured.get(5, TimeUnit.SECONDS)).contains("F9");
            settle();
            assertThat(fired.get()).isZero();
        }

        @Test
        @DisplayName("a hotkey held throughout the capture is not a fresh press once it closes")
        void firesNoHotkeyOnTheKeyStillHeldWhenTheCaptureCloses() throws Exception {
            final var fired = new AtomicInteger();
            monitor.registerHotkey("F9", NO_COOLDOWN, fired::incrementAndGet);
            monitor.start();

            final var captured = capture();
            settle();
            api.press(Win32.functionKey(9));
            assertThat(captured.get(5, TimeUnit.SECONDS)).isPresent();

            // The capture is over, and the player has not let go yet.
            settle();

            assertThat(fired.get()).isZero();
        }

        @Test
        @DisplayName("a key already down when the capture opens is not the answer: the click is still travelling")
        void ignoresAKeyAlreadyHeldWhenItOpens() throws Exception {
            monitor.start();
            api.press(Win32.VK_XBUTTON1);
            settle();

            final var captured = capture();
            settle();
            api.release(Win32.VK_XBUTTON1);
            settle();
            api.press(Win32.functionKey(7));

            assertThat(captured.get(5, TimeUnit.SECONDS)).contains("F7");
        }

        @Test
        @DisplayName("pressing nothing gives up on its own, and the hotkeys come back")
        void givesUpWhenNothingIsPressed() throws Exception {
            final var fired = new AtomicInteger();
            monitor.registerHotkey("F8", NO_COOLDOWN, fired::incrementAndGet);
            monitor.start();

            assertThat(monitor.captureNext(Duration.ofMillis(100))).isEmpty();

            api.press(Win32.functionKey(8));
            settle();
            assertThat(fired.get()).as("the hotkeys are live again").isOne();
        }
    }

    @Nested
    @DisplayName("rebinding, while the loop is running — how the overlay edits a keybind")
    class Rebinding {

        @Test
        @DisplayName("the new key fires, and the key it replaced falls silent")
        void replacesEveryHotkeyAtOnce() throws InterruptedException {
            final var before = new AtomicInteger();
            final var after = new AtomicInteger();
            monitor.registerHotkey("F8", NO_COOLDOWN, before::incrementAndGet);
            monitor.start();

            monitor.rebind(List.of(Binding.of("F7", NO_COOLDOWN, after::incrementAndGet)));

            api.press(Win32.functionKey(8));
            settle();
            assertThat(before.get()).as("F8 was rebound away: it must do nothing now").isZero();

            api.press(Win32.functionKey(7));
            settle();
            assertThat(after.get()).isOne();
        }

        @Test
        @DisplayName("a key still held when it is bound is not a press: the capture dialog leaves it down")
        void doesNotFireAKeyAlreadyHeldWhenItIsBound() throws InterruptedException {
            final var fired = new AtomicInteger();
            monitor.start();

            // The player is naming their new hotkey: the key is down when the rebind lands.
            api.press(Win32.functionKey(7));
            settle();
            monitor.rebind(List.of(Binding.of("F7", NO_COOLDOWN, fired::incrementAndGet)));
            settle();

            assertThat(fired.get())
                    .as("there was no edge — binding a key must not fire the feature it was bound to")
                    .isZero();

            api.release(Win32.functionKey(7));
            settle();
            api.press(Win32.functionKey(7));
            settle();

            assertThat(fired.get()).as("the next real press does fire it").isOne();
        }

        @Test
        @DisplayName("a blank hotkey unbinds the feature rather than crashing the rebind")
        void unbindsAFeatureGivenABlankHotkey() throws InterruptedException {
            final var fired = new AtomicInteger();
            monitor.registerHotkey("F8", NO_COOLDOWN, fired::incrementAndGet);
            monitor.start();

            monitor.rebind(List.of(Binding.of("", NO_COOLDOWN, fired::incrementAndGet)));

            api.press(Win32.functionKey(8));
            settle();

            assertThat(fired.get()).isZero();
        }
    }
}
