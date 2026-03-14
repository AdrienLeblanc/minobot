import asyncio
import logging
from typing import Dict, Any

import pywintypes

from src.core.focus_manager import FocusManager
from src.core.input_simulator import InputSimulator
from src.core.window_manager import WindowManager


class GroupManager:
    """
    Manages the logic for chain-inviting characters to a group using a relay method.
    """

    def __init__(
            self,
            logger: logging.Logger,
            window_manager: WindowManager,
            input_simulator: InputSimulator,
            focus_manager: FocusManager,
            config: Dict[str, Any]  # Added config for sorting
    ):
        """
        Initializes the GroupManager.
        """
        self.logger: logging.Logger = logger
        self.window_manager: WindowManager = window_manager
        self.input_simulator: InputSimulator = input_simulator
        self.focus_manager: FocusManager = focus_manager
        self.config: Dict[str, Any] = config
        self.is_running: bool = False

    async def invite_all(self) -> None:
        """
        Initiates a relay-style group invitation sequence.
        Character 1 invites Char 2, then Char 2 invites Char 3, and so on.
        """
        if self.is_running:
            self.logger.warning("Group invitation sequence already running.")
            return

        self.is_running = True
        self.logger.info("Starting relay group invitation sequence...")

        try:
            self.window_manager.refresh()
            all_windows = self.window_manager.get_ordered_windows()
            if len(all_windows) < 2:
                self.logger.info("Not enough characters found to start a group.")
                return

            initial_leader_title, initial_leader_hwnd = all_windows[0]
            self.logger.info(f"Initial leader identified: {self.window_manager.extract_character_name(initial_leader_title)}")

            # Loop through pairs of (inviter, invitee)
            for i in range(len(all_windows) - 1):
                inviter_title, inviter_hwnd = all_windows[i]
                invitee_title, invitee_hwnd = all_windows[i + 1]

                inviter_name = self.window_manager.extract_character_name(inviter_title)
                invitee_name = self.window_manager.extract_character_name(invitee_title)

                if not inviter_name or not invitee_name:
                    self.logger.error(f"Could not extract names for '{inviter_title}' or '{invitee_title}'. Aborting.")
                    return

                self.logger.info(f"'{inviter_name}' is inviting '{invitee_name}'...")

                # 1. Focus the inviter
                await self.focus_manager.focus(inviter_hwnd)
                await asyncio.sleep(0.05)

                # 2. Type and send invite command
                self.input_simulator.press_key('enter')
                await asyncio.sleep(0.05)
                self.input_simulator.paste_string(f"/invite {invitee_name}")
                await asyncio.sleep(0.05)
                self.input_simulator.press_key('enter')
                await asyncio.sleep(0.05)  # Wait for invitation to be sent and received

                # 3. Switch to invitee to accept
                self.logger.debug(f"Switching to '{invitee_name}' to accept...")
                await self.focus_manager.focus(invitee_hwnd)
                await asyncio.sleep(0.05)

                # 4. Accept invitation
                self.input_simulator.press_key('enter')
                self.logger.debug(f"'{invitee_name}' joined the group.")
                await asyncio.sleep(0.05)

            self.logger.info("All invitations processed. Switching back to initial leader.")
            await self.focus_manager.focus(initial_leader_hwnd)

        except pywintypes.error as e:
            self.logger.error(f"Win32 API error during group invitation: {e}")
        except Exception as e:
            self.logger.error(f"Error during group invitation sequence: {e}", exc_info=True)
        finally:
            self.is_running = False
