import asyncio
import logging
import os
import sqlite3
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from typing import Optional, Dict, Any, List, Union, Callable, Awaitable

@dataclass
class Notification:
    """Represents a parsed Windows notification."""
    title: str
    message: str

# Type hint for an async callback that takes a Notification object
NotificationCallback = Callable[[Notification], Awaitable[None]]

class NotificationManager:
    """
    Connects to the Windows notification database, polls for new entries,
    parses them, and dispatches them to registered callbacks.
    This is a core system component.
    """

    def __init__(self, logger: logging.Logger, config: Dict[str, Any]):
        """
        Initializes the NotificationManager.
        """
        self.logger: logging.Logger = logger
        self.config: Dict[str, Any] = config
        self.db_path: str = os.path.expandvars(
            r"%LOCALAPPDATA%\Microsoft\Windows\Notifications\wpndatabase.db"
        )
        self.last_id: int = 0
        self.connection: Optional[sqlite3.Connection] = None
        self.callbacks: List[NotificationCallback] = []

    def register_callback(self, callback: NotificationCallback):
        """Registers an async function to be called when a new notification is detected."""
        if asyncio.iscoroutinefunction(callback):
            self.callbacks.append(callback)
            self.logger.info(f"Registered notification callback: {callback.__name__}")
        else:
            self.logger.warning(f"Callback {callback.__name__} is not an async function and was not registered.")

    async def start(self) -> None:
        """
        Starts the notification listening process.
        """
        self.logger.info("Starting Core Notification Manager...")
        if not os.path.exists(self.db_path):
            self.logger.error(f"Notification database not found at {self.db_path}")
            return

        try:
            self.connection = sqlite3.connect(f"file:{self.db_path}?mode=ro", uri=True)
            self.connection.execute("PRAGMA journal_mode=WAL")
            self.logger.info("Connected to Windows notification database (read-only).")
            await self._listen_loop()
        except sqlite3.OperationalError:
            self.logger.warning("Read-only connection failed, falling back to read-write.")
            try:
                self.connection = sqlite3.connect(self.db_path)
                self.connection.execute("PRAGMA journal_mode=WAL")
                await self._listen_loop()
            except Exception as e:
                self.logger.error(f"Fatal error in notification manager (RW mode): {e}", exc_info=True)
        except Exception as e:
            self.logger.error(f"Fatal error in notification manager: {e}", exc_info=True)
        finally:
            if self.connection:
                self.connection.close()
                self.logger.info("Notification database connection closed.")

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
                cursor = self.connection.execute(
                    "SELECT Id, Payload FROM Notification ORDER BY Id DESC LIMIT ?", (batch_size,)
                )
                rows = cursor.fetchall()

                for notif_id, payload in reversed(rows):
                    if notif_id > self.last_id:
                        self.last_id = notif_id
                        await self._process_and_dispatch(payload)
            except sqlite3.Error as e:
                self.logger.error(f"Database error in polling loop: {e}")
                await asyncio.sleep(poll_interval * 2)
            except Exception as e:
                self.logger.error(f"Unexpected error in polling loop: {e}")
            
            await asyncio.sleep(poll_interval)

    async def _process_and_dispatch(self, payload: Union[str, bytes]) -> None:
        """
        Parses the raw payload and dispatches it to all registered callbacks.
        """
        if not payload:
            return
        
        payload_str = payload.decode("utf-8", errors="ignore") if isinstance(payload, bytes) else payload
        if "<toast" not in payload_str:
            return

        try:
            root = ET.fromstring(payload_str)
            texts = [elem.text for elem in root.iter("text") if elem.text]
            if not texts:
                return

            notification = Notification(
                title=texts[0],
                message=texts[1] if len(texts) > 1 else ""
            )

            # Dispatch to all registered callbacks concurrently
            tasks = [callback(notification) for callback in self.callbacks]
            await asyncio.gather(*tasks)

        except ET.ParseError:
            self.logger.debug(f"Ignoring XML parse error for payload: {payload_str[:150]}")
        except Exception as e:
            self.logger.error(f"Error processing notification payload: {e}")
