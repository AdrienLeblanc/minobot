import asyncio
import time
from typing import Callable, Dict, List

import win32api
import win32con


class KeyboardMonitor:
    """
    Surveille les pressions de touches et de boutons souris et déclenche des callbacks associés.
    Ne déclenche l'action qu'une seule fois lorsque la touche est pressée, pas en continu.
    """

    def __init__(self, logger):
        self.logger = logger
        self.monitoring = False
        
        # Structure: vk_code -> List of registered handlers for that key
        self.hotkeys: Dict[int, List[Dict]] = {}
        
        # Dictionnaire pour suivre l'état "pressé" de chaque touche principale
        self.key_states: Dict[int, bool] = {}

        self.key_mapping = self._build_key_map()
        self.mouse_mapping = {
            'LEFT': win32con.VK_LBUTTON, 'RIGHT': win32con.VK_RBUTTON,
            'MIDDLE': win32con.VK_MBUTTON, 'X1': win32con.VK_XBUTTON1,
            'X2': win32con.VK_XBUTTON2,
        }
        self.modifier_mapping = {
            'CTRL': win32con.VK_CONTROL,
            'SHIFT': win32con.VK_SHIFT,
            'ALT': win32con.VK_MENU
        }

    def _build_key_map(self):
        key_map = {
            'F1': win32con.VK_F1, 'F2': win32con.VK_F2, 'F3': win32con.VK_F3,
            'F4': win32con.VK_F4, 'F5': win32con.VK_F5, 'F6': win32con.VK_F6,
            'F7': win32con.VK_F7, 'F8': win32con.VK_F8, 'F9': win32con.VK_F9,
            'F10': win32con.VK_F10, 'F11': win32con.VK_F11, 'F12': win32con.VK_F12,
        }
        return key_map

    def register_hotkey(self, key_combo: str, callback: Callable, cooldown: float = 0.5, pass_mouse_pos: bool = False):
        if not key_combo: return
        
        parts = [p.strip().upper() for p in key_combo.split('+')]
        main_key = parts[-1]
        modifiers_str = parts[:-1]

        vk_code = self.key_mapping.get(main_key) or self.mouse_mapping.get(main_key)
        if not vk_code:
            self.logger.error(f"Hotkey main key '{main_key}' is not supported in combo '{key_combo}'.")
            return

        required_modifiers = [self.modifier_mapping[mod] for mod in modifiers_str if mod in self.modifier_mapping]

        if vk_code not in self.hotkeys:
            self.hotkeys[vk_code] = []
            self.key_states[vk_code] = False # Initialiser l'état à "non pressé"

        self.hotkeys[vk_code].append({
            'callback': callback,
            'last_trigger': 0.0,
            'cooldown': cooldown,
            'pass_mouse_pos': pass_mouse_pos,
            'modifiers': required_modifiers
        })
        
        self.hotkeys[vk_code].sort(key=lambda x: len(x['modifiers']), reverse=True)
        self.logger.debug(f"Registered hotkey '{key_combo}'. Handlers for VK {vk_code}: {len(self.hotkeys[vk_code])}")

    def _is_key_pressed(self, vk_code: int) -> bool:
        return win32api.GetAsyncKeyState(vk_code) & 0x8000 != 0

    def _are_modifiers_pressed(self, modifiers: List[int]) -> bool:
        for mod_code in modifiers:
            if not self._is_key_pressed(mod_code):
                return False
        return True

    async def start(self):
        if not self.hotkeys:
            self.logger.warning("Keyboard monitor started, but no hotkeys are registered.")
            return

        self.monitoring = True
        self.logger.info("Keyboard/Mouse monitor started.")
        poll_interval = 0.02 # Poll plus rapide pour une meilleure réactivité

        while self.monitoring:
            now = time.time()
            
            for vk_code, handlers in self.hotkeys.items():
                is_pressed = self._is_key_pressed(vk_code)
                was_pressed = self.key_states[vk_code]

                # Déclencher uniquement sur le front descendant -> montant (relâché -> pressé)
                if is_pressed and not was_pressed:
                    matched_handler = None
                    for handler in handlers:
                        if self._are_modifiers_pressed(handler['modifiers']):
                            # On a trouvé le handler le plus spécifique qui correspond
                            if (now - handler['last_trigger']) > handler['cooldown']:
                                matched_handler = handler
                                break
                    
                    if matched_handler:
                        matched_handler['last_trigger'] = now
                        callback = matched_handler['callback']
                        pass_pos = matched_handler['pass_mouse_pos']

                        try:
                            if pass_pos:
                                mouse_pos = win32api.GetCursorPos()
                                if asyncio.iscoroutinefunction(callback):
                                    asyncio.create_task(callback(mouse_pos))
                                else:
                                    callback(mouse_pos)
                            else:
                                if asyncio.iscoroutinefunction(callback):
                                    asyncio.create_task(callback())
                                else:
                                    callback()
                        except Exception as e:
                            self.logger.error(f"Error in hotkey callback: {e}", exc_info=True)

                # Mettre à jour l'état de la touche pour la prochaine itération
                self.key_states[vk_code] = is_pressed

            await asyncio.sleep(poll_interval)

    def stop(self):
        self.monitoring = False
        self.logger.info("Keyboard monitor stopped.")
