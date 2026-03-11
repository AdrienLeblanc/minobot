import logging
import os
import sys
from logging.handlers import RotatingFileHandler
from typing import Dict, Any, Optional

def setup_logger(config: Optional[Dict[str, Any]] = None) -> logging.Logger:
    """
    Configures the application logger based on the provided configuration.

    Args:
        config: A dictionary containing logging configuration:
            - log_level: The minimum log level (e.g., "INFO", "DEBUG").
            - log_to_file: Boolean to enable file logging.
            - log_file_path: Path to the log file.

    Returns:
        A configured logging.Logger instance.
    """
    if config is None:
        config = {}

    log_level_str = config.get("log_level", "INFO")
    log_to_file = config.get("log_to_file", False)
    log_file_path = config.get("log_file_path", "logs/minobot.log")

    logger = logging.getLogger("Minobot")
    
    # Stop messages from being passed to the root logger
    logger.propagate = False
    
    # Reset existing handlers to avoid duplication if called multiple times
    if logger.handlers:
        logger.handlers.clear()

    # Convert string level to numeric constant
    numeric_level = getattr(logging, log_level_str.upper(), logging.INFO)
    logger.setLevel(numeric_level)

    # Console Handler
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(numeric_level)

    console_formatter = logging.Formatter(
        "%(asctime)s | %(levelname)s | %(message)s",
        datefmt="%H:%M:%S"
    )
    console_handler.setFormatter(console_formatter)
    logger.addHandler(console_handler)

    # File Handler (Optional)
    if log_to_file:
        try:
            # Ensure the directory exists
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
                datefmt="%Y-%m-%d %H:%M:%S"
            )
            file_handler.setFormatter(file_formatter)
            logger.addHandler(file_handler)

            logger.info(f"Logging enabled to file: {log_file_path}")

        except OSError as e:
            logger.warning(f"Could not setup file logging: {e}")
        except Exception as e:
            logger.error(f"Unexpected error setting up file logging: {e}", exc_info=True)

    return logger
