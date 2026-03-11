import win32gui
import asyncio
import time


class WindowManager:

    def __init__(self, logger, config):

        self.logger = logger
        self.config = config
        self.windows = {}
        self.last_refresh = 0
        self.refresh_interval = config.get("window_refresh_interval", 30)

    def refresh(self):
        """Rafraîchit la liste des fenêtres de jeu"""

        self.windows = {}

        game_keywords = self.config.get("game_keywords", ["Dofus"])

        def enum_windows(hwnd, _):

            if not win32gui.IsWindowVisible(hwnd):
                return

            title = win32gui.GetWindowText(hwnd)

            # Vérifier si le titre contient un des mots-clés du jeu
            if any(keyword in title for keyword in game_keywords):
                self.windows[title] = hwnd

        win32gui.EnumWindows(enum_windows, None)

        self.last_refresh = time.time()

        self.logger.info(f"Detected {len(self.windows)} game window(s):")

        for title in self.windows:
            self.logger.info(f"  {title}")

    def ensure_fresh(self):
        """S'assure que la liste des fenêtres est à jour"""

        now = time.time()

        if now - self.last_refresh > self.refresh_interval:
            self.logger.debug("Refreshing window list...")
            self.refresh()

    def find_window(self, character):
        """Trouve une fenêtre par nom de personnage"""

        # Recherche exacte d'abord
        for title, hwnd in self.windows.items():
            if character.lower() == title.lower():
                return hwnd

        # Recherche partielle ensuite
        for title, hwnd in self.windows.items():
            if character.lower() in title.lower():
                return hwnd

        return None