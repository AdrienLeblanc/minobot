import ctypes
import logging
import time
from typing import Tuple, Dict, Any, List, Optional

import win32gui

# --- Monitor API Setup ---
user32 = ctypes.windll.user32
MONITOR_DEFAULTTONEAREST = 0x00000002


class WindowManager:
    """
    Manages the detection and tracking of game windows, including their monitor context.
    """

    def __init__(self, logger: logging.Logger, config: Dict[str, Any]):
        """
        Initializes the WindowManager.
        """
        self.logger: logging.Logger = logger
        self.config: Dict[str, Any] = config
        # Maps HWND (int) -> Title (str)
        self.windows: Dict[int, str] = {}
        self.last_refresh: float = 0.0
        # Ensure refresh_interval is a float
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
            if not win32gui.IsWindowVisible(hwnd):
                return
            title: str = win32gui.GetWindowText(hwnd)
            if any(keyword in title for keyword in game_keywords):
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
        """
        if (time.time() - self.last_refresh) > self.refresh_interval:
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

        # 1. Exact match on extracted name
        for hwnd, title in self.windows.items():
            extracted = self.extract_character_name(title)
            if extracted and extracted.lower() == character_lower:
                self.logger.debug(f"Found exact match for '{character_name}': '{title}'")
                return hwnd

        # 2. Partial match on full title
        for hwnd, title in self.windows.items():
            if character_lower in title.lower():
                self.logger.debug(f"Found partial match for '{character_name}': '{title}'")
                return hwnd

        self.logger.warning(f"Could not find any window for character '{character_name}'.")
        return None

    def extract_character_name(self, title: str) -> Optional[str]:
        """
        Extracts the character name from the window title based on separators.

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
        Returns a list of (Title, HWND) tuples.
        """
        self.ensure_fresh()
        
        # Convert dict items (HWND, Title) to list of (Title, HWND)
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
            # Windows not in config go to the end
            return len(cycle_order) + 1000

        # Sort alphabetically first to stabilize order for unknown windows
        raw_windows.sort(key=lambda x: x[0])
        # Then sort by priority configuration
        raw_windows.sort(key=sort_key, reverse=reverse_order)

        return raw_windows

    def get_active_ordered_windows(self) -> List[Tuple[str, int]]:
        """
        Retrieves the list of visible (non-minimized) game windows, sorted.
        Returns a list of (Title, HWND) tuples.
        """
        ordered_windows = self.get_ordered_windows()
        return [(title, hwnd) for title, hwnd in ordered_windows if not win32gui.IsIconic(hwnd)]

    def get_monitor_handle(self, hwnd: int) -> int:
        """Returns the handle of the monitor containing the given window."""
        return user32.MonitorFromWindow(hwnd, MONITOR_DEFAULTTONEAREST)

    def get_windows_on_current_monitor(self) -> List[Tuple[str, int]]:
        """
        Returns a list of ordered game windows that are on the same monitor
        as the currently active window.
        """
        current_hwnd = win32gui.GetForegroundWindow()
        
        # If no window is focused, just return all active windows
        if not current_hwnd:
            return self.get_active_ordered_windows()

        current_monitor = self.get_monitor_handle(current_hwnd)
        all_windows = self.get_active_ordered_windows()

        # Filter windows that are on the same monitor
        same_monitor_windows = [
            (title, hwnd) for title, hwnd in all_windows
            if self.get_monitor_handle(hwnd) == current_monitor
        ]
        
        if not same_monitor_windows:
            self.logger.debug("No game windows found on the current monitor.")
            return []

        return same_monitor_windows
