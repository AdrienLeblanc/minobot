import logging
from typing import Optional

import pyautogui
import win32clipboard


class InputSimulator:
    """
    A utility class to simulate user inputs like keyboard presses and mouse clicks.
    This acts as a wrapper around pyautogui and other libraries to provide a consistent interface.
    """

    def __init__(self, logger: logging.Logger):
        """
        Initializes the InputSimulator.

        Args:
            logger: The application logger.
        """
        self.logger: logging.Logger = logger
        # Configure a minimal pause after each pyautogui action for reliability
        pyautogui.PAUSE = 0.01

    def type_string(self, text: str, interval: float = 0.005) -> None:
        """
        Simulates typing a string of characters.

        Args:
            text: The string to type.
            interval: The delay between each key press.
        """
        self.logger.debug(f"Typing string: '{text}'")
        pyautogui.write(text, interval=interval)

    def paste_string(self, text: str) -> None:
        """
        Pastes a string using the system clipboard (Ctrl+V).
        This is significantly faster than typing character by character.

        Args:
            text: The string to paste.
        """
        self.logger.debug(f"Pasting string: '{text}'")
        try:
            # Save current clipboard content to restore it later
            original_clipboard: Optional[str] = self._get_clipboard_text()

            self._set_clipboard_text(text)

            # Simulate Ctrl+V
            pyautogui.keyDown('ctrl')
            pyautogui.press('v')
            pyautogui.keyUp('ctrl')

            # Restore original clipboard content if it existed
            if original_clipboard:
                self._set_clipboard_text(original_clipboard)

        except Exception as e:
            self.logger.error(f"Failed to paste text: {e}", exc_info=True)
            # Fallback to typing if pasting fails
            self.type_string(text)

    def _set_clipboard_text(self, text: str) -> None:
        """Safely sets the clipboard text."""
        try:
            win32clipboard.OpenClipboard()
            win32clipboard.EmptyClipboard()
            win32clipboard.SetClipboardData(win32clipboard.CF_UNICODETEXT, text)
        finally:
            win32clipboard.CloseClipboard()

    def _get_clipboard_text(self) -> Optional[str]:
        """Safely retrieves text from the clipboard."""
        try:
            win32clipboard.OpenClipboard()
            if win32clipboard.IsClipboardFormatAvailable(win32clipboard.CF_UNICODETEXT):
                return win32clipboard.GetClipboardData(win32clipboard.CF_UNICODETEXT)
            return None
        finally:
            win32clipboard.CloseClipboard()

    def press_key(self, key: str) -> None:
        """
        Simulates pressing a special key (e.g., 'enter', 'f1').

        Args:
            key: The name of the key to press, as recognized by pyautogui.
        """
        # self.logger.debug(f"Pressing key: '{key}'") # Often too verbose
        pyautogui.press(key)

    def click(self, x: int, y: int) -> None:
        """
        Simulates a left mouse click at specific screen coordinates.

        Args:
            x: The x-coordinate on the screen.
            y: The y-coordinate on the screen.
        """
        self.logger.debug(f"Clicking at position: ({x}, {y})")
        pyautogui.click(x, y)
