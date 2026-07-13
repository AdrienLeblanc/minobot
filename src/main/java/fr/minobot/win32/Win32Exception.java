package fr.minobot.win32;

/**
 * A native call could not be performed at all — the FFM equivalent of never getting to make the
 * call, not of Windows answering "no".
 *
 * <p>A Win32 function that simply fails (a stale handle, a refused focus) is reported through the
 * return type of {@link WindowApi} — {@code false} or an empty {@code Optional}
 */
public class Win32Exception extends RuntimeException {

    public Win32Exception(String message, Throwable cause) {
        super(message, cause);
    }
}
