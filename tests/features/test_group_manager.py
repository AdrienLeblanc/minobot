import logging
import sys
from unittest.mock import MagicMock

import pytest

# Add src to path to allow imports
sys.path.insert(0, 'src')

from src.features.group_manager import GroupManager


@pytest.fixture
def mock_dependencies_for_group_manager():
    """Pytest fixture to create mock objects for GroupManager dependencies."""
    logger = logging.getLogger("test_group_manager")
    window_manager = MagicMock()
    input_simulator = MagicMock()
    focus_manager = MagicMock()
    return logger, window_manager, input_simulator, focus_manager


@pytest.mark.parametrize("window_title, expected_name", [
    ("MyChar - Dofus Retro", "MyChar"),
    ("Another-Char - Dofus Retro v1.47.20", "Another-Char"),
    ("(AccountName) MyChar - Dofus Retro", "MyChar"),
    ("  Spaced Name  - Dofus Retro", "Spaced Name"),
    ("No-Dofus-Retro-Tag", None),
    ("", None),
    ("Just a title", None),
])
def test_extract_character_name(mock_dependencies_for_group_manager, window_title, expected_name):
    """
    Tests the character name extraction from various window titles.
    """
    logger, window_manager, input_simulator, focus_manager = mock_dependencies_for_group_manager
    
    # --- Arrange ---
    # We don't need a config for this specific method test
    group_manager = GroupManager(logger, window_manager, input_simulator, focus_manager)
    
    # --- Act ---
    extracted_name = group_manager._extract_character_name(window_title)
    
    # --- Assert ---
    assert extracted_name == expected_name
