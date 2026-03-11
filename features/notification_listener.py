import asyncio
import logging
import os
import sqlite3
import xml.etree.ElementTree as ET
from typing import Optional, Dict, Any, List, Union

from core.focus_manager import FocusManager
from core.window_manager import WindowManager


class NotificationListener:
    """
    Listens for Windows notifications in real-time by polling the system notification database.
    This method is more reliable than the WinRT API for detecting new notifications from specific apps.
    """

    def __init__(
            self,
            logger: logging.Logger,
            window_manager: WindowManager,
            focus_manager: FocusManager,
            config: Dict[str, Any]
    ):
        """
        Initializes the NotificationListener.

        Args:
            logger: The application logger.
            window_manager: The manager for tracking game windows.
            focus_manager: The manager for focusing windows.
            config: The application configuration dictionary.
        """
        self.logger: logging.Logger = logger
        self.window_manager: WindowManager = window_manager
        self.focus_manager: FocusManager = focus_manager
        self.config: Dict[str, Any] = config

        # Path to the Windows notification database
        self.db_path: str = os.path.expandvars(
            r"%LOCALAPPDATA%\Microsoft\Windows\Notifications\wpndatabase.db"
        )
        self.last_id: int = 0
        self.connection: Optional[sqlite3.Connection] = None

    async def start(self) -> None:
        """
        Starts the notification listening process.
        Connects to the database and enters the polling loop.
        """
        self.logger.info("Starting Windows notification listener...")

        if not os.path.exists(self.db_path):
            self.logger.error(f"Notification database not found at {self.db_path}")
            return

        try:
            # Open database connection in read-only mode if possible, but standard connect works
            # We use WAL mode to avoid locking issues
            self.connection = sqlite3.connect(self.db_path)
            self.connection.execute("PRAGMA journal_mode=WAL")

            self.logger.info("Connected to Windows notification database")
            self.logger.info("Listening for notifications...")

            await self._listen_loop()

        except Exception as e:
            self.logger.error(f"Fatal error in notification listener: {e}", exc_info=True)

        finally:
            if self.connection:
                self.connection.close()
                self.logger.info("Database connection closed")

    async def _listen_loop(self) -> None:
        """
        Main loop that polls the database for new notifications.
        """
        poll_interval: float = float(self.config.get("poll_interval", 0.5))
        batch_size: int = int(self.config.get("notification_batch_size", 10))

        while True:
            try:
                if not self.connection:
                    break

                # Fetch latest notifications
                cursor = self.connection.execute(
                    """
                    SELECT Id, Payload
                    FROM Notification
                    ORDER BY Id DESC LIMIT ?
                    """,
                    (batch_size,)
                )

                rows = cursor.fetchall()

                # Process notifications in chronological order (oldest first within the batch)
                # But we fetched DESC, so we reverse the list
                for notif_id, payload in reversed(rows):
                    if notif_id <= self.last_id:
                        continue

                    self.last_id = notif_id
                    self.logger.debug(f"[NEW NOTIF] ID {notif_id}")
                    await self._process_notification_payload(payload)

            except sqlite3.Error as e:
                self.logger.error(f"Database error: {e}")
                await asyncio.sleep(poll_interval * 2)

            except Exception as e:
                self.logger.error(f"Unexpected error in listen loop: {e}")

            await asyncio.sleep(poll_interval)

    async def _process_notification_payload(self, payload: Union[str, bytes]) -> None:
        """
        Parses the XML payload from the database.

        Args:
            payload: The raw payload (string or bytes) containing the notification XML.
        """
        if not payload:
            return

        # Convert bytes to string if necessary
        if isinstance(payload, bytes):
            payload_str = payload.decode("utf-8", errors="ignore")
        else:
            payload_str = payload

        if "<toast" not in payload_str:
            return

        self.logger.debug(f"[RAW NOTIF] {payload_str}")

        try:
            # Parse XML
            root = ET.fromstring(payload_str)
            texts = [elem.text for elem in root.iter("text") if elem.text]

            if not texts:
                return

            title = texts[0]
            message = texts[1] if len(texts) > 1 else ""

            await self.process_notification_content(title, message)

        except ET.ParseError as e:
            self.logger.debug(f"[XML PARSE ERROR] {e}")
        except Exception as e:
            self.logger.error(f"Error processing notification payload: {e}")

    async def process_notification_content(self, title: str, message: str) -> None:
        """
        Processes the extracted content of a notification.
        If it matches game keywords, it focuses the relevant window.

        Args:
            title: The title of the notification.
            message: The body text of the notification.
        """
        if not title:
            return

        # Filter by configurable keywords
        game_keywords: List[str] = self.config.get("game_keywords", ["Dofus"])

        if not any(keyword in title for keyword in game_keywords):
            return

        self.logger.debug(f"[NOTIF TITLE] {title}")
        self.logger.debug(f"[NOTIF TEXT] {message}")

        character = self._extract_character_name(title)

        if not character:
            self.logger.warning(f"[CHAR EXTRACTION FAILED] {title}")
            return

        self.window_manager.ensure_fresh()
        hwnd = self.window_manager.find_window(character)

        if hwnd:
            await self.focus_manager.focus(hwnd)
        else:
            self.logger.warning(f"[WINDOW NOT FOUND] {character}")

    def _extract_character_name(self, title: str) -> Optional[str]:
        """
        Extracts the character name from the notification title based on separators.

        Args:
            title: The notification title.

        Returns:
            The extracted name or the cleaned title.
        """
        separators: List[str] = self.config.get("character_separators", [" - ", ": ", " | "])

        for separator in separators:
            if separator in title:
                return title.split(separator)[0].strip()

        return title.strip()
