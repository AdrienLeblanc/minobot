import win32gui


class WindowManager:

    def __init__(self, logger):

        self.logger = logger
        self.windows = {}

    def refresh(self):

        self.windows = {}

        def enum_windows(hwnd, _):

            if not win32gui.IsWindowVisible(hwnd):
                return

            title = win32gui.GetWindowText(hwnd)

            if "Dofus" in title:

                self.windows[title] = hwnd

        win32gui.EnumWindows(enum_windows, None)

        self.logger.info("Detected game windows:")

        for title in self.windows:
            self.logger.info(f"  {title}")

    def find_window(self, character):

        for title, hwnd in self.windows.items():

            if character.lower() in title.lower():
                return hwnd

        return None