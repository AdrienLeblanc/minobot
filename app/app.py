import asyncio
import logging
import sys
from typing import Dict, Any, Callable

from app.config_loader import load_config
from app.logger import setup_logger
from core.focus_manager import FocusManager
from core.input_simulator import InputSimulator
from core.keyboard_monitor import KeyboardMonitor
from core.system_tray import SystemTrayManager
from core.window_manager import WindowManager
from features.group_manager import GroupManager
from features.multi_window_clicker import MultiWindowClicker
from features.notification_listener import NotificationListener
from features.window_cycler import WindowCycler
from features.window_reorder import WindowReorder


class MinobotApp:
    """
    The main application class that orchestrates all components and features.
    """

    def __init__(self) -> None:
        """
        Initializes the application by loading configuration, setting up logging,
        and instantiating all necessary managers and features.
        """
        self.config: Dict[str, Any] = load_config("config.json")
        self.logger: logging.Logger = setup_logger(self.config)

        # Core Components
        self.system_tray: SystemTrayManager = SystemTrayManager(self.logger)
        self.window_manager: WindowManager = WindowManager(self.logger, self.config)
        self.input_simulator: InputSimulator = InputSimulator(self.logger)
        self.focus_manager: FocusManager = FocusManager(self.logger, self.config, self.input_simulator)
        self.keyboard_monitor: KeyboardMonitor = KeyboardMonitor(self.logger)

        # Features
        self.group_manager: GroupManager = GroupManager(
            self.logger, self.window_manager, self.input_simulator, self.focus_manager
        )
        self.multi_clicker: MultiWindowClicker = MultiWindowClicker(self.logger, self.window_manager, self.config)
        self.notification_listener: NotificationListener = NotificationListener(
            self.logger, self.window_manager, self.focus_manager, self.config
        )
        self.window_cycler: WindowCycler = WindowCycler(
            self.logger, self.window_manager, self.focus_manager, self.config
        )
        self.window_reorder: WindowReorder = WindowReorder(
            self.logger, self.window_manager, self.focus_manager, self.config
        )

        self._setup_hotkeys()

    def _register_feature_hotkey(
        self,
        config_key_enabled: str,
        config_key_hotkey: str,
        default_hotkey: str,
        callback: Callable[..., Any],
        feature_name: str,
        cooldown: float,
        pass_mouse_pos: bool = False
    ) -> None:
        """Helper to register a hotkey for a feature if it's enabled in the config."""
        if self.config.get(config_key_enabled, True):
            hotkey = self.config.get(config_key_hotkey, default_hotkey)
            if hotkey:
                self.keyboard_monitor.register_hotkey(
                    hotkey,
                    callback,
                    cooldown=cooldown,
                    pass_mouse_pos=pass_mouse_pos
                )
                self.logger.info(f"Feature '{feature_name}' enabled on hotkey '{hotkey}'.")

    def _setup_hotkeys(self) -> None:
        """Configures all application hotkeys based on the config file."""
        self._register_feature_hotkey(
            "multiclick_enabled", "multiclick_hotkey", "x1",
            self.multi_clicker.click_all_windows,
            "Multi-Window Click",
            cooldown=self.config.get("multiclick_cooldown", 0.1),
            pass_mouse_pos=True
        )
        self._register_feature_hotkey(
            "group_invite_enabled", "group_invite_hotkey", "F8",
            self.group_manager.invite_all,
            "Group Invitation",
            cooldown=5.0
        )
        self._register_feature_hotkey(
            "window_cycle_enabled", "window_cycle_next_hotkey", "x2",
            self.window_cycler.cycle_next,
            "Window Cycler (Next)",
            cooldown=0.1
        )
        self._register_feature_hotkey(
            "window_cycle_enabled", "window_cycle_prev_hotkey", "shift+x2",
            self.window_cycler.cycle_prev,
            "Window Cycler (Prev)",
            cooldown=0.1
        )
        self._register_feature_hotkey(
            "window_reorder_enabled", "window_reorder_hotkey", "F9",
            self.window_reorder.reorder_taskbar,
            "Window Reorder",
            cooldown=5.0
        )

    async def run(self) -> None:
        """Starts all services and runs the main application loop."""
        self.logger.info("=== Minobot Starting ===")
        self.system_tray.start()
        self.window_manager.refresh()

        try:
            tasks = [
                asyncio.create_task(self.notification_listener.start(), name="notification_listener"),
                asyncio.create_task(self.keyboard_monitor.start(), name="keyboard_monitor")
            ]
            await asyncio.gather(*tasks)
        except Exception as e:
            self.logger.critical(f"Fatal error in main run loop: {e}", exc_info=True)
        finally:
            self.stop()

    def stop(self) -> None:
        """Stops all services gracefully and exits the application."""
        self.logger.info("=== Minobot Stopping ===")
        self.system_tray.stop()
        self.keyboard_monitor.stop()
        sys.exit(0)
