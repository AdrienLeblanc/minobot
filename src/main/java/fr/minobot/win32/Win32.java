package fr.minobot.win32;

/**
 * The Win32 constants and bit-twiddling the project needs
 */
public final class Win32 {

    /** The value of a {@code HWND} / {@code HMONITOR} that designates "no handle". */
    public static final long NULL_HANDLE = 0L;

    // ShowWindow commands
    public static final int SW_HIDE = 0;
    public static final int SW_SHOW = 5;
    public static final int SW_RESTORE = 9;

    // Mouse messages
    public static final int WM_LBUTTONDOWN = 0x0201;
    public static final int WM_LBUTTONUP = 0x0202;
    public static final int WM_RBUTTONDOWN = 0x0204;
    public static final int WM_RBUTTONUP = 0x0205;
    public static final int WM_MBUTTONDOWN = 0x0207;
    public static final int WM_MBUTTONUP = 0x0208;

    // wParam flags of the mouse messages above
    public static final int MK_LBUTTON = 0x0001;
    public static final int MK_RBUTTON = 0x0002;
    public static final int MK_MBUTTON = 0x0010;

    /** FlashWindowEx: stop flashing and restore the window to its original state. */
    public static final int FLASHW_STOP = 0;

    /** MonitorFromWindow: return the monitor that the window overlaps the most. */
    public static final int MONITOR_DEFAULTTONEAREST = 0x00000002;

    /** The bit that {@code GetAsyncKeyState} sets when the key is currently held down. */
    public static final int KEY_DOWN_MASK = 0x8000;

    // Virtual key codes
    public static final int VK_LBUTTON = 0x01;
    public static final int VK_RBUTTON = 0x02;
    public static final int VK_MBUTTON = 0x04;
    public static final int VK_XBUTTON1 = 0x05;
    public static final int VK_XBUTTON2 = 0x06;
    public static final int VK_BACK = 0x08;
    public static final int VK_TAB = 0x09;
    public static final int VK_RETURN = 0x0D;
    public static final int VK_SHIFT = 0x10;
    public static final int VK_CONTROL = 0x11;
    public static final int VK_MENU = 0x12;
    public static final int VK_SPACE = 0x20;

    /** {@code 0} through {@code 9}. */
    public static final int VK_0 = 0x30;
    public static final int VK_9 = 0x39;

    /** {@code A} through {@code Z}. */
    public static final int VK_A = 0x41;
    public static final int VK_Z = 0x5A;

    /** {@code F1} through {@code F12} are contiguous from here. */
    public static final int VK_F1 = 0x70;

    /** The virtual key code of {@code Fn}, for {@code n} in 1..12. */
    public static int functionKey(int n) {
        if (n < 1 || n > 12) {
            throw new IllegalArgumentException("No such function key: F" + n);
        }
        return VK_F1 + n - 1;
    }

    /**
     * Packs a coordinate pair into the {@code lParam} of a mouse message — the equivalent of
     * {@code win32api.MAKELONG(x, y)}.
     *
     * <p>Coordinates are signed 16-bit: a click outside the client area legitimately yields negative
     * values, and each half must be truncated to its low 16 bits rather than sign-extended over the
     * other half. Getting this wrong sends the click to the wrong place instead of failing loudly,
     * which is why it lives alone here.
     */
    public static long makeLParam(int x, int y) {
        return ((long) (y & 0xFFFF) << 16) | (x & 0xFFFF);
    }

    private Win32() {
    }
}
