import logging
import sys
from unittest.mock import MagicMock

import pytest

# Add src to path to allow imports
sys.path.insert(0, 'src')

from src.core.window_manager import WindowManager


@pytest.fixture
def mock_dependencies_for_window_manager():
    """Pytest fixture for NotificationListener dependencies."""
    logger = logging.getLogger("test_listener")
    return logger


@pytest.mark.parametrize("notification_title, configured_separators, expected_name", [
    # Default separators: " - ", ": ", " | "
    ("MyChar - Message", [" - ", ": ", " | "], "MyChar"),
    ("MyChar: Message", [" - ", ": ", " | "], "MyChar"),
    ("MyChar | Message", [" - ", ": ", " | "], "MyChar"),
    ("MyChar without separator", [" - ", ": ", " | "], "MyChar without separator"),

    # Custom separators
    ("MyChar>>Message", [">>"], "MyChar"),
    ("AnotherChar - Test", [":"], "AnotherChar - Test"),  # Separator not in list

    # Edge cases
    ("  Spaced Name  - Message", [" - "], "Spaced Name"),
    ("", [" - "], ""),
])
def test_extract_character_name_from_notification(mock_dependencies_for_window_manager, notification_title,
                                                  configured_separators, expected_name):
    """
    Tests character name extraction from notification titles with various separators.
    """
    logger = mock_dependencies_for_window_manager

    # --- Arrange ---
    config = {
        "character_separators": configured_separators
    }

    window_manager = WindowManager(logger, config)

    # --- Act ---
    extracted_name = window_manager.extract_character_name(notification_title)

    # --- Assert ---
    assert extracted_name == expected_name
