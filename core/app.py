import asyncio
import sys

from .config_loader import load_config
from .focus_manager import FocusManager
from .group_manager import GroupManager
from .input_simulator import InputSimulator
from .keyboard_monitor import KeyboardMonitor
from .logger import setup_logger
from .multi_window_clicker import MultiWindowClicker
from .notification_listener import NotificationListener
from .system_tray import SystemTrayManager
from .window_manager import WindowManager


class MinobotApp:
    """
    Classe principale qui encapsule toute la logique de l'application.
    """

    def __init__(self):
        self.config = load_config("config.json")
        self.logger = setup_logger(self.config)

        # Initialisation des composants
        self.system_tray = SystemTrayManager(self.logger)
        self.window_manager = WindowManager(self.logger, self.config)
        self.input_simulator = InputSimulator(self.logger)
        self.focus_manager = FocusManager(self.logger, self.config, self.input_simulator)
        self.group_manager = GroupManager(self.logger, self.window_manager, self.input_simulator)
        self.multi_clicker = MultiWindowClicker(self.logger, self.window_manager, self.config)
        self.keyboard_monitor = KeyboardMonitor(self.logger)
        self.notification_listener = NotificationListener(self.logger, self.window_manager, self.focus_manager,
                                                          self.config)

        self._setup_hotkeys()

    def _setup_hotkeys(self):
        """Configure les raccourcis clavier."""
        multiclick_hotkey = self.config.get("multiclick_hotkey", "x1")
        if self.config.get("multiclick_enabled", True):
            self.keyboard_monitor.register_hotkey(
                multiclick_hotkey,
                self.multi_clicker.click_all_windows,
                cooldown=self.config.get("multiclick_cooldown", 0.1),
                pass_mouse_pos=True
            )
            self.logger.info(f"Multi-window click feature enabled on mouse button '{multiclick_hotkey}'.")

        group_invite_hotkey = self.config.get("group_invite_hotkey", "F8")
        if self.config.get("group_invite_enabled", True):
            self.keyboard_monitor.register_hotkey(
                group_invite_hotkey,
                self.group_manager.invite_all,
                cooldown=5.0,
                pass_mouse_pos=False
            )
            self.logger.info(f"Group invitation feature enabled on key '{group_invite_hotkey}'.")

    async def run(self):
        """Démarre tous les services et les exécute en parallèle."""
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
            self.logger.error(f"Fatal error in main run loop: {e}", exc_info=True)
        finally:
            self.stop()

    def stop(self):
        """Arrête proprement tous les services."""
        self.logger.info("=== Minobot Stopping ===")
        self.system_tray.stop()
        self.keyboard_monitor.stop()
        sys.exit(0)
