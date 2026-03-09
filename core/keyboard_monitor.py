import win32api
import win32con
import asyncio
import time
from typing import Callable, Set


class KeyboardMonitor:
    """
    Surveille les combinaisons de touches en temps réel.
    Utilise GetAsyncKeyState pour une détection rapide et non-bloquante.
    """

    def __init__(self, logger, config):
        self.logger = logger
        self.config = config

        # État des touches
        self.keys_pressed: Set[int] = set()
        self.last_trigger_time = 0
        self.trigger_cooldown = config.get("multiclick_cooldown", 0.1)

        # Callback appelé lors de la combinaison
        self.on_trigger_callback: Callable = None

        # Touches de la combinaison (par défaut CTRL gauche + ALT gauche)
        self.required_keys = self._parse_key_combination(
            config.get("multiclick_combination", "LCTRL+LALT")
        )

        # État du clic gauche
        self.monitoring = False

    def _parse_key_combination(self, combination: str) -> Set[int]:
        """Convertit une combinaison de touches en codes virtuels"""

        key_mapping = {
            "LCTRL": win32con.VK_LCONTROL,
            "RCTRL": win32con.VK_RCONTROL,
            "CTRL": win32con.VK_CONTROL,
            "LALT": win32con.VK_LMENU,
            "RALT": win32con.VK_RMENU,
            "ALT": win32con.VK_MENU,
            "LSHIFT": win32con.VK_LSHIFT,
            "RSHIFT": win32con.VK_RSHIFT,
            "SHIFT": win32con.VK_SHIFT,
        }

        keys = set()
        for key_name in combination.upper().split("+"):
            key_name = key_name.strip()
            if key_name in key_mapping:
                keys.add(key_mapping[key_name])
            else:
                self.logger.warning(f"Unknown key: {key_name}")

        return keys

    def set_trigger_callback(self, callback: Callable):
        """Définit la fonction à appeler lors du déclenchement"""
        self.on_trigger_callback = callback

    def _is_key_pressed(self, vk_code: int) -> bool:
        """Vérifie si une touche est actuellement pressée"""
        return win32api.GetAsyncKeyState(vk_code) & 0x8000 != 0

    def _are_required_keys_pressed(self) -> bool:
        """Vérifie si toutes les touches requises sont pressées"""
        return all(self._is_key_pressed(key) for key in self.required_keys)

    def _is_left_click_pressed(self) -> bool:
        """Vérifie si le clic gauche est pressé"""
        return self._is_key_pressed(win32con.VK_LBUTTON)

    async def start(self):
        """Démarre la surveillance du clavier"""

        self.monitoring = True

        combination_str = self.config.get("multiclick_combination", "LCTRL+LALT")
        self.logger.info(f"Keyboard monitor started - Combination: {combination_str} + Left Click")

        poll_interval = 0.05  # 50ms = 20 checks/sec, bon équilibre réactivité/CPU

        while self.monitoring:
            try:
                # Vérifier si la combinaison est active
                if self._are_required_keys_pressed():

                    # Vérifier si un clic gauche est effectué
                    if self._is_left_click_pressed():

                        # Cooldown pour éviter les déclenchements multiples
                        now = time.time()
                        if now - self.last_trigger_time >= self.trigger_cooldown:
                            self.last_trigger_time = now

                            # Récupérer la position de la souris
                            mouse_pos = win32api.GetCursorPos()

                            self.logger.debug(f"[MULTICLICK TRIGGERED] Position: {mouse_pos}")

                            # Appeler le callback
                            if self.on_trigger_callback:
                                try:
                                    if asyncio.iscoroutinefunction(self.on_trigger_callback):
                                        await self.on_trigger_callback(mouse_pos)
                                    else:
                                        self.on_trigger_callback(mouse_pos)
                                except Exception as e:
                                    self.logger.error(f"Error in trigger callback: {e}")

                await asyncio.sleep(poll_interval)

            except Exception as e:
                self.logger.error(f"Error in keyboard monitor: {e}")
                await asyncio.sleep(0.1)

    def stop(self):
        """Arrête la surveillance"""
        self.monitoring = False
        self.logger.info("Keyboard monitor stopped")
