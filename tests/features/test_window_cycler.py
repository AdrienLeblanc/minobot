import logging
import sys
from unittest.mock import MagicMock

import pytest

# Add src to path to allow imports
sys.path.insert(0, 'src')

from src.features.window_cycler import WindowCycler


@pytest.fixture
def mock_managers():
    """Pytest fixture to create mock objects for managers."""
    logger = logging.getLogger("test")
    window_manager = MagicMock()
    focus_manager = MagicMock()
    return logger, window_manager, focus_manager


def test_get_sorted_windows_with_config_order(mock_managers):
    """
    Tests if the window list is sorted correctly based on the 'window_cycle_order' config.
    """
    logger, window_manager, focus_manager = mock_managers
    
    # --- Arrange ---
    config = {
        "window_cycle_order": ["Cra", "Eni", "Panda"]
    }
    
    # Mock the windows detected by WindowManager
    window_manager.windows = {
        "Panda-Char - Dofus Retro": 3,
        "Zobal-Char - Dofus Retro": 4, # Not in config order
        "Cra-Char - Dofus Retro": 1,
        "Eni-Char - Dofus Retro": 2,
    }
    
    cycler = WindowCycler(logger, window_manager, focus_manager, config)
    
    # --- Act ---
    sorted_windows = cycler._get_sorted_windows()
    
    # --- Assert ---
    # Extract just the titles to check the order
    sorted_titles = [title for title, hwnd in sorted_windows]
    
    expected_order = [
        "Cra-Char - Dofus Retro",
        "Eni-Char - Dofus Retro",
        "Panda-Char - Dofus Retro",
        "Zobal-Char - Dofus Retro", # Should be last as it's not in the priority list
    ]
    
    assert sorted_titles == expected_order, "Windows were not sorted according to config order."


def test_get_sorted_windows_empty(mock_managers):
    """
    Tests the behavior when no windows are found.
    """
    logger, window_manager, focus_manager = mock_managers
    
    # --- Arrange ---
    config = {"window_cycle_order": ["Cra", "Eni"]}
    window_manager.windows = {}
    
    cycler = WindowCycler(logger, window_manager, focus_manager, config)
    
    # --- Act ---
    sorted_windows = cycler._get_sorted_windows()
    
    # --- Assert ---
    assert sorted_windows == [], "Should return an empty list when no windows are found."


def test_get_sorted_windows_no_config_order(mock_managers):
    """
    Tests if windows are sorted alphabetically when 'window_cycle_order' is empty.
    """
    logger, window_manager, focus_manager = mock_managers
    
    # --- Arrange ---
    config = {"window_cycle_order": []}
    window_manager.windows = {
        "Panda-Char - Dofus Retro": 3,
        "Cra-Char - Dofus Retro": 1,
        "Eni-Char - Dofus Retro": 2,
    }
    
    cycler = WindowCycler(logger, window_manager, focus_manager, config)
    
    # --- Act ---
    sorted_windows = cycler._get_sorted_windows()
    
    # --- Assert ---
    sorted_titles = [title for title, hwnd in sorted_windows]
    
    expected_order = [
        "Cra-Char - Dofus Retro",
        "Eni-Char - Dofus Retro",
        "Panda-Char - Dofus Retro",
    ]
    
    assert sorted_titles == expected_order, "Windows should be sorted alphabetically by default."
