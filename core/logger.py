import logging
import sys


def setup_logger():

    logger = logging.getLogger("bot")
    logger.setLevel(logging.DEBUG)

    handler = logging.StreamHandler(sys.stdout)

    formatter = logging.Formatter(
        "%(asctime)s | %(levelname)s | %(message)s",
        "%H:%M:%S"
    )

    handler.setFormatter(formatter)
    logger.addHandler(handler)

    return logger