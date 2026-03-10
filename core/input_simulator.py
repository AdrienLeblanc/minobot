import pyautogui


class InputSimulator:
    """
    Classe utilitaire pour simuler les entrées utilisateur (clavier, souris).
    """

    def __init__(self, logger):
        self.logger = logger
        # Configurer une pause de sécurité après chaque action pyautogui
        pyautogui.PAUSE = 0.1

    def type_string(self, text: str):
        """
        Simule la frappe d'une chaîne de caractères.

        Args:
            text (str): La chaîne à taper.
        """
        self.logger.debug(f"Typing string: '{text}'")
        pyautogui.write(text, interval=0.05) # Un petit intervalle pour un rendu plus humain

    def press_key(self, key: str):
        """
        Simule l'appui sur une touche spéciale (ex: 'enter', 'f1', etc.).

        Args:
            key (str): Le nom de la touche à presser.
        """
        self.logger.debug(f"Pressing key: '{key}'")
        pyautogui.press(key)

    def click(self, x: int, y: int):
        """
        Simule un clic de souris à des coordonnées spécifiques.

        Args:
            x (int): Coordonnée X.
            y (int): Coordonnée Y.
        """
        self.logger.debug(f"Clicking at position: ({x}, {y})")
        pyautogui.click(x, y)
