import asyncio
import win32con
import win32gui


class WindowReorder:
    """
    Réarrange les fenêtres dans la barre des tâches en les cachant/réaffichant
    dans l'ordre défini par la configuration.
    """

    def __init__(self, logger, window_manager, focus_manager, config):
        self.logger = logger
        self.window_manager = window_manager
        self.focus_manager = focus_manager
        self.config = config
        self.is_running = False

    def _get_sorted_windows(self):
        """
        Retourne la liste des fenêtres (titre, hwnd) triée selon l'ordre configuré.
        """
        self.window_manager.ensure_fresh()
        raw_windows = list(self.window_manager.windows.items())

        if not raw_windows:
            return []

        cycle_order = self.config.get("window_cycle_order", [])

        def sort_key(item):
            title, _ = item
            title_lower = title.lower()
            for i, name_part in enumerate(cycle_order):
                if name_part.lower() in title_lower:
                    return i
            return len(cycle_order) + 1000

        raw_windows.sort(key=lambda x: x[0])
        raw_windows.sort(key=sort_key)
        return raw_windows

    async def reorder_taskbar(self):
        """
        Exécute la séquence de réarrangement.
        """
        if self.is_running:
            self.logger.warning("Window reorder sequence already running.")
            return

        self.is_running = True
        self.logger.info("Starting taskbar reorder sequence...")

        try:
            sorted_windows = self._get_sorted_windows()
            if not sorted_windows:
                self.logger.warning("No Dofus windows found to reorder.")
                self.is_running = False
                return

            sorted_hwnds = [hwnd for _, hwnd in sorted_windows]
            self.logger.info(f"Reordering {len(sorted_hwnds)} windows based on configuration.")

            for hwnd in sorted_hwnds:
                if win32gui.IsWindow(hwnd):
                    win32gui.ShowWindow(hwnd, win32con.SW_HIDE)
            
            await asyncio.sleep(0.5)

            for i, (title, hwnd) in enumerate(sorted_windows):
                if win32gui.IsWindow(hwnd):
                    win32gui.ShowWindow(hwnd, win32con.SW_SHOW)
                    if win32gui.IsIconic(hwnd):
                        win32gui.ShowWindow(hwnd, win32con.SW_RESTORE)
                    await asyncio.sleep(0.1)

            # Utiliser le FocusManager pour restaurer le focus de manière fiable
            if sorted_hwnds:
                first_hwnd = sorted_hwnds[0]
                if win32gui.IsWindow(first_hwnd):
                    self.logger.info(f"Restoring focus to first window: {sorted_windows[0][0]}")
                    await self.focus_manager.focus(first_hwnd)

            self.logger.info("Taskbar reorder complete.")

        except Exception as e:
            self.logger.error(f"Error during reorder sequence: {e}", exc_info=True)
            try:
                for hwnd in self.window_manager.windows.values():
                     win32gui.ShowWindow(hwnd, win32con.SW_SHOW)
            except:
                pass
        finally:
            self.is_running = False
