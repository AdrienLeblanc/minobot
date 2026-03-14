import logging
import time
from typing import Tuple, Dict, Any, List, Optional

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
        # Maps HWND to its current Window Title
        self.windows: Dict[int, str] = {}
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
                # We use HWND as key to allow multiple windows with the same title
                # (e.g., several windows named "Dofus Retro" during login)
                self.windows[hwnd] = title

        win32gui.EnumWindows(enum_windows_callback, None)
        self.last_refresh = time.time()

        self.logger.debug(f"Detected {len(self.windows)} game window(s).")
        if self.windows:
            for hwnd, title in self.windows.items():
                self.logger.debug(f"  -> Found: '{title}' (HWND: {hwnd})")

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

        Args:
            character_name: The name of the character to find.

        Returns:
            The window handle (HWND) as an integer if found, otherwise None.
        """
        self.ensure_fresh()
        character_lower = character_name.lower()

        # 1. Attempt exact match on extracted character name
        for hwnd, title in self.windows.items():
            extracted = self.extract_character_name(title)
            if extracted and extracted.lower() == character_lower:
                self.logger.debug(f"Found exact match for '{character_name}': '{title}'")
                return hwnd

        # 2. Fallback to partial match in the full title
        for hwnd, title in self.windows.items():
            if character_lower in title.lower():
                self.logger.debug(f"Found partial match for '{character_name}': '{title}'")
                return hwnd

        self.logger.warning(f"Could not find any window for character '{character_name}'.")
        return None

    def extract_character_name(self, title: str) -> Optional[str]:
        """
        Extracts the character name from the notification title based on separators.

        Args:
            title: The notification title.

        Returns:
            The extracted name or the cleaned title.
        """
        separators: List[str] = self.config.get("character_separators", [" - ", ": ", " | "])

        for separator in separators:
            if separator in title:
                return title.split(separator)[0].strip()

        return title.strip()

    def get_ordered_windows(self, reverse_order: bool = False) -> List[Tuple[str, int]]:
        """
        Retrieves the list of game windows, sorted according to the configuration order.
        """
        self.ensure_fresh()
        # Create a list of (title, hwnd) for compatibility with other features
        raw_windows: List[Tuple[str, int]] = [(title, hwnd) for hwnd, title in self.windows.items()]

        if not raw_windows:
            return []

        cycle_order: List[str] = self.config.get("window_cycle_order", [])

        def sort_key(item: Tuple[str, int]) -> int:
            title, _ = item
            title_lower = title.lower()
            for i, name_part in enumerate(cycle_order):
                if name_part.lower() in title_lower:
                    return i
            # Windows without character names in title go to the end
            return len(cycle_order) + 1000

        # Primary sort by title, then by the configured order
        raw_windows.sort(key=lambda x: x[0])
        raw_windows.sort(key=sort_key, reverse=reverse_order)

        return raw_windows

    def get_active_ordered_windows(self) -> List[Tuple[str, int]]:
        """
        Retrieves the list of visible (non-minimized) game windows,
        sorted according to the configuration order.

        Returns:
            A list of tuples (window_title, hwnd), sorted by priority.
        """
        self.ensure_fresh()

        ordered_windows = self.get_ordered_windows()

        # Filter out minimized windows
        visible_windows = [
            (title, hwnd) for title, hwnd in ordered_windows
            if not win32gui.IsIconic(hwnd)
        ]

        if not visible_windows:
            self.logger.debug("No visible game windows to cycle.")
            return []

        return visible_windows
