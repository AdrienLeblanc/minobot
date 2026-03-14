import json
import logging
import os
from typing import Dict, Any

# Ta configuration par défaut avec l'ordre exact de tes personnages
DEFAULT_CONFIG: Dict[str, Any] = {
    "poll_interval": 0.5,
    "notification_batch_size": 10,
    "focus_cooldown": 0.1,
    "window_refresh_interval": 30,
    "game_keywords": ["Dofus"],
    "character_separators": [" - ", ": ", " | "],
    "log_level": "INFO",
    "log_to_file": True,
    "log_file_path": "logs/minobot.log",
    "multiclick_enabled": True,
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
    "window_cycle_prev_hotkey": "shift+x2",
    "smart_focus_enabled": True,
    "smart_focus_threshold": 2.0
}

logger = logging.getLogger("ConfigLoader")


def load_config(config_path: str) -> Dict[str, Any]:
    """
    1. Le .exe vérifie si un fichier config.json se tient à côté de lui.
    2. Si oui : utiliser cette configuration.
    3. Sinon : créer le fichier avec ce qu'il y a dans DEFAULT_CONFIG.
    4. Les modifs sont prises en compte au redémarrage car on relit le fichier.
    """
    config = DEFAULT_CONFIG.copy()

    # 1. Vérifie si config.json existe
    if os.path.exists(config_path):
        # 2. Si oui : utiliser cette configuration
        try:
            with open(config_path, 'r', encoding='utf-8') as f:
                user_config = json.load(f)
                if isinstance(user_config, dict):
                    config.update(user_config)
                    logger.info(f"Configuration chargée et appliquée depuis {config_path}")
                else:
                    logger.warning(f"Le fichier {config_path} est invalide. Utilisation des défauts.")
        except Exception as e:
            logger.error(f"Erreur lors de la lecture de la config : {e}")
            logger.info("Utilisation des défauts.")
    else:
        # 3. Sinon : créer le fichier avec DEFAULT_CONFIG
        logger.info(f"Fichier config non trouvé. Création de {config_path}")
        try:
            os.makedirs(os.path.dirname(config_path), exist_ok=True)
            with open(config_path, 'w', encoding='utf-8') as f:
                json.dump(DEFAULT_CONFIG, f, indent=4, ensure_ascii=False)
            logger.info(f"Fichier {config_path} généré avec succès.")
        except Exception as e:
            logger.error(f"Impossible de créer le fichier de config : {e}")

    return config
