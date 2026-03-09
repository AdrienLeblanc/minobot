import asyncio

from core.logger import setup_logger
from core.window_manager import WindowManager
from core.focus_manager import FocusManager
from core.notification_listener import NotificationListener


async def main():

    logger = setup_logger()

    window_manager = WindowManager(logger)
    window_manager.refresh()

    focus_manager = FocusManager(logger)

    listener = NotificationListener(
        logger,
        window_manager,
        focus_manager
    )

    await listener.start()


if __name__ == "__main__":
    asyncio.run(main())