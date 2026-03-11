import logging
import win32gui
from typing import List, Tuple, Dict, Any

from core.window_manager import WindowManager
from core.focus_manager import FocusManager


class WindowCycler:
    """
    Manages the cycling of game window focus based on a predefined order.
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
        Retrieves the list of game windows, sorted according to the configuration order.

        Returns:
            A list of tuples (window_title, hwnd), sorted by priority.
        """
        self.window_manager.ensure_fresh()
        
        # We only take windows managed by WindowManager (thus Dofus windows)
        raw_windows: List[Tuple[str, int]] = list(self.window_manager.windows.items())

        if not raw_windows:
            return []

        # Get priority order from config
        cycle_order: List[str] = self.config.get("window_cycle_order", [])

        # Sort key function
        def sort_key(item: Tuple[str, int]) -> int:
            title, _ = item
            title_lower = title.lower()

            # Try to find the index in the config list
            for i, name_part in enumerate(cycle_order):
                if name_part.lower() in title_lower:
                    return i  # Return priority index (0, 1, 2...)

            # If not in list, put at the end
            return len(cycle_order) + 1000

        # First, global alphabetical sort for stable ordering of non-configured windows
        raw_windows.sort(key=lambda x: x[0])
        
        # Then apply priority sort
        raw_windows.sort(key=sort_key)

        return raw_windows

    async def cycle_next(self) -> None:
        """
        Switches focus to the next window in the sorted list.
        """
        sorted_windows = self._get_sorted_windows()
        if not sorted_windows:
            self.logger.warning("No Dofus windows found to cycle.")
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
        Switches focus to the previous window in the sorted list.
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
            prev_index = len(sorted_windows) - 1 # Start from end if out of context
        else:
            prev_index = (current_index - 1) % len(sorted_windows)
        
        prev_title, prev_hwnd = sorted_windows[prev_index]
        self.logger.debug(f"Cycling to prev window: {prev_title}")
        
        await self.focus_manager.focus(prev_hwnd)
