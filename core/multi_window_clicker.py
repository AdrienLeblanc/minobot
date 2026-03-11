import asyncio
import ctypes
import time
from ctypes import wintypes
from typing import Tuple

import win32api
import win32con
import win32gui

user32 = ctypes.WinDLL('user32', use_last_error=True)


# Structure pour FlashWindowEx (arrêter le clignotement orange de la taskbar)
class FLASHWINFO(ctypes.Structure):
    _fields_ = (('cbSize', wintypes.UINT),
                ('hwnd', wintypes.HWND),
                ('dwFlags', wintypes.DWORD),
                ('uCount', wintypes.UINT),
                ('dwTimeout', wintypes.DWORD))


user32.FlashWindowEx.argtypes = (ctypes.POINTER(FLASHWINFO),)


class MultiWindowClicker:
    """
    Gère les clics synchronisés sur plusieurs fenêtres.
    Optimisé pour la vitesse maximale avec support multi-boutons.
    """

    def __init__(self, logger, window_manager, config):
        self.logger = logger
        self.window_manager = window_manager
        self.config = config

        self.click_delay = config.get("multiclick_delay", 0.01)
        self.restore_original_window = config.get("multiclick_restore_focus", True)
        self.dry_run = config.get("multiclick_dry_run", False)
        self.exclude_list = config.get("multiclick_exclude", [])
        self.click_button = config.get("multiclick_button", "left")

        # Statistiques
        self.stats = {
            "total_triggers": 0,
            "total_clicks": 0,
            "total_failures": 0,
            "total_time_ms": 0
        }

        if self.dry_run:
            self.logger.warning("[MULTICLICK] DRY RUN MODE - No actual clicks will be sent")

    async def click_all_windows(self, screen_position: Tuple[int, int]):
        """
        Clique sur toutes les fenêtres de jeu à la position relative équivalente.

        Args:
            screen_position: Position absolue (x, y) sur l'écran où le clic a été effectué
        """

        start_time = time.time()
        self.stats["total_triggers"] += 1

        # Sauvegarder la fenêtre active actuelle
        original_window = win32gui.GetForegroundWindow() if self.restore_original_window else None

        # Rafraîchir la liste des fenêtres si nécessaire
        self.window_manager.ensure_fresh()

        windows = list(self.window_manager.windows.items())  # (title, hwnd) pairs

        if not windows:
            self.logger.warning("[MULTICLICK] No game windows found")
            return

        # Filtrer les fenêtres blacklistées
        if self.exclude_list:
            windows = [
                (title, hwnd) for title, hwnd in windows
                if not any(excluded.lower() in title.lower() for excluded in self.exclude_list)
            ]

        # Trier les fenêtres par nom pour ordre consistant
        windows.sort(key=lambda x: x[0])

        # Obtenir la fenêtre actuelle pour calculer la position relative
        current_window = win32gui.WindowFromPoint(screen_position)
        window_hwnds = [hwnd for _, hwnd in windows]

        # Chercher la fenêtre parente avec limite d'itérations
        if current_window not in window_hwnds:
            parent = win32gui.GetParent(current_window)
            depth = 0
            max_depth = 10

            while parent and parent not in window_hwnds and depth < max_depth:
                parent = win32gui.GetParent(parent)
                depth += 1

            if parent and parent in window_hwnds:
                current_window = parent
            else:
                self.logger.debug("[MULTICLICK] Click not on a game window, using absolute position")
                current_window = None

        # Calculer la position relative en coordonnées CLIENT
        relative_client_pos = None

        if current_window:
            try:
                # Convertir screen position en coordonnées client de la fenêtre source
                point = (screen_position[0], screen_position[1])
                client_point = win32gui.ScreenToClient(current_window, point)

                relative_client_pos = client_point

                self.logger.debug(
                    f"[MULTICLICK] Screen pos: {screen_position}, Client pos in source window: {relative_client_pos}"
                )
            except Exception as e:
                self.logger.debug(f"[MULTICLICK] Error calculating relative pos: {e}")

        clicked_count = 0
        failed_count = 0

        # Cliquer sur chaque fenêtre
        for title, hwnd in windows:
            try:
                # Vérifier que la fenêtre existe toujours
                if not win32gui.IsWindow(hwnd):
                    continue

                # Ne pas cliquer sur fenêtres minimisées
                if win32gui.IsIconic(hwnd):
                    self.logger.debug(f"[MULTICLICK] Skipping minimized window: {title}")
                    continue

                # Calculer la position de clic pour cette fenêtre
                if relative_client_pos:
                    # Convertir la position client relative en position écran pour cette fenêtre
                    try:
                        screen_point = win32gui.ClientToScreen(hwnd, relative_client_pos)
                        click_x, click_y = screen_point
                        self.logger.debug(
                            f"[MULTICLICK] {title}: client {relative_client_pos} -> screen ({click_x},{click_y})")
                    except Exception as e:
                        self.logger.debug(f"[MULTICLICK] ClientToScreen failed for {title}: {e}")
                        # Fallback: position absolue
                        click_x, click_y = screen_position
                else:
                    # Utiliser la position absolue
                    click_x, click_y = screen_position

                # Effectuer le clic directement sans changer le focus
                # Note: Cela causera un flash orange dans la taskbar pour les fenêtres
                # qui n'ont pas le focus, mais tous les personnages bougeront correctement
                max_retries = 2
                success = False

                for attempt in range(max_retries):
                    success = self._click_at_position(hwnd, click_x, click_y, title)

                    if success:
                        break

                    if attempt < max_retries - 1:
                        self.logger.debug(f"[MULTICLICK] Retry {attempt + 1} for {title}")
                        await asyncio.sleep(0.005)  # 5ms avant retry

                if success:
                    clicked_count += 1
                else:
                    failed_count += 1
                    self.logger.warning(f"[MULTICLICK] Failed to click {title} after {max_retries} attempts")

                # Petit délai entre les clics
                if self.click_delay > 0:
                    await asyncio.sleep(self.click_delay)

            except Exception as e:
                self.logger.debug(f"[MULTICLICK] Error clicking window {title}: {e}")
                failed_count += 1
                continue

        # Restaurer la fenêtre originale
        if original_window and self.restore_original_window:
            win32gui.SetForegroundWindow(original_window)

        # Statistiques
        elapsed = (time.time() - start_time) * 1000
        self.stats["total_clicks"] += clicked_count
        self.stats["total_failures"] += failed_count
        self.stats["total_time_ms"] += elapsed

        avg_time = self.stats["total_time_ms"] / self.stats["total_triggers"] if self.stats["total_triggers"] > 0 else 0

        self.logger.info(
            f"[MULTICLICK] {clicked_count} OK, {failed_count} failed in {elapsed:.0f}ms "
            f"(avg: {avg_time:.0f}ms, total: {self.stats['total_clicks']} clicks)"
        )

    def _stop_taskbar_flash(self, hwnd: int, repeat: int = 1) -> None:
        """
        Arrête le clignotement orange de la taskbar en appelant FlashWindowEx(FLASHW_STOP).

        Args:
            hwnd: Handle de la fenêtre
            repeat: Nombre de fois à appeler (1 ou plus pour "spam" et rattraper le flash)
        """
        try:
            flash_info = FLASHWINFO(
                cbSize=ctypes.sizeof(FLASHWINFO),
                hwnd=hwnd,
                dwFlags=win32con.FLASHW_STOP,
                uCount=0,
                dwTimeout=0
            )
            for _ in range(repeat):
                user32.FlashWindowEx(ctypes.byref(flash_info))
        except Exception as e:
            self.logger.debug(f"[FLASH STOP ERROR]: {e}")

    def _click_at_position(self, hwnd: int, x: int, y: int, window_title: str = "") -> bool:
        """
        Utilise PostMessage avec coordonnées client + FlashWindowEx(FLASHW_STOP)
        pour arrêter le flash orange immédiatement après le clic.

        Args:
            hwnd: Handle de la fenêtre
            x: Position X en coordonnées écran
            y: Position Y en coordonnées écran
            window_title: Titre de la fenêtre (pour logs)

        Returns:
            True si succès, False sinon
        """

        if self.dry_run:
            self.logger.debug(f"[DRY RUN] Would click at ({x}, {y}) on {window_title}")
            return True

        try:
            # Convertir les coordonnées écran en coordonnées CLIENT de cette fenêtre
            client_point = win32gui.ScreenToClient(hwnd, (x, y))
            client_x, client_y = client_point

            # Créer le lParam (coordonnées client x et y combinées)
            lparam = win32api.MAKELONG(client_x, client_y)

            if self.config.get("log_level") == "DEBUG":
                self.logger.debug(f"[CLICK] {window_title}: screen ({x},{y}) -> client ({client_x},{client_y})")

            # Déterminer les messages selon le type de clic
            if self.click_button == "right":
                msg_down = win32con.WM_RBUTTONDOWN
                msg_up = win32con.WM_RBUTTONUP
                wparam = win32con.MK_RBUTTON
            elif self.click_button == "middle":
                msg_down = win32con.WM_MBUTTONDOWN
                msg_up = win32con.WM_MBUTTONUP
                wparam = win32con.MK_MBUTTON
            else:  # left (défaut)
                msg_down = win32con.WM_LBUTTONDOWN
                msg_up = win32con.WM_LBUTTONUP
                wparam = win32con.MK_LBUTTON

            # Désactiver le flash avant le clic (tentative préventive)
            self._stop_taskbar_flash(hwnd, repeat=1)

            # Envoyer les messages de clic
            win32gui.PostMessage(hwnd, msg_down, wparam, lparam)
            win32gui.PostMessage(hwnd, msg_up, 0, lparam)

            # Spam FLASHW_STOP après le clic pour "rattraper" le flash
            # PostMessage est asynchrone, donc on appelle plusieurs fois pour maximiser les chances
            self._stop_taskbar_flash(hwnd, repeat=5)

            return True

        except Exception as e:
            self.logger.debug(f"[CLICK ERROR] {window_title}: {e}")
            return False
