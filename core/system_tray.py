import pystray
from PIL import Image, ImageDraw
import threading
import sys
import os


class SystemTrayManager:
    """Gestionnaire de l'icône système dans la barre des tâches Windows"""

    def __init__(self, logger, on_quit_callback=None):
        self.logger = logger
        self.on_quit_callback = on_quit_callback
        self.icon = None
        self.tray_thread = None

    def create_image(self):
        """Crée une icône personnalisée : M doré sur fond marron"""
        width = 64
        height = 64
        # Fond marron
        color_brown = '#5D4037'
        color_gold = '#FFD700'
        
        image = Image.new('RGB', (width, height), color=color_brown)
        dc = ImageDraw.Draw(image)

        # Dessiner un M doré
        coords = [
            (12, 52), 
            (12, 12), 
            (32, 32), 
            (52, 12), 
            (52, 52)
        ]
        
        # Dessiner les lignes avec une épaisseur
        dc.line(coords, fill=color_gold, width=8)

        return image

    def show_window(self, icon, item):
        """Affiche la fenêtre console"""
        self.logger.info("Showing console window (requested from system tray)")
        # Note: La fenêtre console ne peut pas vraiment être "cachée" puis "montrée"
        # dans une application Python standard, mais on peut gérer ça différemment
        # selon les besoins

    def quit_application(self, icon, item):
        """Quitte l'application"""
        self.logger.info("Quitting application from system tray")
        icon.stop()
        if self.on_quit_callback:
            self.on_quit_callback()
        else:
            os._exit(0)

    def start(self):
        """Démarre l'icône système dans un thread séparé"""
        def run_tray():
            # Créer le menu
            menu = pystray.Menu(
                pystray.MenuItem("Minobot", lambda: None, enabled=False),
                pystray.Menu.SEPARATOR,
                pystray.MenuItem("Quitter", self.quit_application)
            )

            # Créer l'icône
            self.icon = pystray.Icon(
                "minobot",
                self.create_image(),
                "Minobot",
                menu
            )

            self.logger.info("System tray icon started")
            self.icon.run()

        # Lancer le system tray dans un thread séparé
        self.tray_thread = threading.Thread(target=run_tray, daemon=True)
        self.tray_thread.start()
        self.logger.info("System tray thread started")

    def stop(self):
        """Arrête l'icône système"""
        if self.icon:
            self.icon.stop()
            self.logger.info("System tray stopped")
