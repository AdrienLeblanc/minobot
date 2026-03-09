import asyncio
import xml.etree.ElementTree as ET
from typing import Optional
import os
import sqlite3
import time


class NotificationListener:
    """
    Écoute les notifications Windows en temps réel via polling de la base de données système.
    Cette méthode est plus fiable que l'API WinRT pour détecter les nouvelles notifications.
    """

    def __init__(self, logger, window_manager, focus_manager, config):

        self.logger = logger
        self.window_manager = window_manager
        self.focus_manager = focus_manager
        self.config = config

        # Base de données des notifications Windows
        self.db_path = os.path.expandvars(
            r"%LOCALAPPDATA%\Microsoft\Windows\Notifications\wpndatabase.db"
        )
        self.last_id = 0
        self.connection = None

    async def start(self):
        """Démarre l'écoute des notifications"""

        self.logger.info("Starting Windows notification listener...")

        if not os.path.exists(self.db_path):
            self.logger.error(f"Notification database not found at {self.db_path}")
            return

        try:
            # Ouvrir la connexion à la base de données
            self.connection = sqlite3.connect(self.db_path)
            self.connection.execute("PRAGMA journal_mode=WAL")

            self.logger.info("Connected to Windows notification database")
            self.logger.info("Listening for notifications...")

            # Boucle principale d'écoute
            await self._listen_loop()

        except Exception as e:
            self.logger.error(f"Fatal error in notification listener: {e}", exc_info=True)

        finally:
            # Fermer proprement la connexion
            if self.connection:
                try:
                    self.connection.close()
                    self.logger.info("Database connection closed")
                except:
                    pass

    async def _listen_loop(self):
        """Boucle principale qui poll la base de données pour détecter les nouvelles notifications"""

        poll_interval = self.config.get("poll_interval", 0.5)
        batch_size = self.config.get("notification_batch_size", 10)

        while True:
            try:
                # Récupérer les dernières notifications
                cursor = self.connection.execute(
                    """
                    SELECT Id, Payload
                    FROM Notification
                    ORDER BY Id DESC
                    LIMIT ?
                    """,
                    (batch_size,)
                )

                rows = cursor.fetchall()

                # Traiter les notifications dans l'ordre chronologique (plus anciennes en premier)
                for notif_id, payload in reversed(rows):

                    if notif_id <= self.last_id:
                        continue

                    self.last_id = notif_id

                    self.logger.debug(f"[NEW NOTIF] ID {notif_id}")

                    await self._process_notification_payload(payload)

            except sqlite3.Error as e:
                self.logger.error(f"Database error: {e}")
                await asyncio.sleep(poll_interval * 2)  # Attendre plus longtemps en cas d'erreur

            except Exception as e:
                self.logger.error(f"Unexpected error in listen loop: {e}")

            await asyncio.sleep(poll_interval)

    async def _process_notification_payload(self, payload):
        """Traite le payload XML de la base de données"""

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
        except Exception as e:
            self.logger.debug(f"[XML PARSE ERROR] {e}")
            return

        texts = [elem.text for elem in root.iter("text") if elem.text]

        if not texts:
            return

        title = texts[0]
        message = texts[1] if len(texts) > 1 else ""

        await self.process_notification_content(title, message)

    async def process_notification_content(self, title: str, message: str):
        """Traite le contenu d'une notification (titre et message)"""

        if not title:
            return

        # Filtrer selon les patterns configurables
        game_keywords = self.config.get("game_keywords", ["Dofus"])

        if not any(keyword in title for keyword in game_keywords):
            return

        self.logger.info(f"[NOTIF TITLE] {title}")
        self.logger.info(f"[NOTIF TEXT] {message}")

        # Extraction du nom de personnage avec gestion robuste
        character = self._extract_character_name(title)

        if not character:
            self.logger.warning(f"[CHAR EXTRACTION FAILED] {title}")
            return

        self.logger.info(f"[CHAR] {character}")

        # Rafraîchir les fenêtres si nécessaire
        await self.window_manager.ensure_fresh()

        hwnd = self.window_manager.find_window(character)

        if hwnd:
            await self.focus_manager.focus(hwnd)
        else:
            self.logger.warning(f"[WINDOW NOT FOUND] {character}")

    def _extract_character_name(self, title: str) -> Optional[str]:
        """Extrait le nom du personnage depuis le titre de notification"""

        # Patterns configurables pour l'extraction
        separators = self.config.get("character_separators", [" - ", ": ", " | "])

        for separator in separators:
            if separator in title:
                return title.split(separator)[0].strip()

        # Si aucun séparateur trouvé, retourner le titre complet nettoyé
        return title.strip()