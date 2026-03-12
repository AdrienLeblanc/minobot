import logging
import sys
from unittest.mock import MagicMock

import pytest

# Add src to path to allow imports
sys.path.insert(0, 'src')

from src.features.multi_window_clicker import MultiWindowClicker


@pytest.fixture
def mock_dependencies():
    """Pytest fixture to create mock objects for dependencies."""
    logger = logging.getLogger("test_multiclicker")
    window_manager = MagicMock()
    focus_manager = MagicMock()
    input_simulator = MagicMock()
    return logger, window_manager, focus_manager, input_simulator


def test_get_sorted_windows_respects_config_order(mock_dependencies):
    """
    Tests if the window list is sorted correctly based on 'window_cycle_order'.
    """
    logger, window_manager, focus_manager, input_simulator = mock_dependencies
    
    # --- Arrange ---
    config = {
        "window_cycle_order": ["Cra", "Eni", "Panda"]
    }
    
    window_manager.windows = {
        "Panda-Char - Dofus Retro": 3,
        "Zobal-Char - Dofus Retro": 4,
        "Cra-Char - Dofus Retro": 1,
        "Eni-Char - Dofus Retro": 2,
    }
    
    clicker = MultiWindowClicker(logger, window_manager, focus_manager, input_simulator, config)
    
    # --- Act ---
    sorted_windows = clicker._get_sorted_windows()
    
    # --- Assert ---
    sorted_titles = [title for title, _ in sorted_windows]
    
    expected_order = [
        "Cra-Char - Dofus Retro",
        "Eni-Char - Dofus Retro",
        "Panda-Char - Dofus Retro",
        "Zobal-Char - Dofus Retro",
    ]
    
    assert sorted_titles == expected_order


def test_get_sorted_windows_reverse_order(mock_dependencies):
    """
    Tests if the window list is correctly reversed for the reset function.
    """
    logger, window_manager, focus_manager, input_simulator = mock_dependencies
    
    # --- Arrange ---
    config = {
        "window_cycle_order": ["Cra", "Eni", "Panda"]
    }
    
    window_manager.windows = {
        "Panda-Char - Dofus Retro": 3,
        "Cra-Char - Dofus Retro": 1,
        "Eni-Char - Dofus Retro": 2,
    }
    
    clicker = MultiWindowClicker(logger, window_manager, focus_manager, input_simulator, config)
    
    # --- Act ---
    sorted_windows = clicker._get_sorted_windows(reverse_order=True)
    
    # --- Assert ---
    sorted_titles = [title for title, _ in sorted_windows]
    
    # The expected order is the reverse of the configured priority
    expected_order = [
        "Panda-Char - Dofus Retro",
        "Eni-Char - Dofus Retro",
        "Cra-Char - Dofus Retro",
    ]
    
    assert sorted_titles == expected_order


def test_get_sorted_windows_no_config_order_is_alphabetic(mock_dependencies):
    """
    Tests if windows are sorted alphabetically when 'window_cycle_order' is empty.
    """
    logger, window_manager, focus_manager, input_simulator = mock_dependencies
    
    # --- Arrange ---
    config = {"window_cycle_order": []}
    window_manager.windows = {
        "Panda-Char - Dofus Retro": 3,
        "Cra-Char - Dofus Retro": 1,
        "Eni-Char - Dofus Retro": 2,
    }
    
    clicker = MultiWindowClicker(logger, window_manager, focus_manager, input_simulator, config)
    
    # --- Act ---
    sorted_windows = clicker._get_sorted_windows()
    
    # --- Assert ---
    sorted_titles = [title for title, _ in sorted_windows]
    
    expected_order = [
        "Cra-Char - Dofus Retro",
        "Eni-Char - Dofus Retro",
        "Panda-Char - Dofus Retro",
    ]
    
    assert sorted_titles == expected_order
