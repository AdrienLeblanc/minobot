import logging
import sys
import threading
from typing import Optional, Callable

import pystray
from PIL import Image, ImageDraw


class SystemTrayManager:
    """
    Manages the system tray icon for the application.
    """

    def __init__(self, logger: logging.Logger, on_quit_callback: Optional[Callable[[], None]] = None):
        """
        Initializes the SystemTrayManager.

        Args:
            logger: The application logger.
            on_quit_callback: A function to call when the "Quit" menu item is selected.
                              If None, os._exit(0) is used as a fallback.
        """
        self.logger: logging.Logger = logger
        self.on_quit_callback: Optional[Callable[[], None]] = on_quit_callback
        self.icon: Optional[pystray.Icon] = None
        self.tray_thread: Optional[threading.Thread] = None

    def _create_image(self) -> Image.Image:
        """
        Creates a custom icon image: a golden 'M' on a brown background.

        Returns:
            A PIL Image object.
        """
        width, height = 64, 64
        color_brown = '#5D4037'
        color_gold = '#FFD700'

        image = Image.new('RGB', (width, height), color=color_brown)
        dc = ImageDraw.Draw(image)

        # Draw a golden 'M'
        coords = [
            (12, 52), (12, 12),
            (32, 32),
            (52, 12), (52, 52)
        ]
        dc.line(coords, fill=color_gold, width=8)

        return image

    def _quit_application(self) -> None:
        """
        Handles the application quit logic from the system tray.
        """
        self.logger.info("Quitting application from system tray...")
        if self.icon:
            self.icon.stop()

        if self.on_quit_callback:
            self.on_quit_callback()
        else:
            # Fallback if no callback is provided
            sys.exit(0)

    def start(self) -> None:
        """
        Starts the system tray icon in a separate daemon thread.
        """
        if self.tray_thread and self.tray_thread.is_alive():
            self.logger.warning("System tray thread is already running.")
            return

        def run_tray() -> None:
            """The target function for the tray thread."""
            menu = pystray.Menu(
                pystray.MenuItem("Minobot", lambda: None, enabled=False),
                pystray.Menu.SEPARATOR,
                pystray.MenuItem("Quit", self._quit_application)
            )

            self.icon = pystray.Icon(
                "minobot",
                self._create_image(),
                "Minobot",
                menu
            )

            self.logger.debug("System tray icon started.")
            self.icon.run()
            self.logger.debug("System tray icon finished run.")

        self.tray_thread = threading.Thread(target=run_tray, daemon=True)
        self.tray_thread.start()
        self.logger.info("System tray thread started.")

    def stop(self) -> None:
        """
        Stops the system tray icon.
        """
        if self.icon:
            self.icon.stop()
            self.logger.info("System tray stopped.")
