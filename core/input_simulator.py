import pyautogui
import win32clipboard
import win32con
import time

class InputSimulator:
    """
    Classe utilitaire pour simuler les entrées utilisateur (clavier, souris).
    """

    def __init__(self, logger):
        self.logger = logger
        # Configurer une pause de sécurité après chaque action pyautogui
        pyautogui.PAUSE = 0.01 # Réduit pour plus de réactivité

    def type_string(self, text: str, interval: float = 0.005):
        """
        Simule la frappe d'une chaîne de caractères.
        """
        self.logger.debug(f"Typing string: '{text}'")
        pyautogui.write(text, interval=interval)

    def paste_string(self, text: str):
        """
        Colle une chaîne de caractères en utilisant le presse-papier (Ctrl+V).
        Beaucoup plus rapide que de taper caractère par caractère.
        """
        self.logger.debug(f"Pasting string: '{text}'")
        
        try:
            # Mettre le texte dans le presse-papier Windows
            win32clipboard.OpenClipboard()
            win32clipboard.EmptyClipboard()
            # Utiliser CF_UNICODETEXT pour supporter tous les caractères
            win32clipboard.SetClipboardData(win32clipboard.CF_UNICODETEXT, text)
            win32clipboard.CloseClipboard()
            
            # Simuler Ctrl+V
            # On utilise keyUp/keyDown pour être sûr que ça passe
            pyautogui.keyDown('ctrl')
            pyautogui.press('v')
            pyautogui.keyUp('ctrl')
            
        except Exception as e:
            self.logger.error(f"Failed to paste text: {e}")
            # Fallback sur la frappe normale en cas d'erreur
            self.type_string(text)

    def press_key(self, key: str):
        """
        Simule l'appui sur une touche spéciale (ex: 'enter', 'f1', etc.).
        """
        # self.logger.debug(f"Pressing key: '{key}'")
        pyautogui.press(key)

    def click(self, x: int, y: int):
        """
        Simule un clic de souris à des coordonnées spécifiques.
        """
        self.logger.debug(f"Clicking at position: ({x}, {y})")
        pyautogui.click(x, y)
