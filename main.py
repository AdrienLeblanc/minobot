import asyncio
import sys
import socket

from core.logger import setup_logger
from core.config_loader import load_config
from core.window_manager import WindowManager
from core.focus_manager import FocusManager
from core.notification_listener import NotificationListener
from core.keyboard_monitor import KeyboardMonitor
from core.multi_window_clicker import MultiWindowClicker
from core.system_tray import SystemTrayManager
from core.input_simulator import InputSimulator
from core.group_manager import GroupManager

# Port pour le verrouillage de l'instance unique
LOCK_PORT = 12345
lock_socket = None

async def main():
    """Point d'entrée principal de l'application"""

    # --- 1. Initialisation et Configuration ---
    config = load_config("config.json")
    logger = setup_logger(config)

    logger.info("=== Minobot ===")
    logger.info("Starting application...")

    # --- 2. Initialisation des composants principaux ---
    system_tray = SystemTrayManager(logger)
    window_manager = WindowManager(logger, config)
    input_simulator = InputSimulator(logger)
    
    # Le FocusManager a maintenant besoin de l'InputSimulator
    focus_manager = FocusManager(logger, config, input_simulator)
    
    group_manager = GroupManager(logger, window_manager, input_simulator)
    multi_clicker = MultiWindowClicker(logger, window_manager, config)
    keyboard_monitor = KeyboardMonitor(logger)

    # --- 3. Configuration des raccourcis clavier ---
    
    multiclick_hotkey = config.get("multiclick_hotkey", "x1")
    if config.get("multiclick_enabled", True):
        keyboard_monitor.register_hotkey(
            multiclick_hotkey, 
            multi_clicker.click_all_windows,
            cooldown=config.get("multiclick_cooldown", 0.1),
            pass_mouse_pos=True
        )
        logger.info(f"Multi-window click feature enabled on mouse button '{multiclick_hotkey}'.")

    group_invite_hotkey = config.get("group_invite_hotkey", "F8")
    if config.get("group_invite_enabled", True):
        keyboard_monitor.register_hotkey(
            group_invite_hotkey,
            group_manager.invite_all,
            cooldown=5.0,
            pass_mouse_pos=False
        )
        logger.info(f"Group invitation feature enabled on key '{group_invite_hotkey}'.")

    # --- 4. Démarrage des services ---
    system_tray.start()
    logger.info("System tray icon added to taskbar")

    try:
        window_manager.refresh()

        listener = NotificationListener(logger, window_manager, focus_manager, config)

        tasks = [
            asyncio.create_task(listener.start(), name="notification_listener"),
            asyncio.create_task(keyboard_monitor.start(), name="keyboard_monitor")
        ]

        await asyncio.gather(*tasks)

    except KeyboardInterrupt:
        logger.info("Application interrupted by user")
    except Exception as e:
        logger.error(f"Fatal error in main loop: {e}", exc_info=True)
    finally:
        logger.info("Stopping application...")
        system_tray.stop()
        keyboard_monitor.stop()
        sys.exit(0)


if __name__ == "__main__":
    try:
        lock_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        lock_socket.bind(("127.0.0.1", LOCK_PORT))
    except socket.error:
        print(f"Une autre instance de Minobot est déjà en cours d'exécution sur le port {LOCK_PORT}. Cette instance va se fermer.")
        sys.exit(0)

    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nApplication stopped by user")
    finally:
        if lock_socket:
            lock_socket.close()
        sys.exit(0)
