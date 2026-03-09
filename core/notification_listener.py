import sqlite3
import os
import time
import xml.etree.ElementTree as ET


class NotificationListener:

    def __init__(self, logger, window_manager, focus_manager):

        self.logger = logger
        self.window_manager = window_manager
        self.focus_manager = focus_manager

        self.db_path = os.path.expandvars(
            r"%LOCALAPPDATA%\Microsoft\Windows\Notifications\wpndatabase.db"
        )

        self.last_id = 0

    def start(self):

        self.logger.info("Opening Windows notification database")

        if not os.path.exists(self.db_path):
            self.logger.error("Notification database not found")
            return

        conn = sqlite3.connect(self.db_path)

        conn.execute("PRAGMA journal_mode=WAL")

        self.logger.info("Listening for notifications...")

        while True:

            try:

                cursor = conn.execute(
                    """
                    SELECT Id, Payload
                    FROM Notification
                    ORDER BY Id DESC
                        LIMIT 10
                    """
                )

                rows = cursor.fetchall()

                for notif_id, payload in reversed(rows):

                    if notif_id <= self.last_id:
                        continue

                    self.last_id = notif_id

                    self.logger.debug(f"[DB] New notification {notif_id}")

                    self.process_notification(payload)

            except Exception as e:

                self.logger.error(f"[DB ERROR] {e}")

            time.sleep(0.5)

    def process_notification(self, payload):

        if not payload:
            return

        # convertir bytes → string
        if isinstance(payload, bytes):
            payload = payload.decode("utf-8", errors="ignore")

        if "<toast" not in payload:
            return

        self.logger.debug(f"[RAW NOTIF] {payload}")

        try:
            root = ET.fromstring(payload)
        except Exception:
            return

        texts = [elem.text for elem in root.iter("text") if elem.text]

        if not texts:
            return

        title = texts[0]
        message = texts[1] if len(texts) > 1 else ""

        if "Dofus" not in title:
            return

        self.logger.info(f"[NOTIF TITLE] {title}")
        self.logger.info(f"[NOTIF TEXT] {message}")

        character = title.split(" - ")[0].strip()

        self.logger.info(f"[CHAR] {character}")

        hwnd = self.window_manager.find_window(character)

        if hwnd:
            self.focus_manager.focus(hwnd)
        else:
            self.logger.warning(f"[WINDOW NOT FOUND] {character}")