import logging
import sys
from logging.handlers import RotatingFileHandler
import os


def setup_logger(config=None):

    if config is None:
        config = {}

    log_level = config.get("log_level", "INFO")
    log_to_file = config.get("log_to_file", False)
    log_file_path = config.get("log_file_path", "dofus_focus_bot.log")

    logger = logging.getLogger("bot")

    # Convertir le niveau de log string en constante
    numeric_level = getattr(logging, log_level.upper(), logging.INFO)
    logger.setLevel(numeric_level)

    # Éviter les duplications de handlers
    if logger.handlers:
        logger.handlers.clear()

    # Handler console
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(numeric_level)

    formatter = logging.Formatter(
        "%(asctime)s | %(levelname)s | %(message)s",
        "%H:%M:%S"
    )

    console_handler.setFormatter(formatter)
    logger.addHandler(console_handler)

    # Handler fichier (optionnel)
    if log_to_file:
        try:
            # Créer le répertoire si nécessaire
            log_dir = os.path.dirname(log_file_path)
            if log_dir and not os.path.exists(log_dir):
                os.makedirs(log_dir)

            file_handler = RotatingFileHandler(
                log_file_path,
                maxBytes=5 * 1024 * 1024,  # 5 MB
                backupCount=3,
                encoding='utf-8'
            )
            file_handler.setLevel(numeric_level)

            file_formatter = logging.Formatter(
                "%(asctime)s | %(levelname)s | %(message)s",
                "%Y-%m-%d %H:%M:%S"
            )

            file_handler.setFormatter(file_formatter)
            logger.addHandler(file_handler)

            logger.info(f"Logging to file: {log_file_path}")

        except Exception as e:
            logger.warning(f"Could not setup file logging: {e}")

    return logger