import win32gui
import win32api
import win32con
import asyncio
import time
from typing import Tuple


class MultiWindowClicker:
    """
    Gère les clics synchronisés sur plusieurs fenêtres.
    Optimisé pour la vitesse maximale.
    """

    def __init__(self, logger, window_manager, config):
        self.logger = logger
        self.window_manager = window_manager
        self.config = config

        self.click_delay = config.get("multiclick_delay", 0.01)  # Délai entre les clics (10ms par défaut)
        self.restore_original_window = config.get("multiclick_restore_focus", True)

    async def click_all_windows(self, screen_position: Tuple[int, int]):
        """
        Clique sur toutes les fenêtres de jeu à la position relative équivalente.

        Args:
            screen_position: Position absolue (x, y) sur l'écran où le clic a été effectué
        """

        start_time = time.time()

        # Sauvegarder la fenêtre active actuelle
        original_window = win32gui.GetForegroundWindow() if self.restore_original_window else None

        # Rafraîchir la liste des fenêtres si nécessaire
        await self.window_manager.ensure_fresh()

        windows = list(self.window_manager.windows.values())

        if not windows:
            self.logger.warning("[MULTICLICK] No game windows found")
            return

        # Obtenir la fenêtre actuelle pour calculer la position relative
        current_window = win32gui.WindowFromPoint(screen_position)

        # Vérifier si le clic est sur une fenêtre de jeu
        if current_window not in windows:
            # Chercher la fenêtre parente qui pourrait être une fenêtre de jeu
            parent = win32gui.GetParent(current_window)
            while parent and parent not in windows:
                parent = win32gui.GetParent(parent)

            if parent and parent in windows:
                current_window = parent
            else:
                self.logger.debug("[MULTICLICK] Click not on a game window, using absolute position")
                current_window = None

        # Calculer la position relative si on a cliqué sur une fenêtre de jeu
        relative_pos = None
        if current_window:
            try:
                rect = win32gui.GetWindowRect(current_window)
                relative_pos = (
                    screen_position[0] - rect[0],
                    screen_position[1] - rect[1]
                )
                self.logger.debug(f"[MULTICLICK] Relative position: {relative_pos}")
            except:
                pass

        clicked_count = 0

        # Cliquer sur chaque fenêtre
        for hwnd in windows:
            try:
                # Vérifier que la fenêtre existe toujours
                if not win32gui.IsWindow(hwnd):
                    continue

                # Calculer la position de clic pour cette fenêtre
                if relative_pos:
                    # Utiliser la position relative
                    rect = win32gui.GetWindowRect(hwnd)
                    click_x = rect[0] + relative_pos[0]
                    click_y = rect[1] + relative_pos[1]
                else:
                    # Utiliser la position absolue
                    click_x, click_y = screen_position

                # Effectuer le clic sans changer le focus
                await self._click_at_position(hwnd, click_x, click_y)

                clicked_count += 1

                # Petit délai entre les clics pour ne pas surcharger
                if self.click_delay > 0:
                    await asyncio.sleep(self.click_delay)

            except Exception as e:
                self.logger.debug(f"[MULTICLICK] Error clicking window: {e}")
                continue

        # Restaurer la fenêtre originale
        if original_window and self.restore_original_window:
            try:
                win32gui.SetForegroundWindow(original_window)
            except:
                pass

        elapsed = (time.time() - start_time) * 1000
        self.logger.info(f"[MULTICLICK] Clicked {clicked_count} window(s) in {elapsed:.0f}ms")

    async def _click_at_position(self, hwnd, x: int, y: int):
        """
        Effectue un clic à une position spécifique sans changer le focus de la fenêtre.
        Utilise PostMessage pour envoyer les événements directement à la fenêtre.
        """

        try:
            # Convertir les coordonnées écran en coordonnées fenêtre
            rect = win32gui.GetWindowRect(hwnd)
            window_x = x - rect[0]
            window_y = y - rect[1]

            # Créer le lParam (coordonnées x et y combinées)
            lparam = win32api.MAKELONG(window_x, window_y)

            # Envoyer les événements de clic directement à la fenêtre
            # Utiliser PostMessage pour ne pas bloquer et envoyer rapidement
            win32gui.PostMessage(hwnd, win32con.WM_LBUTTONDOWN, win32con.MK_LBUTTON, lparam)
            win32gui.PostMessage(hwnd, win32con.WM_LBUTTONUP, 0, lparam)

        except Exception as e:
            self.logger.debug(f"[CLICK ERROR] {e}")
            # Fallback : méthode alternative avec SetCursorPos + mouse_event
            try:
                old_pos = win32api.GetCursorPos()
                win32api.SetCursorPos((x, y))
                win32api.mouse_event(win32con.MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0)
                win32api.mouse_event(win32con.MOUSEEVENTF_LEFTUP, 0, 0, 0, 0)
                win32api.SetCursorPos(old_pos)
            except Exception as e2:
                self.logger.debug(f"[CLICK FALLBACK ERROR] {e2}")
