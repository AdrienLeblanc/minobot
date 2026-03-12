import logging
from typing import List, Tuple, Dict, Any

import win32gui

from src.core.focus_manager import FocusManager
from src.core.window_manager import WindowManager


class WindowCycler:
    """
    Manages the cycling of game window focus based on a predefined order.
    It only cycles through non-minimized windows.
    """

    def __init__(
            self,
            logger: logging.Logger,
            window_manager: WindowManager,
            focus_manager: FocusManager,
            config: Dict[str, Any]
    ):
        """
        Initializes the WindowCycler.

        Args:
            logger: The application logger.
            window_manager: The manager responsible for tracking game windows.
            focus_manager: The manager responsible for focusing windows.
            config: The application configuration dictionary.
        """
        self.logger: logging.Logger = logger
        self.window_manager: WindowManager = window_manager
        self.focus_manager: FocusManager = focus_manager
        self.config: Dict[str, Any] = config

    def _get_sorted_windows(self) -> List[Tuple[str, int]]:
        """
        Retrieves the list of visible (non-minimized) game windows,
        sorted according to the configuration order.

        Returns:
            A list of tuples (window_title, hwnd), sorted by priority.
        """
        self.window_manager.ensure_fresh()

        raw_windows: List[Tuple[str, int]] = list(self.window_manager.windows.items())
        if not raw_windows:
            return []

        # Filter out minimized windows
        visible_windows = [
            (title, hwnd) for title, hwnd in raw_windows
            if not win32gui.IsIconic(hwnd)
        ]
        
        if not visible_windows:
            self.logger.debug("No visible game windows to cycle.")
            return []

        # Get priority order from config
        cycle_order: List[str] = self.config.get("window_cycle_order", [])

        # Sort key function
        def sort_key(item: Tuple[str, int]) -> int:
            title, _ = item
            title_lower = title.lower()
            for i, name_part in enumerate(cycle_order):
                if name_part.lower() in title_lower:
                    return i
            return len(cycle_order) + 1000

        # Sort visible windows
        visible_windows.sort(key=lambda x: x[0])
        visible_windows.sort(key=sort_key)

        return visible_windows

    async def cycle_next(self) -> None:
        """
        Switches focus to the next visible window in the sorted list.
        """
        sorted_windows = self._get_sorted_windows()
        if not sorted_windows:
            return

        current_hwnd = win32gui.GetForegroundWindow()

        # Find index of current window
        current_index = -1
        for i, (_, hwnd) in enumerate(sorted_windows):
            if hwnd == current_hwnd:
                current_index = i
                break

        # Calculate next index (looping)
        if current_index == -1:
            next_index = 0
        else:
            next_index = (current_index + 1) % len(sorted_windows)

        next_title, next_hwnd = sorted_windows[next_index]
        self.logger.debug(f"Cycling to next window: {next_title}")

        await self.focus_manager.focus(next_hwnd)

    async def cycle_prev(self) -> None:
        """
        Switches focus to the previous visible window in the sorted list.
        """
        sorted_windows = self._get_sorted_windows()
        if not sorted_windows:
            return

        current_hwnd = win32gui.GetForegroundWindow()

        # Find index of current window
        current_index = -1
        for i, (_, hwnd) in enumerate(sorted_windows):
            if hwnd == current_hwnd:
                current_index = i
                break

        # Calculate prev index (looping)
        if current_index == -1:
            prev_index = len(sorted_windows) - 1
        else:
            prev_index = (current_index - 1) % len(sorted_windows)

        prev_title, prev_hwnd = sorted_windows[prev_index]
        self.logger.debug(f"Cycling to prev window: {prev_title}")

        await self.focus_manager.focus(prev_hwnd)
