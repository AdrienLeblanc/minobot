import asyncio
import logging
import re
from typing import Optional, List, Tuple

import pywintypes
import win32gui

from core.focus_manager import FocusManager
from core.input_simulator import InputSimulator
from core.window_manager import WindowManager


class GroupManager:
    """
    Manages the logic for chain-inviting characters to a group.
    """

    def __init__(
            self,
            logger: logging.Logger,
            window_manager: WindowManager,
            input_simulator: InputSimulator,
            focus_manager: FocusManager
    ):
        """
        Initializes the GroupManager.

        Args:
            logger: The application logger.
            window_manager: The manager for tracking game windows.
            input_simulator: The simulator for keyboard/mouse inputs.
            focus_manager: The manager for focusing windows.
        """
        self.logger: logging.Logger = logger
        self.window_manager: WindowManager = window_manager
        self.input_simulator: InputSimulator = input_simulator
        self.focus_manager: FocusManager = focus_manager
        self.is_running: bool = False

    def _extract_character_name(self, window_title: str) -> Optional[str]:
        """
        Extracts the character name from the window title.
        
        Example: "MyChar (Lvl 200) - Dofus Retro" -> "MyChar"

        Args:
            window_title: The title of the game window.

        Returns:
            The extracted character name, or None if not found.
        """
        match = re.search(r"(.+?)\s-\sDofus Retro", window_title)
        if not match:
            return None

        name_part = match.group(1).strip()
        # Handle cases like "(AccountName) CharacterName"
        if ')' in name_part:
            name_part = name_part.split(')')[-1].strip()

        return name_part

    async def invite_all(self) -> None:
        """
        Initiates the asynchronous group invitation sequence.
        
        The character in the foreground window is designated as the leader.
        The leader invites all other detected characters one by one.
        """
        if self.is_running:
            self.logger.warning("Group invitation sequence already running.")
            return

        self.is_running = True
        self.logger.info("Starting async group invitation sequence...")

        try:
            self.window_manager.refresh()
            leader_hwnd = win32gui.GetForegroundWindow()

            if leader_hwnd not in self.window_manager.windows.values():
                leader_title = win32gui.GetWindowText(leader_hwnd)
                self.logger.error(f"The active window ('{leader_title}') is not a recognized Dofus window.")
                return

            leader_name = self._extract_character_name(win32gui.GetWindowText(leader_hwnd))
            if not leader_name:
                self.logger.error(f"Could not extract character name from active window.")
                return

            self.logger.info(f"Leader identified: {leader_name}")

            members: List[Tuple[str, int]] = []
            for title, hwnd in self.window_manager.windows.items():
                if hwnd == leader_hwnd:
                    continue
                char_name = self._extract_character_name(title)
                if char_name:
                    members.append((char_name, hwnd))

            if not members:
                self.logger.info("No other characters found to invite.")
                return

            self.logger.info(f"Found {len(members)} members to invite: {[name for name, _ in members]}")

            await self.focus_manager.focus(leader_hwnd)
            await asyncio.sleep(0.25)

            for member_name, member_hwnd in members:
                self.logger.info(f"Inviting {member_name}...")

                # Ensure leader is focused before typing
                await self.focus_manager.focus(leader_hwnd)
                await asyncio.sleep(0.1)

                # Type and send invite command
                self.input_simulator.press_key('enter')
                await asyncio.sleep(0.1)
                self.input_simulator.paste_string(f"/invite {member_name}")
                await asyncio.sleep(0.05)
                self.input_simulator.press_key('enter')
                await asyncio.sleep(0.5)

                # Switch to member to accept
                self.logger.debug(f"Switching to {member_name} to accept...")
                await self.focus_manager.focus(member_hwnd)
                await asyncio.sleep(0.25)

                # Accept invitation
                self.input_simulator.press_key('enter')
                self.logger.debug(f"{member_name} joined the group.")
                await asyncio.sleep(0.25)

            self.logger.info("All invitations processed. Switching back to leader.")
            await self.focus_manager.focus(leader_hwnd)

        except pywintypes.error as e:
            self.logger.error(f"Win32 API error during group invitation: {e}")
        except Exception as e:
            self.logger.error(f"Error during group invitation sequence: {e}", exc_info=True)
        finally:
            self.is_running = False
