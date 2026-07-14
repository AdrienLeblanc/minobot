package fr.minobot.win32;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The real {@link WindowApi}: {@code user32.dll} called through the FFM API
 * ({@code java.lang.foreign}), with no JNI and no third-party library.
 *
 * <p>Requires {@code --enable-native-access=ALL-UNNAMED}; the executable jar carries it as the
 * {@code Enable-Native-Access} manifest entry, so {@code java -jar} needs no flag.
 *
 * <p>{@code HWND}, {@code LPARAM} and {@code WPARAM} are modelled as {@link ValueLayout#JAVA_LONG}
 * rather than {@code ADDRESS}: on the Win64 ABI they are 64-bit values passed in a register.
 * {@code ADDRESS} is reserved for the buffers we actually hand out.
 */
public final class User32 implements WindowApi {

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = SymbolLookup.libraryLookup("user32.dll", Arena.global());

    /** {@code POINT { LONG x; LONG y; }} — 8 bytes, so the Win64 ABI passes it by value in a register. */
    private static final MemoryLayout POINT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("x"),
            ValueLayout.JAVA_INT.withName("y"));

    /** {@code RECT { LONG left; LONG top; LONG right; LONG bottom; }} — written in place by the callee. */
    private static final MemoryLayout RECT = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("left"),
            ValueLayout.JAVA_INT.withName("top"),
            ValueLayout.JAVA_INT.withName("right"),
            ValueLayout.JAVA_INT.withName("bottom"));

    /** {@code FLASHWINFO { UINT cbSize; HWND hwnd; DWORD dwFlags; UINT uCount; DWORD dwTimeout; }} */
    private static final MemoryLayout FLASHWINFO = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("cbSize"),
            MemoryLayout.paddingLayout(4),          // hwnd must land on an 8-byte boundary
            ValueLayout.JAVA_LONG.withName("hwnd"),
            ValueLayout.JAVA_INT.withName("dwFlags"),
            ValueLayout.JAVA_INT.withName("uCount"),
            ValueLayout.JAVA_INT.withName("dwTimeout"),
            MemoryLayout.paddingLayout(4));         // tail padding to the struct's 8-byte alignment

    /**
     * {@code fWinIni} of {@code SystemParametersInfo}: neither write the setting to the user's
     * profile, nor broadcast it.
     *
     * <p>The change then lives in the session and dies with it — so a Minobot killed outright leaves
     * nothing behind that a logout does not undo.
     */
    private static final int NO_PERSIST = 0;

    /** {@code WNDENUMPROC: BOOL CALLBACK(HWND, LPARAM)} */
    private static final FunctionDescriptor ENUM_PROC = FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG);

    private static final MethodHandle EnumWindows = downcall("EnumWindows",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final MethodHandle GetWindowTextW = downcall("GetWindowTextW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle IsWindowVisible = downcall("IsWindowVisible",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle IsIconic = downcall("IsIconic",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle IsWindow = downcall("IsWindow",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle ShowWindow = downcall("ShowWindow",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle SetForegroundWindow = downcall("SetForegroundWindow",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle BringWindowToTop = downcall("BringWindowToTop",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG));
    private static final MethodHandle GetForegroundWindow = downcall("GetForegroundWindow",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG));
    private static final MethodHandle PostMessageW = downcall("PostMessageW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle ScreenToClient = downcall("ScreenToClient",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle ClientToScreen = downcall("ClientToScreen",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle GetClientRect = downcall("GetClientRect",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_LONG, ValueLayout.ADDRESS));
    private static final MethodHandle WindowFromPoint = downcall("WindowFromPoint",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, POINT));
    private static final MethodHandle GetParent = downcall("GetParent",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle GetAsyncKeyState = downcall("GetAsyncKeyState",
            FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ValueLayout.JAVA_INT));
    private static final MethodHandle GetCursorPos = downcall("GetCursorPos",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle MonitorFromWindow = downcall("MonitorFromWindow",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));
    private static final MethodHandle FlashWindowEx = downcall("FlashWindowEx",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    /**
     * {@code BOOL SystemParametersInfoW(UINT uiAction, UINT uiParam, PVOID pvParam, UINT fWinIni)}.
     *
     * <p>Two handles on one symbol, because {@code pvParam} is a pointer when reading a setting and
     * the value itself, cast to a pointer, when writing one. On the Win64 ABI both travel in the same
     * register, but the FFM signature has to say which it is.
     */
    private static final MethodHandle SystemParametersInfoRead = downcall("SystemParametersInfoW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle SystemParametersInfoWrite = downcall("SystemParametersInfoW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_INT));

    private static final User32 INSTANCE = new User32();

    private User32() {
    }

    public static User32 instance() {
        return INSTANCE;
    }

    private static MethodHandle downcall(String name, FunctionDescriptor descriptor) {
        final var symbol = LOOKUP.find(name)
                .orElseThrow(() -> new IllegalStateException("user32.dll exports no " + name));
        return LINKER.downcallHandle(symbol, descriptor);
    }

    // ------------------------------------------------------------------ enumeration

    @Override
    public List<Long> topLevelWindows() {
        final var collector = new Collector();
        try (final var arena = Arena.ofConfined()) {
            final var target = MethodHandles.lookup()
                    .findVirtual(Collector.class, "onWindow", MethodType.methodType(int.class, long.class, long.class))
                    .bindTo(collector);
            final var callback = LINKER.upcallStub(target, ENUM_PROC, arena);
            final var ignored = (int) EnumWindows.invokeExact(callback, 0L);
        } catch (Throwable t) {
            throw failure("EnumWindows", t);
        }
        return collector.hwnds;
    }

    /**
     * Holds the handles the upcall feeds back. One per enumeration, so concurrent callers never
     * share it — and the callback never throws, since an exception crossing back into native code
     * would take the process down.
     */
    private static final class Collector {
        private final List<Long> hwnds = new ArrayList<>();

        @SuppressWarnings("unused") // called from native code through an upcall stub
        int onWindow(long hwnd, long lparam) {
            hwnds.add(hwnd);
            return 1; // keep enumerating
        }
    }

    @Override
    public String windowText(long hwnd) {
        try (final var arena = Arena.ofConfined()) {
            final var capacity = 512;
            final var buffer = arena.allocate(capacity * 2L); // WCHAR
            final var length = (int) GetWindowTextW.invokeExact(hwnd, buffer, capacity);
            if (length <= 0) {
                return "";
            }
            // Decode exactly the returned length: the buffer's NUL terminator is not to be trusted.
            final var utf16 = buffer.asSlice(0, length * 2L).toArray(ValueLayout.JAVA_BYTE);
            return new String(utf16, StandardCharsets.UTF_16LE);
        } catch (Throwable t) {
            throw failure("GetWindowTextW", t);
        }
    }

    // ------------------------------------------------------------------ state

    @Override
    public boolean isWindowVisible(long hwnd) {
        try {
            return (int) IsWindowVisible.invokeExact(hwnd) != 0;
        } catch (Throwable t) {
            throw failure("IsWindowVisible", t);
        }
    }

    @Override
    public boolean isIconic(long hwnd) {
        try {
            return (int) IsIconic.invokeExact(hwnd) != 0;
        } catch (Throwable t) {
            throw failure("IsIconic", t);
        }
    }

    @Override
    public boolean isWindow(long hwnd) {
        try {
            return (int) IsWindow.invokeExact(hwnd) != 0;
        } catch (Throwable t) {
            throw failure("IsWindow", t);
        }
    }

    // ------------------------------------------------------------------ focus

    @Override
    public void showWindow(long hwnd, int command) {
        try {
            final var ignored = (int) ShowWindow.invokeExact(hwnd, command);
        } catch (Throwable t) {
            throw failure("ShowWindow", t);
        }
    }

    @Override
    public boolean setForegroundWindow(long hwnd) {
        try {
            return (int) SetForegroundWindow.invokeExact(hwnd) != 0;
        } catch (Throwable t) {
            throw failure("SetForegroundWindow", t);
        }
    }

    @Override
    public void bringWindowToTop(long hwnd) {
        try {
            final var ignored = (int) BringWindowToTop.invokeExact(hwnd);
        } catch (Throwable t) {
            throw failure("BringWindowToTop", t);
        }
    }

    @Override
    public long foregroundWindow() {
        try {
            return (long) GetForegroundWindow.invokeExact();
        } catch (Throwable t) {
            throw failure("GetForegroundWindow", t);
        }
    }

    // ------------------------------------------------------------------ messages

    @Override
    public boolean postMessage(long hwnd, int message, long wparam, long lparam) {
        try {
            return (int) PostMessageW.invokeExact(hwnd, message, wparam, lparam) != 0;
        } catch (Throwable t) {
            throw failure("PostMessageW", t);
        }
    }

    // ------------------------------------------------------------------ coordinates

    @Override
    public Optional<Point> screenToClient(long hwnd, Point screen) {
        return mapPoint(ScreenToClient, "ScreenToClient", hwnd, screen);
    }

    @Override
    public Optional<Point> clientToScreen(long hwnd, Point client) {
        return mapPoint(ClientToScreen, "ClientToScreen", hwnd, client);
    }

    /** Both conversions take a POINT by reference and rewrite it in place. */
    private static Optional<Point> mapPoint(MethodHandle handle, String name, long hwnd, Point point) {
        try (final var arena = Arena.ofConfined()) {
            final var segment = allocatePoint(arena, point);
            int ok = (int) handle.invokeExact(hwnd, segment);
            return ok == 0 ? Optional.empty() : Optional.of(readPoint(segment));
        } catch (Throwable t) {
            throw failure(name, t);
        }
    }

    /**
     * {@code GetClientRect} answers in <em>client</em> coordinates, so its origin is always (0, 0) and
     * only its size means anything. Where that origin sits on the screen is a separate question — and
     * the only one that says where to draw — hence the {@code ClientToScreen} that follows.
     *
     * <p>Together they exclude the frame: the client origin is <em>below</em> the title bar, which is
     * exactly what keeps the overlay off the minimize and close buttons.
     */
    @Override
    public Optional<Rect> clientArea(long hwnd) {
        try (final var arena = Arena.ofConfined()) {
            final var segment = arena.allocate(RECT);
            final var ok = (int) GetClientRect.invokeExact(hwnd, segment);
            if (ok == 0) {
                return Optional.empty();
            }

            final var width = segment.get(ValueLayout.JAVA_INT, 8);
            final var height = segment.get(ValueLayout.JAVA_INT, 12);

            return clientToScreen(hwnd, new Point(0, 0)).map(origin ->
                    new Rect(origin.x(), origin.y(), origin.x() + width, origin.y() + height));
        } catch (Throwable t) {
            throw failure("GetClientRect", t);
        }
    }

    @Override
    public long windowFromPoint(Point screen) {
        try (final var arena = Arena.ofConfined()) {
            final var segment = allocatePoint(arena, screen);
            return (long) WindowFromPoint.invokeExact(segment); // by value, not by reference
        } catch (Throwable t) {
            throw failure("WindowFromPoint", t);
        }
    }

    @Override
    public long parentWindow(long hwnd) {
        try {
            return (long) GetParent.invokeExact(hwnd);
        } catch (Throwable t) {
            throw failure("GetParent", t);
        }
    }

    @Override
    public Optional<Point> cursorPosition() {
        try (final var arena = Arena.ofConfined()) {
            final var segment = arena.allocate(POINT);
            final var ok = (int) GetCursorPos.invokeExact(segment);
            return ok == 0 ? Optional.empty() : Optional.of(readPoint(segment));
        } catch (Throwable t) {
            throw failure("GetCursorPos", t);
        }
    }

    @Override
    public long monitorFromWindow(long hwnd) {
        try {
            return (long) MonitorFromWindow.invokeExact(hwnd, Win32.MONITOR_DEFAULTTONEAREST);
        } catch (Throwable t) {
            throw failure("MonitorFromWindow", t);
        }
    }

    private static MemorySegment allocatePoint(Arena arena, Point point) {
        final var segment = arena.allocate(POINT);
        segment.set(ValueLayout.JAVA_INT, 0, point.x());
        segment.set(ValueLayout.JAVA_INT, 4, point.y());
        return segment;
    }

    private static Point readPoint(MemorySegment segment) {
        return new Point(segment.get(ValueLayout.JAVA_INT, 0), segment.get(ValueLayout.JAVA_INT, 4));
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean isKeyDown(int virtualKey) {
        try {
            final var state = (short) GetAsyncKeyState.invokeExact(virtualKey);
            return (state & Win32.KEY_DOWN_MASK) != 0;
        } catch (Throwable t) {
            throw failure("GetAsyncKeyState", t);
        }
    }

    // ------------------------------------------------------------------ taskbar

    @Override
    public void stopFlashing(long hwnd) {
        try (final var arena = Arena.ofConfined()) {
            final var info = arena.allocate(FLASHWINFO);
            info.set(ValueLayout.JAVA_INT, 0, (int) FLASHWINFO.byteSize()); // cbSize
            info.set(ValueLayout.JAVA_LONG, 8, hwnd);
            info.set(ValueLayout.JAVA_INT, 16, Win32.FLASHW_STOP);          // dwFlags
            info.set(ValueLayout.JAVA_INT, 20, 0);                          // uCount
            info.set(ValueLayout.JAVA_INT, 24, 0);                          // dwTimeout

            // The return value is the window's previous state, not a success: nothing to check.
            final var ignored = (int) FlashWindowEx.invokeExact(info);
        } catch (Throwable t) {
            throw failure("FlashWindowEx", t);
        }
    }

    @Override
    public OptionalInt foregroundFlashCount() {
        try (final var arena = Arena.ofConfined()) {
            final var count = arena.allocate(ValueLayout.JAVA_INT);

            final var read = (int) SystemParametersInfoRead.invokeExact(
                    Win32.SPI_GETFOREGROUNDFLASHCOUNT, 0, count, NO_PERSIST);

            return read == 0
                    ? OptionalInt.empty()
                    : OptionalInt.of(count.get(ValueLayout.JAVA_INT, 0));
        } catch (Throwable t) {
            throw failure("SystemParametersInfo", t);
        }
    }

    @Override
    public boolean setForegroundFlashCount(int count) {
        try {
            final var written = (int) SystemParametersInfoWrite.invokeExact(
                    Win32.SPI_SETFOREGROUNDFLASHCOUNT, 0, (long) count, NO_PERSIST);

            return written != 0;
        } catch (Throwable t) {
            throw failure("SystemParametersInfo", t);
        }
    }

    /**
     * A {@code Throwable} out of {@code invokeExact} means the call itself could not be made — a
     * signature mismatch or a closed arena — never a Win32 error code. It is a bug, not a condition
     * to recover from, so it surfaces unchecked.
     */
    private static Win32Exception failure(String function, Throwable cause) {
        if (cause instanceof Error error) {
            throw error;
        }
        return new Win32Exception("Native call to " + function + " failed", cause);
    }
}
