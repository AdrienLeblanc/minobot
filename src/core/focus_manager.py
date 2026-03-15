import asyncio
import logging
import time
from typing import Dict, Any, Optional

import pywintypes
import win32con
import win32gui

from src.core.input_simulator import InputSimulator
from src.core.window_manager import WindowManager
# We import only for type hinting to avoid circular imports if possible,
# or handle it gracefully.
# from src.core.keyboard_monitor import KeyboardMonitor (avoid if circular)

class FocusManager:
    """
    Manages bringing game windows to the foreground and setting focus.
    """

    def __init__(
            self,
            logger: logging.Logger,
            config: Dict[str, Any],
            input_simulator: InputSimulator,
            window_manager: WindowManager,
            keyboard_monitor: Optional[Any] = None  # Typed as Any to avoid circular import issues
    ):
        """
        Initializes the FocusManager.

        Args:
            logger: The application logger.
            config: The application configuration dictionary.
            input_simulator: The simulator for keyboard/mouse actions.
            window_manager: The manager for tracking game windows.
            keyboard_monitor: Optional KeyboardMonitor for smart focus logic.
        """
        self.logger: logging.Logger = logger
        self.config: Dict[str, Any] = config
        self.input_simulator: InputSimulator = input_simulator
        self.window_manager: WindowManager = window_manager
        self.keyboard_monitor = keyboard_monitor
        self.last_focus_time: float = 0.0
        self.cooldown: float = float(config.get("focus_cooldown", 0.2))

    async def focus(self, hwnd: int, smart: bool = False, force: bool = False) -> None:
        """
        Brings a window to the foreground and sets it as the active window.

        Args:
            hwnd: The handle (HWND) of the window to focus.
            smart: If True, skips focus if the user is currently typing.
            force: If True, ignores cooldown and smart focus checks.
        """
        now = time.time()

        if not force:
            # Smart Focus Check
            if smart and self.keyboard_monitor:
                # We assume keyboard_monitor has get_last_keyboard_activity() method
                activity_threshold = float(self.config.get("smart_focus_threshold", 2.0))
                last_activity = self.keyboard_monitor.get_last_keyboard_activity()
                
                if (now - last_activity) < activity_threshold:
                    self.logger.debug(
                        f"Smart focus for HWND {hwnd} skipped (recent user activity detected)."
                    )
                    return

            # Cooldown Check
            if (now - self.last_focus_time) < self.cooldown:
                self.logger.debug(f"Focus attempt on HWND {hwnd} skipped due to cooldown.")
                return

        self.last_focus_time = now

        try:
            title = self.window_manager.extract_character_name(win32gui.GetWindowText(hwnd))
            self.logger.debug(f"Attempting to focus window: '{title}' (HWND: {hwnd})")

            # 1. Simulate a brief ALT press to "wake up" Windows' focus logic.
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
                win32gui.ShowWindow(hwnd, win32con.SW_SHOW)
                if win32gui.GetForegroundWindow() == hwnd:
                    self.logger.debug(f"Successfully focused '{title}' on second attempt.")
                else:
                    self.logger.error(f"Could not focus '{title}'.")
            else:
                self.logger.debug(f"Successfully focused '{title}'.")

        except pywintypes.error as e:
            self.logger.error(
                f"Win32 API error while focusing '{win32gui.GetWindowText(hwnd)}': {e}"
            )
        except Exception as e:
            self.logger.error(f"An unexpected error occurred during focus: {e}", exc_info=True)
