package fr.minobot.win32;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Exercises the FFM binding against the live {@code user32.dll}.
 *
 * <p>These assert on the shape of what Windows answers, not on any particular window being open, so
 * they hold whatever is running on the machine. They are the only thing standing between a
 * mis-declared {@code FunctionDescriptor} and a crash at runtime — a wrong signature does not fail
 * to compile, it corrupts the stack.
 */
@EnabledOnOs(OS.WINDOWS)
class User32Test {

    private final WindowApi api = User32.instance();

    /** A visible, titled, non-minimized window — whatever the machine happens to be showing. */
    private long anyVisibleWindow() {
        return api.topLevelWindows().stream()
                .filter(api::isWindowVisible)
                .filter(hwnd -> !api.isIconic(hwnd))
                .filter(hwnd -> !api.windowText(hwnd).isBlank())
                .findFirst()
                .orElse(Win32.NULL_HANDLE);
    }

    @Test
    @DisplayName("EnumWindows walks the desktop through an FFM upcall")
    void enumeratesTopLevelWindows() {
        final var windows = api.topLevelWindows();

        assertThat(windows).as("a Windows desktop always has top-level windows").isNotEmpty();
        assertThat(windows.stream().anyMatch(hwnd -> api.isWindowVisible(hwnd) && !api.windowText(hwnd).isBlank())).as("at least one visible window should have a title").isTrue();
    }

    @Test
    @DisplayName("a title comes back decoded, not truncated at the first NUL byte")
    void readsWindowTitles() {
        final var hwnd = anyVisibleWindow();
        assumeTrue(hwnd != Win32.NULL_HANDLE, "no visible titled window on this desktop");

        final var title = api.windowText(hwnd);

        assertThat(title)
                .isNotBlank()
                .doesNotContain("\0");
    }

    @Test
    @DisplayName("the null handle is answered, not crashed on")
    void toleratesTheNullHandle() {
        assertThat(api.isWindow(Win32.NULL_HANDLE)).isFalse();
        assertThat(api.isWindowVisible(Win32.NULL_HANDLE)).isFalse();
        assertThat(api.windowText(Win32.NULL_HANDLE)).isEmpty();
    }

    @Test
    void reportsTheForegroundWindow() {
        long hwnd = api.foregroundWindow();
        assumeTrue(hwnd != Win32.NULL_HANDLE, "no window holds the focus (headless session?)");

        assertThat(api.isWindow(hwnd)).isTrue();
    }

    @Test
    @DisplayName("ScreenToClient and ClientToScreen are inverses of one another")
    void convertsCoordinatesBothWays() {
        long hwnd = anyVisibleWindow();
        assumeTrue(hwnd != Win32.NULL_HANDLE, "no visible titled window on this desktop");

        final var screen = new Point(300, 400);
        final var client = api.screenToClient(hwnd, screen);
        assumeTrue(client.isPresent(), "the window vanished mid-test");

        assertThat(api.clientToScreen(hwnd, client.get())).contains(screen);
    }

    @Test
    @DisplayName("a client coordinate outside the window comes back negative, and survives the trip")
    void convertsCoordinatesOutsideTheClientArea() {
        final var hwnd = anyVisibleWindow();
        assumeTrue(hwnd != Win32.NULL_HANDLE, "no visible titled window on this desktop");

        // The far top-left of the virtual desktop is above and left of any normal window.
        final var screen = new Point(-4000, -4000);
        final var client = api.screenToClient(hwnd, screen);
        assumeTrue(client.isPresent());

        assertThat(api.clientToScreen(hwnd, client.get())).as("the round trip must hold for the negative coordinates that makeLParam has to pack").contains(screen);
    }

    @Test
    @DisplayName("WindowFromPoint takes its POINT by value — 8 bytes in a register on Win64")
    void findsTheWindowUnderTheCursor() {
        final var cursor = api.cursorPosition();
        assumeTrue(cursor.isPresent(), "no cursor (headless session?)");

        final var hwnd = api.windowFromPoint(cursor.get());
        assumeTrue(hwnd != Win32.NULL_HANDLE, "the cursor sits over no window");

        assertThat(api.isWindow(hwnd)).isTrue();
    }

    @Test
    void walksUpToTheTopLevelWindow() {
        final var hwnd = anyVisibleWindow();
        assumeTrue(hwnd != Win32.NULL_HANDLE, "no visible titled window on this desktop");

        assertThat(api.parentWindow(hwnd)).as("a top-level window has no parent").isZero();
    }

    @Test
    void locatesTheMonitorOfAWindow() {
        final var hwnd = anyVisibleWindow();
        assumeTrue(hwnd != Win32.NULL_HANDLE, "no visible titled window on this desktop");

        final var monitor = api.monitorFromWindow(hwnd);

        assertThat(monitor).as("MONITOR_DEFAULTTONEAREST always yields a monitor").isNotZero();
        assertThat(api.monitorFromWindow(hwnd)).as("the same window must map to the same monitor").isEqualTo(monitor);
    }

    @Test
    @DisplayName("polling a key that no keyboard has reports it as up")
    void pollsKeyState() {
        final var vkF13 = 0x7C;

        assertThat(api.isKeyDown(vkF13)).isFalse();
    }

    @Test
    @DisplayName("FlashWindowEx accepts the FLASHWINFO struct we lay out")
    void stopsTaskbarFlashing() {
        final var hwnd = anyVisibleWindow();
        assumeTrue(hwnd != Win32.NULL_HANDLE, "no visible titled window on this desktop");

        // A bad cbSize or a mis-aligned hwnd field would corrupt the call, not merely do nothing.
        assertDoesNotThrow(() -> api.stopFlashing(hwnd));
    }

    @Test
    @DisplayName("SystemParametersInfo reads back the taskbar flash count of this session")
    void readsTheForegroundFlashCount() {
        // Read-only: the write is exercised by FlashSuppressorTest, on the fake — setting it here
        // would change the session of whoever runs the tests. A wrong FFM signature would throw, and
        // a pvParam Windows would not write into comes back empty.
        assertThat(api.foregroundFlashCount()).as("Windows always has a flash count").isPresent();
    }
}
