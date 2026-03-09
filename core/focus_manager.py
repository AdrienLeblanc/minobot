import win32gui
import win32con
import win32api
import time


class FocusManager:

    def __init__(self, logger):

        self.logger = logger
        self.last_focus = None
        self.last_time = 0
        self.cooldown = 1

    def focus(self, hwnd):

        now = time.time()

        if hwnd == self.last_focus and now - self.last_time < self.cooldown:
            return

        self.last_focus = hwnd
        self.last_time = now

        try:

            title = win32gui.GetWindowText(hwnd)

            if win32gui.IsIconic(hwnd):
                win32gui.ShowWindow(hwnd, win32con.SW_RESTORE)

            # ASTUCE : simuler ALT pour autoriser le focus
            win32api.keybd_event(win32con.VK_MENU, 0, 0, 0)
            win32api.keybd_event(win32con.VK_MENU, 0, win32con.KEYEVENTF_KEYUP, 0)

            win32gui.SetForegroundWindow(hwnd)
            win32gui.BringWindowToTop(hwnd)

            self.logger.info(f"[FOCUS OK] {title}")

        except Exception as e:

            self.logger.error(f"[FOCUS ERROR] {e}")