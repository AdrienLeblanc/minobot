import logging
import sys
from unittest.mock import MagicMock

import pytest

# Add src to path to allow imports
sys.path.insert(0, 'src')

from src.features.notification_listener import NotificationListener


@pytest.fixture
def mock_dependencies_for_listener():
    """Pytest fixture for NotificationListener dependencies."""
    logger = logging.getLogger("test_listener")
    window_manager = MagicMock()
    focus_manager = MagicMock()
    return logger, window_manager, focus_manager


@pytest.mark.parametrize("notification_title, configured_separators, expected_name", [
    # Default separators: " - ", ": ", " | "
    ("MyChar - Message", [" - ", ": ", " | "], "MyChar"),
    ("MyChar: Message", [" - ", ": ", " | "], "MyChar"),
    ("MyChar | Message", [" - ", ": ", " | "], "MyChar"),
    ("MyChar without separator", [" - ", ": ", " | "], "MyChar without separator"),
    
    # Custom separators
    ("MyChar>>Message", [">>"], "MyChar"),
    ("AnotherChar - Test", [":"], "AnotherChar - Test"), # Separator not in list
    
    # Edge cases
    ("  Spaced Name  - Message", [" - "], "Spaced Name"),
    ("", [" - "], ""),
])
def test_extract_character_name_from_notification(mock_dependencies_for_listener, notification_title, configured_separators, expected_name):
    """
    Tests character name extraction from notification titles with various separators.
    """
    logger, window_manager, focus_manager = mock_dependencies_for_listener
    
    # --- Arrange ---
    config = {
        "character_separators": configured_separators
    }
    
    listener = NotificationListener(logger, window_manager, focus_manager, config)
    
    # --- Act ---
    extracted_name = listener._extract_character_name(notification_title)
    
    # --- Assert ---
    assert extracted_name == expected_name
