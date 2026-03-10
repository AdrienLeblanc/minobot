import win32gui
import win32con
import asyncio
import time

class FocusManager:
    """
    Gère la mise au premier plan et le focus des fenêtres de jeu.
    """

    def __init__(self, logger, config, input_simulator):
        self.logger = logger
        self.config = config
        self.input_simulator = input_simulator
        self.last_focus_time = 0
        self.cooldown = config.get("focus_cooldown", 1.0)

    async def focus(self, hwnd):
        """
        Met une fenêtre au premier plan et lui donne le focus.
        """
        now = time.time()
        if (now - self.last_focus_time) < self.cooldown:
            # self.logger.debug("Focus request ignored due to cooldown.")
            return

        self.last_focus_time = now
        
        try:
            title = win32gui.GetWindowText(hwnd)
            self.logger.info(f"Attempting to focus window: '{title}' (HWND: {hwnd})")

            # --- La méthode ALT ---
            # 1. Simuler un bref appui sur ALT pour "réveiller" Windows
            # C'est la clé pour permettre à une application en arrière-plan de prendre le focus.
            self.input_simulator.press_key('alt')
            await asyncio.sleep(0.05) # Très court délai pour que le système traite la touche ALT

            # 2. Restaurer la fenêtre si elle est minimisée
            if win32gui.IsIconic(hwnd):
                win32gui.ShowWindow(hwnd, win32con.SW_RESTORE)
                await asyncio.sleep(0.1) # Laisser le temps à la restauration

            # 3. Mettre la fenêtre au premier plan
            # SetForegroundWindow est la méthode la plus directe et devrait fonctionner
            # maintenant que nous avons "préparé" le système avec ALT.
            win32gui.SetForegroundWindow(hwnd)
            
            # 4. Vérification
            await asyncio.sleep(0.1)
            if win32gui.GetForegroundWindow() == hwnd:
                self.logger.info(f"[FOCUS OK] Successfully focused '{title}'.")
            else:
                self.logger.warning(f"[FOCUS FAILED] Could not focus '{title}'. Another window may be interfering.")

        except Exception as e:
            self.logger.error(f"[FOCUS ERROR] An unexpected error occurred: {e}", exc_info=True)
