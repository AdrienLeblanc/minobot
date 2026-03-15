import logging
from typing import Dict, Any

import win32gui

from src.core.focus_manager import FocusManager
from src.core.window_manager import WindowManager


class WindowCycler:
    """
    Manages the cycling of game window focus based on a predefined order.
    It cycles through non-minimized windows located on the CURRENT MONITOR.
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

    async def cycle_next(self) -> None:
        """
        Switches focus to the next visible window on the SAME MONITOR.
        """
        # Use the core logic to get monitor-scoped windows
        monitor_windows = self.window_manager.get_windows_on_current_monitor()
        if not monitor_windows:
            self.logger.debug("No windows to cycle on this monitor.")
            return

        current_hwnd = win32gui.GetForegroundWindow()

        # Find index of current window in the filtered list
        current_index = -1
        for i, (_, hwnd) in enumerate(monitor_windows):
            if hwnd == current_hwnd:
                current_index = i
                break

        # Calculate next index (looping within the monitor group)
        if current_index == -1:
            # If current window is not in the list (e.g. external app), start with the first one
            next_index = 0
        else:
            next_index = (current_index + 1) % len(monitor_windows)

        next_title, next_hwnd = monitor_windows[next_index]
        self.logger.debug(f"Cycling NEXT (Monitor-Scoped) to: {next_title}")

        await self.focus_manager.focus(next_hwnd)

    async def cycle_prev(self) -> None:
        """
        Switches focus to the previous visible window on the SAME MONITOR.
        """
        # Use the core logic to get monitor-scoped windows
        monitor_windows = self.window_manager.get_windows_on_current_monitor()
        if not monitor_windows:
            self.logger.debug("No windows to cycle on this monitor.")
            return

        current_hwnd = win32gui.GetForegroundWindow()

        # Find index of current window in the filtered list
        current_index = -1
        for i, (_, hwnd) in enumerate(monitor_windows):
            if hwnd == current_hwnd:
                current_index = i
                break

        # Calculate prev index (looping within the monitor group)
        if current_index == -1:
            # If current window is not in the list, start with the last one
            prev_index = len(monitor_windows) - 1
        else:
            prev_index = (current_index - 1) % len(monitor_windows)

        prev_title, prev_hwnd = monitor_windows[prev_index]
        self.logger.debug(f"Cycling PREV (Monitor-Scoped) to: {prev_title}")

        await self.focus_manager.focus(prev_hwnd)
