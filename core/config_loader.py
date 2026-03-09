import json
import os
from typing import Dict, Any


DEFAULT_CONFIG = {
    "poll_interval": 0.5,
    "notification_batch_size": 10,
    "focus_cooldown": 1,
    "window_refresh_interval": 30,
    "game_keywords": ["Dofus"],
    "character_separators": [" - ", ": ", " | "],
    "log_level": "INFO",
    "log_to_file": False,
    "log_file_path": "logs/dofus_focus_bot.log",
    "multiclick_enabled": True,
    "multiclick_combination": "LCTRL+LALT",
    "multiclick_delay": 0.01,
    "multiclick_cooldown": 0.1,
    "multiclick_restore_focus": True
}


def load_config(config_path: str = "config.json") -> Dict[str, Any]:
    """
    Charge la configuration depuis un fichier JSON.
    Si le fichier n'existe pas, utilise la configuration par défaut.
    """

    config = DEFAULT_CONFIG.copy()

    if os.path.exists(config_path):
        try:
            with open(config_path, 'r', encoding='utf-8') as f:
                user_config = json.load(f)
                config.update(user_config)
                print(f"Configuration loaded from {config_path}")
        except Exception as e:
            print(f"Warning: Could not load config file {config_path}: {e}")
            print("Using default configuration")
    else:
        print(f"Config file {config_path} not found, using default configuration")

    return config


def save_default_config(config_path: str = "config.json"):
    """Crée un fichier de configuration par défaut"""

    try:
        with open(config_path, 'w', encoding='utf-8') as f:
            json.dump(DEFAULT_CONFIG, f, indent=2, ensure_ascii=False)
        print(f"Default configuration saved to {config_path}")
    except Exception as e:
        print(f"Error saving default config: {e}")
