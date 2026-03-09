import win32gui
import win32con
import win32process
import win32api
import ctypes
import asyncio
import time


class FocusManager:

    def __init__(self, logger, config):

        self.logger = logger
        self.config = config
        self.last_focus = None
        self.last_time = 0
        self.cooldown = config.get("focus_cooldown", 1)

        # Désactiver le fallback souris si multiclick est actif (éviter conflit)
        self.multiclick_enabled = config.get("multiclick_enabled", False)

        # Obtenir les fonctions Windows nécessaires
        self.user32 = ctypes.windll.user32
        self.kernel32 = ctypes.windll.kernel32

    async def focus(self, hwnd):

        now = time.time()

        if hwnd == self.last_focus and now - self.last_time < self.cooldown:
            return

        self.last_focus = hwnd
        self.last_time = now

        try:

            title = win32gui.GetWindowText(hwnd)

            # Restaurer la fenêtre si minimisée
            if win32gui.IsIconic(hwnd):
                win32gui.ShowWindow(hwnd, win32con.SW_RESTORE)
                await asyncio.sleep(0.1)  # Laisser le temps à la restauration

            # Méthode améliorée pour le focus sans simulation de touche
            success = self._set_foreground_window_safe(hwnd)

            if success:
                self.logger.info(f"[FOCUS OK] {title}")
            else:
                self.logger.warning(f"[FOCUS PARTIAL] {title}")

        except Exception as e:

            self.logger.error(f"[FOCUS ERROR] {e}")

    def _set_foreground_window_safe(self, hwnd) -> bool:
        """
        Méthode sécurisée pour mettre une fenêtre au premier plan
        Utilise plusieurs méthodes en fallback pour contourner les restrictions Windows
        """

        try:
            # Vérifier que la fenêtre existe toujours
            if not win32gui.IsWindow(hwnd):
                self.logger.warning("[FOCUS] Window no longer exists")
                return False

            # S'assurer que la fenêtre est visible et non minimisée
            if win32gui.IsIconic(hwnd):
                win32gui.ShowWindow(hwnd, win32con.SW_RESTORE)
                time.sleep(0.05)  # Petit délai pour la restauration

            # Obtenir le thread de la fenêtre cible
            target_thread_id, target_pid = win32process.GetWindowThreadProcessId(hwnd)

            # Obtenir le thread actuel (foreground)
            foreground_hwnd = self.user32.GetForegroundWindow()
            if foreground_hwnd and foreground_hwnd != hwnd:
                current_thread_id, _ = win32process.GetWindowThreadProcessId(foreground_hwnd)
            else:
                current_thread_id = self.kernel32.GetCurrentThreadId()

            # Méthode 1 : Autoriser notre processus à définir le foreground
            ASFW_ANY = -1
            try:
                self.user32.AllowSetForegroundWindow(ASFW_ANY)
            except:
                pass

            # Attacher les threads si différents
            attached = False
            if target_thread_id != current_thread_id:
                try:
                    attached = self.user32.AttachThreadInput(
                        current_thread_id,
                        target_thread_id,
                        True
                    )
                except:
                    pass

            try:
                # Afficher la fenêtre
                win32gui.ShowWindow(hwnd, win32con.SW_SHOW)
                win32gui.BringWindowToTop(hwnd)

                # Essayer SetForegroundWindow
                try:
                    win32gui.SetForegroundWindow(hwnd)
                except:
                    # Si SetForegroundWindow échoue, essayer SetActiveWindow
                    try:
                        win32gui.SetActiveWindow(hwnd)
                    except:
                        pass

                return True

            finally:
                # Détacher les threads
                if attached:
                    try:
                        self.user32.AttachThreadInput(
                            current_thread_id,
                            target_thread_id,
                            False
                        )
                    except:
                        pass

        except Exception as e:
            self.logger.debug(f"[FOCUS METHOD 1 FAILED] {e}, trying alternative methods")

        # Fallback 1 : SwitchToThisWindow (plus permissif)
        try:
            self.user32.SwitchToThisWindow(hwnd, True)
            self.logger.debug("[FOCUS] Used SwitchToThisWindow")
            return True
        except Exception as e:
            self.logger.debug(f"[FOCUS METHOD 2 FAILED] {e}")

        # Fallback 2 : Simuler un clic sur la barre de titre (seulement si multiclick désactivé)
        if not self.multiclick_enabled:
            try:
                if win32gui.IsIconic(hwnd):
                    win32gui.ShowWindow(hwnd, win32con.SW_RESTORE)

                win32gui.ShowWindow(hwnd, win32con.SW_SHOW)
                win32gui.BringWindowToTop(hwnd)

                # Obtenir la position de la fenêtre
                rect = win32gui.GetWindowRect(hwnd)
                x = rect[0] + 100  # Position dans la barre de titre
                y = rect[1] + 10

                # Sauvegarder la position actuelle de la souris
                old_pos = win32api.GetCursorPos()

                # Cliquer sur la barre de titre (Windows permet ça)
                win32api.SetCursorPos((x, y))
                time.sleep(0.01)
                win32api.mouse_event(win32con.MOUSEEVENTF_LEFTDOWN, 0, 0, 0, 0)
                win32api.mouse_event(win32con.MOUSEEVENTF_LEFTUP, 0, 0, 0, 0)

                # Restaurer la position de la souris
                win32api.SetCursorPos(old_pos)

                self.logger.debug("[FOCUS] Used mouse click method")
                return True

            except Exception as e:
                self.logger.debug(f"[FOCUS METHOD 3 FAILED] {e}")

        # Fallback 3 : Au moins afficher la fenêtre (succès partiel)
        try:
            if win32gui.IsIconic(hwnd):
                win32gui.ShowWindow(hwnd, win32con.SW_RESTORE)
            win32gui.ShowWindow(hwnd, win32con.SW_SHOW)
            win32gui.BringWindowToTop(hwnd)

            self.logger.warning("[FOCUS] Partial success - window shown but may not have focus")
            return True

        except Exception as e:
            self.logger.error(f"[FOCUS ALL METHODS FAILED] {e}")
            return False