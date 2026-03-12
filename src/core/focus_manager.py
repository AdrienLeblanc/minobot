import asyncio
import logging
import time
from typing import Dict, Any

import pywintypes
import win32con
import win32gui

from src.core.input_simulator import InputSimulator


class FocusManager:
    """
    Manages bringing game windows to the foreground and setting focus.
    """

    def __init__(self, logger: logging.Logger, config: Dict[str, Any], input_simulator: InputSimulator):
        """
        Initializes the FocusManager.

        Args:
            logger: The application logger.
            config: The application configuration dictionary.
            input_simulator: The simulator for keyboard/mouse actions.
        """
        self.logger: logging.Logger = logger
        self.config: Dict[str, Any] = config
        self.input_simulator: InputSimulator = input_simulator
        self.last_focus_time: float = 0.0
        self.cooldown: float = float(config.get("focus_cooldown", 0.2))

    async def focus(self, hwnd: int) -> None:
        """
        Brings a window to the foreground and sets it as the active window.
        
        This method uses a robust sequence of calls to handle cases where
        Windows might prevent a background application from stealing focus.

        Args:
            hwnd: The handle (HWND) of the window to focus.
        """
        now = time.time()
        if (now - self.last_focus_time) < self.cooldown:
            self.logger.debug(f"Focus attempt on HWND {hwnd} skipped due to cooldown.")
            return

        self.last_focus_time = now

        try:
            title = win32gui.GetWindowText(hwnd)
            self.logger.debug(f"Attempting to focus window: '{title}' (HWND: {hwnd})")

            # --- Enhanced Focus Method ---

            # 1. Simulate a brief ALT press to "wake up" Windows' focus logic.
            # This is a common trick to allow a background app to set foreground window.
            self.input_simulator.press_key('alt')
            await asyncio.sleep(0.05)

            # 2. Restore the window if it's minimized.
            if win32gui.IsIconic(hwnd):
                win32gui.ShowWindow(hwnd, win32con.SW_RESTORE)
                await asyncio.sleep(0.1)

            # 3. Bring the window to the top of the Z-order.
            win32gui.BringWindowToTop(hwnd)

            # 4. Set the window as the foreground window.
            win32gui.SetForegroundWindow(hwnd)

            # 5. Verification and fallback
            await asyncio.sleep(0.1)
            if win32gui.GetForegroundWindow() != hwnd:
                self.logger.warning(f"Focus on '{title}' failed on first attempt, retrying...")
                # Fallback with a different ShowWindow command
                win32gui.ShowWindow(hwnd, win32con.SW_SHOW)
                if win32gui.GetForegroundWindow() == hwnd:
                    self.logger.debug(f"Successfully focused '{title}' on second attempt.")
                else:
                    self.logger.error(f"Could not focus '{title}'.")
            else:
                self.logger.debug(f"Successfully focused '{title}'.")

        except pywintypes.error as e:
            # Catch specific pywin32 errors
            self.logger.error(f"Win32 API error while focusing '{win32gui.GetWindowText(hwnd)}': {e}")
        except Exception as e:
            self.logger.error(f"An unexpected error occurred during focus: {e}", exc_info=True)
