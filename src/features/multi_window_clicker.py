import asyncio
import ctypes
import logging
import time
from ctypes import wintypes
from typing import Tuple, Dict, Any, List, Optional

import pywintypes
import win32api
import win32con
import win32gui

from src.core.focus_manager import FocusManager
from src.core.input_simulator import InputSimulator
from src.core.window_manager import WindowManager

# Ctypes setup for FlashWindowEx
user32 = ctypes.WinDLL('user32', use_last_error=True)


class FLASHWINFO(ctypes.Structure):
    _fields_ = (('cbSize', wintypes.UINT),
                ('hwnd', wintypes.HWND),
                ('dwFlags', wintypes.DWORD),
                ('uCount', wintypes.UINT),
                ('dwTimeout', wintypes.DWORD))


user32.FlashWindowEx.argtypes = (ctypes.POINTER(FLASHWINFO),)


class MultiWindowClicker:
    """
    Manages synchronized clicks across multiple game windows.
    """

    def __init__(self, logger: logging.Logger, window_manager: WindowManager, focus_manager: FocusManager,
                 input_simulator: InputSimulator, config: Dict[str, Any]):
        """
        Initializes the MultiWindowClicker.

        Args:
            logger: The application logger.
            window_manager: The manager for tracking game windows.
            focus_manager: The manager for focusing windows.
            input_simulator: The simulator for keyboard/mouse inputs.
            config: The application configuration dictionary.
        """
        self.logger: logging.Logger = logger
        self.window_manager: WindowManager = window_manager
        self.focus_manager: FocusManager = focus_manager
        self.input_simulator: InputSimulator = input_simulator
        self.config: Dict[str, Any] = config

        # Configuration properties
        self.click_delay: float = float(config.get("multiclick_delay", 0.01))
        self.restore_original_window: bool = bool(config.get("multiclick_restore_focus", True))
        self.dry_run: bool = bool(config.get("multiclick_dry_run", False))
        self.exclude_list: List[str] = [name.lower() for name in config.get("multiclick_exclude", [])]
        self.click_button: str = config.get("multiclick_button", "left")

        # Statistics tracking
        self.stats: Dict[str, float] = {
            "total_triggers": 0,
            "total_clicks": 0,
            "total_failures": 0,
            "total_time_ms": 0
        }

        if self.dry_run:
            self.logger.warning("[MULTICLICK] DRY RUN MODE - No actual clicks will be sent.")

    async def click_all_windows(self, screen_position: Tuple[int, int]) -> None:
        """
        Clicks on all game windows at an equivalent relative position.
        """
        start_time = time.time()
        self.stats["total_triggers"] += 1

        original_window: Optional[int] = win32gui.GetForegroundWindow() if self.restore_original_window else None

        windows = self.window_manager.get_ordered_windows()  # Use sorted order
        if not windows:
            self.logger.warning("[MULTICLICK] No game windows found to click.")
            return

        relative_client_pos = self._calculate_relative_position(screen_position, [hwnd for _, hwnd in windows])

        clicked_count, failed_count = 0, 0
        for title, hwnd in windows:
            # Skip minimized windows
            if win32gui.IsIconic(hwnd):
                self.logger.debug(f"[MULTICLICK] Skipping minimized window: {title}")
                continue

            # If it's the original window, we assume the user's click already handled it.
            # We only process other windows.
            if original_window and hwnd == original_window:
                clicked_count += 1
                continue

            if await self._process_single_window_click(hwnd, title, screen_position, relative_client_pos):
                clicked_count += 1
            else:
                failed_count += 1

        if original_window and self.restore_original_window:
            try:
                win32gui.SetForegroundWindow(original_window)
            except pywintypes.error:
                pass

        self._log_stats(clicked_count, failed_count, start_time)

    async def reset_windows_attention_state(self) -> None:
        """
        Simulates a human click on each window in reverse order to reset their attention state.
        This is a visually disruptive but reliable method.
        """
        self.logger.info("Starting window attention state reset sequence...")

        windows = self.window_manager.get_ordered_windows(reverse_order=True)  # Process in reverse order
        if not windows:
            self.logger.warning("No game windows found to reset.")
            return

        leader_window_title, leader_window_hwnd = windows[len(windows) - 1]

        for title, hwnd in windows:
            if win32gui.IsIconic(hwnd):
                self.logger.debug(f"Skipping minimized window for reset: {title}")
                continue

            try:
                self.logger.debug(f"Resetting attention for '{title}' (HWND: {hwnd})")

                # Focus the window
                await self.focus_manager.focus(hwnd)
                await asyncio.sleep(0.05)  # Give Windows a moment to settle

            except Exception as e:
                self.logger.error(f"Failed to reset attention for window '{title}': {e}")

        # Restore focus to the leader window
        if leader_window_hwnd:
            self.logger.debug(f"Restoring focus to '{leader_window_title}' window ")
            await self.focus_manager.focus(leader_window_hwnd)
        self.logger.info("Window attention state reset sequence complete.")

    def _calculate_relative_position(self, screen_pos: Tuple[int, int], window_hwnds: List[int]) -> Optional[
        Tuple[int, int]]:
        """Calculates the click position relative to the source window's client area."""
        source_window = win32gui.WindowFromPoint(screen_pos)

        # Find the top-level game window if the click was on a child window
        parent = source_window
        depth = 0
        while parent and parent not in window_hwnds and depth < 10:
            parent = win32gui.GetParent(parent)
            depth += 1

        if parent and parent in window_hwnds:
            source_window = parent
        else:
            self.logger.debug("[MULTICLICK] Click not on a known game window, using absolute position.")
            return None

        try:
            return win32gui.ScreenToClient(source_window, screen_pos)
        except pywintypes.error as e:
            self.logger.debug(f"[MULTICLICK] ScreenToClient failed: {e}")
            return None

    async def _process_single_window_click(
            self, hwnd: int, title: str, screen_pos: Tuple[int, int], rel_pos: Optional[Tuple[int, int]]
    ) -> bool:
        """Processes the click logic for a single window."""
        try:
            if not win32gui.IsWindow(hwnd) or win32gui.IsIconic(hwnd):
                self.logger.debug(f"[MULTICLICK] Skipping invalid/minimized window: {title}")
                return False

            if rel_pos:
                click_x, click_y = win32gui.ClientToScreen(hwnd, rel_pos)
            else:
                click_x, click_y = screen_pos

            max_retries = 2
            for attempt in range(max_retries):
                if self._click_at_position(hwnd, click_x, click_y, title):
                    if self.click_delay > 0:
                        await asyncio.sleep(self.click_delay)
                    return True
                if attempt < max_retries - 1:
                    await asyncio.sleep(0.005)

            self.logger.warning(f"[MULTICLICK] Failed to click {title} after {max_retries} attempts.")
            return False

        except pywintypes.error as e:
            self.logger.debug(f"[MULTICLICK] Win32 error on window {title}: {e}")
            return False
        except Exception as e:
            self.logger.error(f"[MULTICLICK] Unexpected error on window {title}: {e}", exc_info=True)
            return False

    def _stop_taskbar_flash(self, hwnd: int) -> None:
        """Stops the taskbar from flashing orange by calling FlashWindowEx."""
        try:
            info = FLASHWINFO(
                cbSize=ctypes.sizeof(FLASHWINFO),
                hwnd=hwnd,
                dwFlags=win32con.FLASHW_STOP,
                uCount=0,
                dwTimeout=0
            )
            user32.FlashWindowEx(ctypes.byref(info))
        except Exception:
            pass  # Ignore errors here, it's non-critical

    def _click_at_position(self, hwnd: int, x: int, y: int, title: str) -> bool:
        """Sends a click message to a window at a specific screen position."""
        if self.dry_run:
            self.logger.debug(f"[DRY RUN] Would click at ({x}, {y}) on {title}")
            return True

        try:
            client_x, client_y = win32gui.ScreenToClient(hwnd, (x, y))
            lparam = win32api.MAKELONG(client_x, client_y)

            button_map = {
                "right": (win32con.WM_RBUTTONDOWN, win32con.WM_RBUTTONUP, win32con.MK_RBUTTON),
                "middle": (win32con.WM_MBUTTONDOWN, win32con.WM_MBUTTONUP, win32con.MK_MBUTTON),
                "left": (win32con.WM_LBUTTONDOWN, win32con.WM_LBUTTONUP, win32con.MK_LBUTTON),
            }
            msg_down, msg_up, wparam = button_map.get(self.click_button, button_map["left"])

            self._stop_taskbar_flash(hwnd)
            win32gui.PostMessage(hwnd, msg_down, wparam, lparam)
            win32gui.PostMessage(hwnd, msg_up, 0, lparam)
            self._stop_taskbar_flash(hwnd)
            return True
        except pywintypes.error as e:
            self.logger.debug(f"[CLICK ERROR] Win32 error on {title}: {e}")
            return False

    def _log_stats(self, clicked: int, failed: int, start_time: float) -> None:
        """Logs performance statistics for the click operation."""
        self.stats["total_clicks"] += clicked
        self.stats["total_failures"] += failed
        elapsed = (time.time() - start_time) * 1000
        self.stats["total_time_ms"] += elapsed
        avg_time = self.stats["total_time_ms"] / self.stats["total_triggers"] if self.stats["total_triggers"] > 0 else 0
        self.logger.debug(
            f"[MULTICLICK] {clicked} OK, {failed} failed in {elapsed:.0f}ms "
            f"(avg: {avg_time:.0f}ms, total: {self.stats['total_clicks']} clicks)"
        )
