import win32api
import win32con
import asyncio
import time
import inspect
from typing import Callable, Dict

class KeyboardMonitor:
    """
    Surveille les pressions de touches et de boutons souris et déclenche des callbacks associés.
    """

    def __init__(self, logger):
        self.logger = logger
        self.monitoring = False
        self.hotkeys: Dict[int, tuple[Callable, float, float, bool]] = {}
        self.key_mapping = self._build_key_map()
        self.mouse_mapping = {
            'LEFT': win32con.VK_LBUTTON, 'RIGHT': win32con.VK_RBUTTON,
            'MIDDLE': win32con.VK_MBUTTON, 'X1': win32con.VK_XBUTTON1,
            'X2': win32con.VK_XBUTTON2,
        }

    def _build_key_map(self):
        key_map = {
            'F1': win32con.VK_F1, 'F2': win32con.VK_F2, 'F3': win32con.VK_F3,
            'F4': win32con.VK_F4, 'F5': win32con.VK_F5, 'F6': win32con.VK_F6,
            'F7': win32con.VK_F7, 'F8': win32con.VK_F8, 'F9': win32con.VK_F9,
            'F10': win32con.VK_F10, 'F11': win32con.VK_F11, 'F12': win32con.VK_F12,
        }
        return key_map

    def register_hotkey(self, key_name: str, callback: Callable, cooldown: float = 0.5, pass_mouse_pos: bool = False):
        """
        Enregistre un raccourci.

        Args:
            key_name (str): Nom de la touche ou du bouton souris.
            callback (Callable): La fonction à appeler.
            cooldown (float): Temps d'attente avant redéclenchement.
            pass_mouse_pos (bool): Si True, passe la position de la souris au callback.
        """
        key_name_upper = key_name.upper()
        vk_code = self.key_mapping.get(key_name_upper) or self.mouse_mapping.get(key_name_upper)

        if not vk_code:
            self.logger.error(f"Hotkey '{key_name}' is not supported.")
            return

        self.hotkeys[vk_code] = (callback, 0, cooldown, pass_mouse_pos)
        self.logger.info(f"Registered hotkey '{key_name}' to callback '{callback.__name__ if hasattr(callback, '__name__') else 'lambda'}'.")

    def _is_key_pressed(self, vk_code: int) -> bool:
        return win32api.GetAsyncKeyState(vk_code) & 0x8000 != 0

    async def start(self):
        if not self.hotkeys:
            self.logger.warning("Keyboard monitor started, but no hotkeys are registered.")
            return

        self.monitoring = True
        self.logger.info("Keyboard/Mouse monitor started.")
        
        poll_interval = 0.05

        while self.monitoring:
            now = time.time()
            for vk_code, (callback, last_trigger, cooldown, pass_pos) in list(self.hotkeys.items()):
                if self._is_key_pressed(vk_code):
                    if (now - last_trigger) > cooldown:
                        self.hotkeys[vk_code] = (callback, now, cooldown, pass_pos)
                        
                        try:
                            is_coro = asyncio.iscoroutinefunction(callback)
                            
                            if pass_pos:
                                mouse_pos = win32api.GetCursorPos()
                                if is_coro:
                                    asyncio.create_task(callback(mouse_pos))
                                else:
                                    callback(mouse_pos)
                            else:
                                if is_coro:
                                    asyncio.create_task(callback())
                                else:
                                    callback()
                                    
                        except Exception as e:
                            self.logger.error(f"Error in hotkey callback: {e}", exc_info=True)
            
            await asyncio.sleep(poll_interval)

    def stop(self):
        self.monitoring = False
        self.logger.info("Keyboard monitor stopped.")
