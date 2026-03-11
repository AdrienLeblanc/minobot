import asyncio
import logging
import socket
import sys
from typing import Optional

from app.app import MinobotApp

# Port pour le verrouillage de l'instance unique
LOCK_PORT: int = 12345


def acquire_instance_lock(port: int) -> Optional[socket.socket]:
    """
    Tente d'acquérir un verrou réseau pour garantir une instance unique.
    
    Args:
        port: Le port TCP à écouter.
        
    Returns:
        L'objet socket si le verrou est acquis, None sinon.
    """
    try:
        lock_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        lock_socket.bind(("127.0.0.1", port))
        return lock_socket
    except socket.error:
        return None


def main() -> None:
    """
    Point d'entrée principal de l'application.
    Initialise le logger, vérifie l'instance unique et lance l'application.
    """
    # Configuration minimale du logging pour le démarrage
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s | %(levelname)s | %(message)s',
        datefmt='%H:%M:%S'
    )
    logger = logging.getLogger("MinobotBoot")

    # Tenter de créer un verrou pour l'instance unique
    lock_socket = acquire_instance_lock(LOCK_PORT)

    if not lock_socket:
        logger.error(
            f"Une autre instance de Minobot est déjà en cours d'exécution sur le port {LOCK_PORT}. "
            "Cette instance va se fermer."
        )
        sys.exit(0)

    app: Optional[MinobotApp] = None

    try:
        app = MinobotApp()
        asyncio.run(app.run())

    except KeyboardInterrupt:
        logger.info("Application stopped by user (KeyboardInterrupt).")

    except Exception as e:
        logger.critical(f"A fatal error occurred during application startup: {e}", exc_info=True)

    finally:
        if app:
            app.stop()

        if lock_socket:
            lock_socket.close()
            logger.debug("Instance lock released.")

        sys.exit(0)


if __name__ == "__main__":
    main()
