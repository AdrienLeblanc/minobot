import logging
import time
from typing import Dict, Any, Optional, List

import win32gui


class WindowManager:
    """
    Manages the detection and tracking of game windows.
    """

    def __init__(self, logger: logging.Logger, config: Dict[str, Any]):
        """
        Initializes the WindowManager.

        Args:
            logger: The application logger.
            config: The application configuration dictionary.
        """
        self.logger: logging.Logger = logger
        self.config: Dict[str, Any] = config
        self.windows: Dict[str, int] = {}  # Maps window title to its HWND
        self.last_refresh: float = 0.0
        
        # Get refresh interval, ensuring it's a number
        refresh_interval_val = config.get("window_refresh_interval", 30)
        self.refresh_interval: float = float(refresh_interval_val)

    def refresh(self) -> None:
        """
        Refreshes the list of game windows by enumerating all visible windows
        and filtering them based on keywords from the configuration.
        """
        self.logger.debug("Starting window refresh...")
        self.windows = {}
        game_keywords: List[str] = self.config.get("game_keywords", ["Dofus"])

        def enum_windows_callback(hwnd: int, _) -> None:
            """Callback function for win32gui.EnumWindows."""
            if not win32gui.IsWindowVisible(hwnd):
                return

            title: str = win32gui.GetWindowText(hwnd)

            # Check if the title contains any of the game keywords
            if any(keyword in title for keyword in game_keywords):
                self.windows[title] = hwnd

        win32gui.EnumWindows(enum_windows_callback, None)
        self.last_refresh = time.time()

        self.logger.debug(f"Detected {len(self.windows)} game window(s).")
        if self.windows:
            for title in self.windows:
                self.logger.debug(f"  -> Found: '{title}'")

    def ensure_fresh(self) -> None:
        """
        Ensures the window list is up-to-date by checking the refresh interval.
        If the list is stale, a new refresh is triggered.
        """
        is_stale = (time.time() - self.last_refresh) > self.refresh_interval
        if is_stale:
            self.logger.info("Window list is stale, refreshing...")
            self.refresh()

    def find_window(self, character_name: str) -> Optional[int]:
        """
        Finds a window HWND by its character name.

        It first attempts an exact match on the window title and falls back
        to a partial match if the exact match fails.

        Args:
            character_name: The name of the character to find.

        Returns:
            The window handle (HWND) as an integer if found, otherwise None.
        """
        # Ensure the list is fresh before searching
        self.ensure_fresh()
        
        character_lower = character_name.lower()

        # 1. Attempt exact match first for performance and accuracy
        for title, hwnd in self.windows.items():
            # A common pattern is "CharacterName - Dofus Retro"
            # We check if the title *starts with* the character name for robustness
            if title.lower().startswith(character_lower):
                self.logger.debug(f"Found exact match for '{character_name}': '{title}'")
                return hwnd

        # 2. Fallback to partial match
        for title, hwnd in self.windows.items():
            if character_lower in title.lower():
                self.logger.debug(f"Found partial match for '{character_name}': '{title}'")
                return hwnd

        self.logger.warning(f"Could not find any window for character '{character_name}'.")
        return None
