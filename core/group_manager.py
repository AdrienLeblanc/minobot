import asyncio
import re

import win32con  # Import pour les constantes de fenêtre
import win32gui


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
            if win32gui.IsIconic(hwnd):
                win32gui.ShowWindow(hwnd, win32con.SW_RESTORE)
            win32gui.SetForegroundWindow(hwnd)
        except Exception as e:
            self.logger.error(f"Could not focus window with HWND {hwnd}: {e}")

    async def invite_all(self):
        """
        Lance la séquence d'invitation de groupe de manière asynchrone.
        """
        if self.is_running:
            self.logger.warning("Group invitation sequence already running.")
            return

        self.is_running = True
        self.logger.info("Starting async group invitation sequence...")

        try:
            self.window_manager.refresh()

            leader_hwnd = win32gui.GetForegroundWindow()
            leader_title = win32gui.GetWindowText(leader_hwnd)

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

            member_windows = []
            for title, hwnd in self.window_manager.windows.items():
                if hwnd == leader_hwnd:
                    continue
                char_name = self._extract_character_name(title)
                if char_name:
                    member_windows.append((char_name, hwnd))

            if not member_windows:
                self.logger.info("No other characters found to invite.")
                self.is_running = False
                return

            self.logger.info(f"Found {len(member_windows)} members to invite: {[name for name, _ in member_windows]}")

            self._focus_window_by_hwnd(leader_hwnd)
            await asyncio.sleep(0.25)

            for member_name, member_hwnd in member_windows:
                self.logger.info(f"Inviting {member_name}...")

                self._focus_window_by_hwnd(leader_hwnd)
                await asyncio.sleep(0.1)

                # Étape 1: Appuyer sur Entrée pour activer le chat
                self.input_simulator.press_key('enter')
                await asyncio.sleep(0.1)  # Attendre que le chat soit prêt

                # Étape 2: Coller la commande
                self.input_simulator.paste_string(f"/invite {member_name}")
                await asyncio.sleep(0.05)

                # Étape 3: Appuyer sur Entrée pour envoyer la commande
                self.input_simulator.press_key('enter')

                await asyncio.sleep(0.5)  # Pause pour que l'invitation arrive

                self.logger.info(f"Switching to {member_name} to accept...")
                self._focus_window_by_hwnd(member_hwnd)
                await asyncio.sleep(0.25)

                # Accepter l'invitation
                self.input_simulator.press_key('enter')
                await asyncio.sleep(0.25)

            self.logger.info("All invitations processed. Switching back to leader.")
            self._focus_window_by_hwnd(leader_hwnd)

        except Exception as e:
            self.logger.error(f"Error during group invitation sequence: {e}", exc_info=True)
        finally:
            self.is_running = False
