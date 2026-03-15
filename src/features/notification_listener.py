import asyncio
import logging
from typing import Dict, Any, List

from src.core.focus_manager import FocusManager
from src.core.notification_manager import NotificationManager, Notification
from src.core.window_manager import WindowManager


class NotificationListener:
    """
    Listens for game-specific notifications and triggers window focus.
    """

    def __init__(
            self,
            logger: logging.Logger,
            window_manager: WindowManager,
            focus_manager: FocusManager,
            notification_manager: NotificationManager,
            config: Dict[str, Any]
    ):
        """
        Initializes the NotificationListener.
        """
        self.logger: logging.Logger = logger
        self.window_manager: WindowManager = window_manager
        self.focus_manager: FocusManager = focus_manager
        self.notification_manager: NotificationManager = notification_manager
        self.config: Dict[str, Any] = config

        # Register our handler with the core notification system
        self.notification_manager.register_callback(self.handle_notification)

    async def handle_notification(self, notification: Notification) -> None:
        """
        Processes a notification received from the core manager.
        """
        if not notification.title:
            return

        # Filter by configurable keywords
        game_keywords: List[str] = self.config.get("game_keywords", ["Dofus"])
        
        # Check if title contains any keyword
        if not any(keyword in notification.title for keyword in game_keywords):
            return

        self.logger.debug(f"[GAME NOTIF] {notification.title} - {notification.message}")

        character = self.window_manager.extract_character_name(notification.title)
        if not character:
            self.logger.warning(f"[CHAR EXTRACTION FAILED] {notification.title}")
            return

        self.window_manager.ensure_fresh()
        hwnd = self.window_manager.find_window(character)

        if hwnd:
            # We use 'smart=True' to avoid stealing focus if the user is typing
            smart_focus_enabled = self.config.get("smart_focus_enabled", True)
            await self.focus_manager.focus(hwnd, smart=smart_focus_enabled)
        else:
            self.logger.warning(f"[WINDOW NOT FOUND] {character}")
