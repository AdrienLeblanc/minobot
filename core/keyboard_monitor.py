import asyncio
import logging
import time
from dataclasses import dataclass, field
from typing import Callable, Dict, List, Any

import win32api
import win32con


@dataclass
class HotkeyHandler:
    """
    Represents a registered hotkey handler configuration.
    """
    callback: Callable[..., Any]
    cooldown: float
    pass_mouse_pos: bool
    modifiers: List[int]
    last_trigger: float = 0.0


class KeyboardMonitor:
    """
    Monitors keyboard and mouse inputs to trigger registered callbacks.
    
    It supports:
    - Modifier keys (CTRL, SHIFT, ALT)
    - Mouse buttons (Left, Right, Middle, X1, X2)
    - Cooldown management per hotkey
    - Single-press triggering (no spam on hold)
    """

    def __init__(self, logger: logging.Logger):
        """
        Initializes the KeyboardMonitor.

        Args:
            logger: The application logger.
        """
        self.logger: logging.Logger = logger
        self.monitoring: bool = False
        
        # Structure: vk_code -> List of registered handlers for that key
        # We use a list to support multiple combos for the same main key (e.g. F1 vs CTRL+F1)
        self.hotkeys: Dict[int, List[HotkeyHandler]] = {}
        
        # Tracks the "pressed" state of each main key to prevent repeat triggering
        self.key_states: Dict[int, bool] = {}

        self.key_mapping: Dict[str, int] = self._build_key_map()
        self.mouse_mapping: Dict[str, int] = {
            'LEFT': win32con.VK_LBUTTON,
            'RIGHT': win32con.VK_RBUTTON,
            'MIDDLE': win32con.VK_MBUTTON,
            'X1': win32con.VK_XBUTTON1,
            'X2': win32con.VK_XBUTTON2,
        }
        self.modifier_mapping: Dict[str, int] = {
            'CTRL': win32con.VK_CONTROL,
            'SHIFT': win32con.VK_SHIFT,
            'ALT': win32con.VK_MENU
        }

    def _build_key_map(self) -> Dict[str, int]:
        """Builds the mapping for function keys."""
        return {
            f'F{i}': getattr(win32con, f'VK_F{i}')
            for i in range(1, 13)
        }

    def register_hotkey(
        self, 
        key_combo: str, 
        callback: Callable[..., Any], 
        cooldown: float = 0.5, 
        pass_mouse_pos: bool = False
    ) -> None:
        """
        Registers a new hotkey with an associated callback.

        Args:
            key_combo: The key combination string (e.g., "F1", "CTRL+X2").
            callback: The function to call when triggered.
            cooldown: Minimum time in seconds between triggers.
            pass_mouse_pos: If True, passes (x, y) tuple to the callback.
        """
        if not key_combo:
            return
        
        parts = [p.strip().upper() for p in key_combo.split('+')]
        main_key = parts[-1]
        modifiers_str = parts[:-1]

        vk_code = self.key_mapping.get(main_key) or self.mouse_mapping.get(main_key)
        
        if not vk_code:
            self.logger.error(f"Hotkey main key '{main_key}' is not supported in combo '{key_combo}'.")
            return

        required_modifiers = []
        for mod in modifiers_str:
            mod_code = self.modifier_mapping.get(mod)
            if mod_code:
                required_modifiers.append(mod_code)
            else:
                self.logger.warning(f"Unknown modifier '{mod}' in '{key_combo}'")

        if vk_code not in self.hotkeys:
            self.hotkeys[vk_code] = []
            self.key_states[vk_code] = False

        handler = HotkeyHandler(
            callback=callback,
            cooldown=cooldown,
            pass_mouse_pos=pass_mouse_pos,
            modifiers=required_modifiers
        )

        self.hotkeys[vk_code].append(handler)
        
        # Sort handlers by number of modifiers descending.
        # This ensures specific combos (CTRL+X2) are checked before generic ones (X2).
        self.hotkeys[vk_code].sort(key=lambda x: len(x.modifiers), reverse=True)
        
        self.logger.debug(
            f"Registered hotkey '{key_combo}'. Handlers for VK {vk_code}: {len(self.hotkeys[vk_code])}"
        )

    def _is_key_pressed(self, vk_code: int) -> bool:
        """Checks if a virtual key is currently pressed."""
        return win32api.GetAsyncKeyState(vk_code) & 0x8000 != 0

    def _are_modifiers_pressed(self, modifiers: List[int]) -> bool:
        """Checks if all required modifier keys are pressed."""
        for mod_code in modifiers:
            if not self._is_key_pressed(mod_code):
                return False
        return True

    async def start(self) -> None:
        """
        Starts the monitoring loop. Runs indefinitely until stop() is called.
        """
        if not self.hotkeys:
            self.logger.warning("Keyboard monitor started, but no hotkeys are registered.")
            return

        self.monitoring = True
        self.logger.info("Keyboard/Mouse monitor started.")
        poll_interval = 0.02

        while self.monitoring:
            now = time.time()
            
            for vk_code, handlers in self.hotkeys.items():
                is_pressed = self._is_key_pressed(vk_code)
                was_pressed = self.key_states[vk_code]

                # Trigger only on Rising Edge (Released -> Pressed)
                if is_pressed and not was_pressed:
                    matched_handler: Any = None # Typed as Any to avoid strict optional check complexity in loop
                    
                    for handler in handlers:
                        if self._are_modifiers_pressed(handler.modifiers):
                            if (now - handler.last_trigger) > handler.cooldown:
                                matched_handler = handler
                                break
                    
                    if matched_handler:
                        matched_handler.last_trigger = now
                        self._trigger_callback(matched_handler)

                # Update state for next iteration
                self.key_states[vk_code] = is_pressed

            await asyncio.sleep(poll_interval)

    def _trigger_callback(self, handler: HotkeyHandler) -> None:
        """Executes the callback safely."""
        try:
            if handler.pass_mouse_pos:
                mouse_pos = win32api.GetCursorPos()
                if asyncio.iscoroutinefunction(handler.callback):
                    asyncio.create_task(handler.callback(mouse_pos))
                else:
                    handler.callback(mouse_pos)
            else:
                if asyncio.iscoroutinefunction(handler.callback):
                    asyncio.create_task(handler.callback())
                else:
                    handler.callback()
        except Exception as e:
            self.logger.error(f"Error in hotkey callback: {e}", exc_info=True)

    def stop(self) -> None:
        """Stops the monitoring loop."""
        self.monitoring = False
        self.logger.info("Keyboard monitor stopped.")
