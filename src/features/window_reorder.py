import asyncio
import logging
from typing import Dict, Any

import win32con
import win32gui

from src.core.focus_manager import FocusManager
from src.core.window_manager import WindowManager


class WindowReorder:
    """
    Manages the visual reordering of game windows in the Windows taskbar.
    It works by hiding and re-showing windows in the desired order.
    """

    def __init__(
            self,
            logger: logging.Logger,
            window_manager: WindowManager,
            focus_manager: FocusManager,
            config: Dict[str, Any]
    ):
        """
        Initializes the WindowReorder feature.

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
        self.is_running: bool = False

    async def reorder_taskbar(self) -> None:
        """
        Executes the reordering sequence:
        1. Hide all game windows.
        2. Wait for the taskbar to update.
        3. Show windows one by one in the correct order.
        4. Restore focus to the first window.
        """
        if self.is_running:
            self.logger.warning("Window reorder sequence already running.")
            return

        self.is_running = True
        self.logger.info("Starting taskbar reorder sequence...")

        try:
            self.window_manager.refresh()
            sorted_windows = self.window_manager.get_ordered_windows()
            if not sorted_windows:
                self.logger.warning("No Dofus windows found to reorder.")
                return

            sorted_hwnds = [hwnd for _, hwnd in sorted_windows]
            self.logger.info(f"Reordering {len(sorted_hwnds)} windows based on configuration.")

            # 1. Hide all windows
            for hwnd in sorted_hwnds:
                if win32gui.IsWindow(hwnd):
                    win32gui.ShowWindow(hwnd, win32con.SW_HIDE)

            # Wait for Windows to update the taskbar
            await asyncio.sleep(0.5)

            # 2. Show windows in order
            for i, (title, hwnd) in enumerate(sorted_windows):
                if win32gui.IsWindow(hwnd):
                    win32gui.ShowWindow(hwnd, win32con.SW_SHOW)
                    if win32gui.IsIconic(hwnd):
                        win32gui.ShowWindow(hwnd, win32con.SW_RESTORE)

                    # Small delay to ensure Windows registers the order
                    await asyncio.sleep(0.1)

            # 3. Restore focus to the first window using FocusManager
            if sorted_hwnds:
                first_hwnd = sorted_hwnds[0]
                if win32gui.IsWindow(first_hwnd):
                    self.logger.info(f"Restoring focus to first window: {sorted_windows[0][0]}")
                    await self.focus_manager.focus(first_hwnd)

            self.logger.info("Taskbar reorder complete.")

        except Exception as e:
            self.logger.error(f"Error during reorder sequence: {e}", exc_info=True)
            # Emergency recovery: try to show all known windows
            try:
                for hwnd in self.window_manager.windows.values():
                    if win32gui.IsWindow(hwnd):
                        win32gui.ShowWindow(hwnd, win32con.SW_SHOW)
            except Exception:
                pass
        finally:
            self.is_running = False
