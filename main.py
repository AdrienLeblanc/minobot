import asyncio
import socket
import sys

from core.app import MinobotApp

# Port pour le verrouillage de l'instance unique
LOCK_PORT = 12345


def main():
    """Point d'entrée principal de l'application."""

    # Tenter de créer un verrou pour l'instance unique
    try:
        lock_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        lock_socket.bind(("127.0.0.1", LOCK_PORT))
    except socket.error:
        print(
            f"Une autre instance de Minobot est déjà en cours d'exécution sur le port {LOCK_PORT}. Cette instance va se fermer.")
        sys.exit(0)

    app = None
    try:
        app = MinobotApp()
        asyncio.run(app.run())
    except KeyboardInterrupt:
        print("\nApplication stopped by user.")
    except Exception as e:
        # Les logs ne sont peut-être pas encore initialisés, on imprime
        print(f"A fatal error occurred during application startup: {e}")
    finally:
        if app:
            app.stop()
        if lock_socket:
            lock_socket.close()
        sys.exit(0)


if __name__ == "__main__":
    main()
