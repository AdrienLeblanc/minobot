import asyncio
import logging
import os
import sys
from typing import Dict, Any, Callable

from src.app.config_loader import load_config
from src.app.logger import setup_logger
from src.core.focus_manager import FocusManager
from src.core.input_simulator import InputSimulator
from src.core.keyboard_monitor import KeyboardMonitor
from src.core.notification_manager import NotificationManager
from src.core.system_tray import SystemTrayManager
from src.core.window_manager import WindowManager
from src.features.group_manager import GroupManager
from src.features.multi_window_clicker import MultiWindowClicker
from src.features.notification_listener import NotificationListener
from src.features.window_cycler import WindowCycler
from src.features.window_reorder import WindowReorder


class MinobotApp:
    """
    The main application class that orchestrates all components and features.
    """

    def __init__(self) -> None:
        """
        Initializes the application by loading configuration, setting up logging,
        and instantiating all necessary managers and features.
        """
        # --- Robust Path Resolution ---
        if getattr(sys, 'frozen', False):
            # Running as a PyInstaller executable
            # For the config, we ALWAYS look in the directory of the .EXE
            base_dir = os.path.dirname(sys.executable)
        else:
            # Running as a standard script
            base_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))

        # We prioritize a config.json next to the executable
        config_path = os.path.join(base_dir, "config.json")
        
        # load_config will now auto-create the file if it doesn't exist
        self.config: Dict[str, Any] = load_config(config_path)
        self.logger: logging.Logger = setup_logger(self.config)
        
        self.logger.info(f"=== Minobot Starting (App Dir: {base_dir}) ===")
        self.logger.info(f"Using config: {config_path}")

        # Core Components
        self.system_tray: SystemTrayManager = SystemTrayManager(self.logger, self.stop)
        self.window_manager: WindowManager = WindowManager(self.logger, self.config)
        self.input_simulator: InputSimulator = InputSimulator(self.logger)
        self.keyboard_monitor: KeyboardMonitor = KeyboardMonitor(self.logger)
        self.focus_manager: FocusManager = FocusManager(
            self.logger, 
            self.config, 
            self.input_simulator, 
            self.window_manager,
            keyboard_monitor=self.keyboard_monitor
        )
        self.notification_manager: NotificationManager = NotificationManager(self.logger, self.config)

        # Features
        self.notification_listener: NotificationListener = NotificationListener(
            self.logger, self.window_manager, self.focus_manager, self.notification_manager, self.config
        )
        self.group_manager: GroupManager = GroupManager(
            self.logger, self.window_manager, self.input_simulator, self.focus_manager, self.config
        )
        self.multi_clicker: MultiWindowClicker = MultiWindowClicker(
            self.logger, self.window_manager, self.focus_manager, self.input_simulator, self.config
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
            "multiclick_enabled", "reset_windows_hotkey", "shift+x1",
            self.multi_clicker.reset_windows_attention_state,
            "Reset Windows State",
            cooldown=1.0,
            pass_mouse_pos=False
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
        self.logger.info("=== Application Services Ready ===")
        self.system_tray.start()
        self.window_manager.refresh()

        try:
            tasks = [
                # Start the Core Notification Manager (instead of the Listener)
                asyncio.create_task(self.notification_manager.start(), name="notification_manager"),
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
        os._exit(0)
