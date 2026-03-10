import time
import re
import win32gui
import win32con # Import pour les constantes de fenêtre

class GroupManager:
    """
    Gère la logique d'invitation de groupe en chaîne.
    """

    def __init__(self, logger, window_manager, input_simulator):
        self.logger = logger
        self.window_manager = window_manager
        self.input_simulator = input_simulator
        self.is_running = False

    def _extract_character_name(self, window_title):
        """
        Extrait le nom du personnage depuis le titre de la fenêtre.
        """
        match = re.search(r"(.+?)\s-\sDofus Retro", window_title)
        if match:
            name_part = match.group(1).strip()
            if ')' in name_part:
                name_part = name_part.split(')')[-1].strip()
            return name_part
        return None

    def _focus_window_by_hwnd(self, hwnd):
        """
        Met une fenêtre au premier plan en utilisant son HWND.
        """
        try:
            # Restaurer la fenêtre si elle est minimisée
            if win32gui.IsIconic(hwnd):
                win32gui.ShowWindow(hwnd, win32con.SW_RESTORE)
            win32gui.SetForegroundWindow(hwnd)
            self.logger.debug(f"Focused window with HWND: {hwnd}")
        except Exception as e:
            self.logger.error(f"Could not focus window with HWND {hwnd}: {e}")

    def invite_all(self):
        """
        Lance la séquence d'invitation de groupe pour tous les personnages détectés.
        """
        if self.is_running:
            self.logger.warning("Group invitation sequence already running.")
            return

        self.is_running = True
        self.logger.info("Starting group invitation sequence...")

        try:
            # S'assurer que la liste des fenêtres est à jour
            self.window_manager.refresh()
            
            # 1. Identifier le chef (fenêtre active)
            leader_hwnd = win32gui.GetForegroundWindow()
            leader_title = win32gui.GetWindowText(leader_hwnd)

            # Obtenir la liste des HWNDs des fenêtres Dofus connues
            dofus_hwnds = list(self.window_manager.windows.values())

            if leader_hwnd not in dofus_hwnds:
                self.logger.error(f"The active window ('{leader_title}') is not a recognized Dofus window.")
                self.is_running = False
                return

            leader_name = self._extract_character_name(leader_title)
            if not leader_name:
                self.logger.error(f"Could not extract character name from active window: '{leader_title}'")
                self.is_running = False
                return
            
            self.logger.info(f"Leader identified: {leader_name}")

            # 2. Récupérer la liste de tous les autres personnages
            member_windows = []
            for title, hwnd in self.window_manager.windows.items():
                if hwnd == leader_hwnd:
                    continue
                
                char_name = self._extract_character_name(title)
                if char_name:
                    member_windows.append((char_name, hwnd)) # Stocker directement le HWND

            if not member_windows:
                self.logger.info("No other characters found to invite.")
                self.is_running = False
                return

            self.logger.info(f"Found {len(member_windows)} members to invite: {[name for name, _ in member_windows]}")

            # 3. Exécuter la séquence d'invitation
            self._focus_window_by_hwnd(leader_hwnd)
            time.sleep(0.5)

            for member_name, member_hwnd in member_windows:
                self.logger.info(f"Inviting {member_name}...")
                
                self._focus_window_by_hwnd(leader_hwnd)
                time.sleep(0.2)
                
                self.input_simulator.type_string(f"/invite {member_name}")
                self.input_simulator.press_key('enter')
                
                time.sleep(1.0) 

                self.logger.info(f"Switching to {member_name} to accept...")
                self._focus_window_by_hwnd(member_hwnd)
                time.sleep(0.5)
                
                self.input_simulator.press_key('enter')
                time.sleep(0.5)

            self.logger.info("All invitations processed. Switching back to leader.")
            self._focus_window_by_hwnd(leader_hwnd)

        except Exception as e:
            self.logger.error(f"Error during group invitation sequence: {e}", exc_info=True)
        finally:
            self.is_running = False
