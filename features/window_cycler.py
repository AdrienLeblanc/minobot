import asyncio
import win32gui


class WindowCycler:
    """
    Permet de passer d'une fenêtre Dofus à l'autre dans un ordre prédéfini.
    """

    def __init__(self, logger, window_manager, focus_manager, config):
        self.logger = logger
        self.window_manager = window_manager
        self.focus_manager = focus_manager
        self.config = config

    def _get_sorted_windows(self):
        """
        Retourne la liste des fenêtres (titre, hwnd) triée selon l'ordre configuré.
        """
        self.window_manager.ensure_fresh()
        
        # On ne prend que les fenêtres gérées par WindowManager (donc Dofus)
        raw_windows = list(self.window_manager.windows.items())  # [(title, hwnd), ...]

        if not raw_windows:
            return []

        # Récupérer l'ordre de priorité depuis la config
        # On attend une liste de noms de personnages (ou parties de titre)
        cycle_order = self.config.get("window_cycle_order", [])

        # Fonction de clé pour le tri
        def sort_key(item):
            title, _ = item
            title_lower = title.lower()

            # Essayer de trouver l'index dans la liste de config
            for i, name_part in enumerate(cycle_order):
                if name_part.lower() in title_lower:
                    return i  # Retourner l'index de priorité (0, 1, 2...)

            # Si pas dans la liste, mettre à la fin (index grand)
            return len(cycle_order) + 1000

        # On fait d'abord un tri alphabétique global pour que les fenêtres non-configurées soient stables
        raw_windows.sort(key=lambda x: x[0])
        
        # Puis on applique le tri prioritaire
        raw_windows.sort(key=sort_key)

        return raw_windows

    async def cycle_next(self):
        """Active la fenêtre suivante dans l'ordre."""
        sorted_windows = self._get_sorted_windows()
        if not sorted_windows:
            self.logger.warning("No Dofus windows found to cycle.")
            return

        current_hwnd = win32gui.GetForegroundWindow()
        
        # Trouver l'index de la fenêtre actuelle dans la liste triée
        current_index = -1
        for i, (_, hwnd) in enumerate(sorted_windows):
            if hwnd == current_hwnd:
                current_index = i
                break
        
        # Si la fenêtre actuelle n'est pas une fenêtre Dofus connue, on commence au début (index 0)
        # Sinon, on prend la suivante (modulo pour boucler)
        if current_index == -1:
            next_index = 0
        else:
            next_index = (current_index + 1) % len(sorted_windows)
        
        next_title, next_hwnd = sorted_windows[next_index]
        self.logger.debug(f"Cycling to next window: {next_title}")
        
        await self.focus_manager.focus(next_hwnd)

    async def cycle_prev(self):
        """Active la fenêtre précédente dans l'ordre."""
        sorted_windows = self._get_sorted_windows()
        if not sorted_windows:
            return

        current_hwnd = win32gui.GetForegroundWindow()
        
        current_index = -1
        for i, (_, hwnd) in enumerate(sorted_windows):
            if hwnd == current_hwnd:
                current_index = i
                break
        
        if current_index == -1:
            prev_index = len(sorted_windows) - 1 # On commence par la fin si hors contexte
        else:
            prev_index = (current_index - 1) % len(sorted_windows)
        
        prev_title, prev_hwnd = sorted_windows[prev_index]
        self.logger.debug(f"Cycling to prev window: {prev_title}")
        
        await self.focus_manager.focus(prev_hwnd)
