import json
import logging
import os
from typing import Dict, Any

# Configuration par défaut
DEFAULT_CONFIG: Dict[str, Any] = {
    "poll_interval": 0.5,
    "notification_batch_size": 10,
    "focus_cooldown": 0.1,
    "window_refresh_interval": 30,
    "game_keywords": ["Dofus"],
    "character_separators": [" - ", ": ", " | "],
    "log_level": "INFO",
    "log_to_file": False,
    "log_file_path": "logs/minobot.log",
    "multiclick_enabled": True,
    "multiclick_combination": "",
    "multiclick_button": "x1",
    "multiclick_delay": 0.01,
    "multiclick_cooldown": 0.1,
    "multiclick_restore_focus": False,
    "multiclick_dry_run": False,
    "multiclick_exclude": [],
    "window_cycle_order": [
        "Panda",
        "Cra",
        "Eni",
        "Panda",
        "Iop"
    ],
    "window_cycle_next_hotkey": "x2",
    "window_cycle_prev_hotkey": "shift+x2"
}

logger = logging.getLogger("ConfigLoader")


def load_config(config_path: str = "config.json") -> Dict[str, Any]:
    """
    Loads configuration from a JSON file.
    Merges user configuration with default values.

    Args:
        config_path: Path to the JSON configuration file.

    Returns:
        A dictionary containing the merged configuration.
    """
    config = DEFAULT_CONFIG.copy()

    if os.path.exists(config_path):
        try:
            with open(config_path, 'r', encoding='utf-8') as f:
                user_config = json.load(f)

                # Merge user config into default config
                # Note: This is a shallow merge. Nested dictionaries would need deep merge if present.
                if isinstance(user_config, dict):
                    config.update(user_config)
                    logger.info(f"Configuration loaded from {config_path}")
                else:
                    logger.warning(f"Config file {config_path} is not a valid JSON object.")

        except json.JSONDecodeError as e:
            logger.error(f"Error parsing config file {config_path}: {e}")
            logger.info("Using default configuration.")
        except Exception as e:
            logger.error(f"Could not load config file {config_path}: {e}")
            logger.info("Using default configuration.")
    else:
        logger.warning(f"Config file {config_path} not found. Using defaults.")
        # Optionally save the default config if it doesn't exist
        # save_default_config(config_path) 

    return config


def save_default_config(config_path: str = "config.json") -> None:
    """
    Creates a default configuration file.

    Args:
        config_path: Path where to save the configuration.
    """
    try:
        with open(config_path, 'w', encoding='utf-8') as f:
            json.dump(DEFAULT_CONFIG, f, indent=2, ensure_ascii=False)
        logger.info(f"Default configuration saved to {config_path}")
    except Exception as e:
        logger.error(f"Error saving default config to {config_path}: {e}")
