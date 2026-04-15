import asyncio
import logging
from typing import Dict, Any, Optional

import pywintypes

from src.core.focus_manager import FocusManager
from src.core.input_simulator import InputSimulator
from src.core.window_manager import WindowManager
from src.core.notification_manager import NotificationManager, Notification


class GroupManager:
    """
    Manages the logic for chain-inviting characters to a group using a relay method.
    Now uses event-based synchronization via notifications.
    """

    def __init__(
            self,
            logger: logging.Logger,
            window_manager: WindowManager,
            input_simulator: InputSimulator,
            focus_manager: FocusManager,
            notification_manager: NotificationManager,
            config: Dict[str, Any]
    ):
        """
        Initializes the GroupManager.
        """
        self.logger: logging.Logger = logger
        self.window_manager: WindowManager = window_manager
        self.input_simulator: InputSimulator = input_simulator
        self.focus_manager: FocusManager = focus_manager
        self.notification_manager: NotificationManager = notification_manager
        self.config: Dict[str, Any] = config
        self.is_running: bool = False
        
        # Event used to synchronize with notifications
        self._invite_received_event = asyncio.Event()
        self._expected_invitee: Optional[str] = None

        # Register callback to catch invitation notifications
        self.notification_manager.register_callback(self._on_notification_received)

    async def _on_notification_received(self, notification: Notification) -> None:
        """
        Callback triggered by NotificationManager.
        If the notification is an invitation for the expected character, triggers the event.
        """
        if not self.is_running or not self._expected_invitee:
            return

        # Notification title usually contains the character name (e.g., "Dofus - CharacterName")
        if self._expected_invitee in notification.title:
            # Check if it's a group invitation (Adjust keyword if necessary for your language)
            invite_keywords = ["invite", "groupe", "group"]
            if any(keyword.lower() in notification.message.lower() for keyword in invite_keywords):
                self.logger.info(f"Notification received: Invitation detected for {self._expected_invitee}")
                self._invite_received_event.set()

    async def invite_all(self) -> None:
        """
        Initiates a relay-style group invitation sequence.
        Synchronized by Windows notifications for maximum reliability.
        """
        if self.is_running:
            self.logger.warning("Group invitation sequence already running.")
            return

        self.is_running = True
        self.logger.info("Starting event-based group invitation sequence...")

        try:
            self.window_manager.refresh()
            all_windows = self.window_manager.get_ordered_windows()
            if len(all_windows) < 2:
                self.logger.warning(
                    f"Only {len(all_windows)} character(s) found. "
                    "At least 2 are required to start a group."
                )
                return

            initial_leader_title, initial_leader_hwnd = all_windows[0]
            self.logger.info(f"Initial leader identified: {self.window_manager.extract_character_name(initial_leader_title)}")

            for i in range(len(all_windows) - 1):
                inviter_title, inviter_hwnd = all_windows[i]
                invitee_title, invitee_hwnd = all_windows[i + 1]

                inviter_name = self.window_manager.extract_character_name(inviter_title)
                invitee_name = self.window_manager.extract_character_name(invitee_title)

                self.logger.info(f"Step {i+1}: '{inviter_name}' inviting '{invitee_name}'...")

                # Set expectation for the notification handler
                self._expected_invitee = invitee_name
                self._invite_received_event.clear()

                # 1. Focus the inviter and send command
                await self.focus_manager.focus(inviter_hwnd)
                self.input_simulator.press_key('enter')
                await asyncio.sleep(0.05)
                self.input_simulator.paste_string(f"/invite {invitee_name}")
                self.input_simulator.press_key('enter')

                # 2. Wait for the notification (with a timeout of 5 seconds to avoid being stuck)
                try:
                    self.logger.debug(f"Waiting for notification for {invitee_name}...")
                    await asyncio.wait_for(self._invite_received_event.wait(), timeout=5.0)
                except asyncio.TimeoutError:
                    self.logger.warning(f"Timeout waiting for {invitee_name}'s invitation notification. Proceeding anyway.")

                # 3. Switch to invitee and accept
                await self.focus_manager.focus(invitee_hwnd)
                await asyncio.sleep(0.1) # Small delay to ensure the window has focus and is ready for input
                self.input_simulator.press_key('enter')
                self.logger.debug(f"'{invitee_name}' joined.")

            self.logger.info(
                f"Group invitation complete. Returning focus to "
                f"{self.window_manager.extract_character_name(initial_leader_title)}."
            )
            await self.focus_manager.focus(initial_leader_hwnd)

        except Exception as e:
            self.logger.error(f"Error during group invitation sequence: {e}", exc_info=True)
        finally:
            self.is_running = False
            self._expected_invitee = None
