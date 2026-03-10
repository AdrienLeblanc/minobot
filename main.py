import asyncio
import sys

from core.logger import setup_logger
from core.config_loader import load_config
from core.window_manager import WindowManager
from core.focus_manager import FocusManager
from core.notification_listener import NotificationListener
from core.keyboard_monitor import KeyboardMonitor
from core.multi_window_clicker import MultiWindowClicker
from core.system_tray import SystemTrayManager


async def main():
    """Point d'entrée principal de l'application"""

    # Charger la configuration
    config = load_config("config.json")

    # Configurer le logger avec la config
    logger = setup_logger(config)

    logger.info("=== Minobot ===")
    logger.info("Starting application...")

    # Initialiser le system tray
    system_tray = SystemTrayManager(logger)
    system_tray.start()
    logger.info("System tray icon added to taskbar")

    try:
        # Initialiser les composants avec la config
        window_manager = WindowManager(logger, config)
        window_manager.refresh()

        focus_manager = FocusManager(logger, config)

        # Composants pour le multiclick
        multi_clicker = MultiWindowClicker(logger, window_manager, config)

        # Vérifier si le multiclick est activé
        multiclick_enabled = config.get("multiclick_enabled", True)

        if multiclick_enabled:
            keyboard_monitor = KeyboardMonitor(logger, config)
            keyboard_monitor.set_trigger_callback(multi_clicker.click_all_windows)
            logger.info("Multi-window click feature enabled")
        else:
            keyboard_monitor = None
            logger.info("Multi-window click feature disabled")

        # Composant pour les notifications
        listener = NotificationListener(
            logger,
            window_manager,
            focus_manager,
            config
        )

        # Démarrer les tâches en parallèle
        tasks = [
            asyncio.create_task(listener.start(), name="notification_listener")
        ]

        if keyboard_monitor:
            tasks.append(
                asyncio.create_task(keyboard_monitor.start(), name="keyboard_monitor")
            )

        # Attendre que toutes les tâches se terminent (ou qu'une échoue)
        await asyncio.gather(*tasks)

    except KeyboardInterrupt:
        logger.info("Application interrupted by user")
        system_tray.stop()
        sys.exit(0)

    except Exception as e:
        logger.error(f"Fatal error: {e}", exc_info=True)
        system_tray.stop()
        sys.exit(1)


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\nApplication stopped by user")
        sys.exit(0)