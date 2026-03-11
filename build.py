import logging
import os
import subprocess
import sys
from pathlib import Path

# Basic logging setup for the build script
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s | %(levelname)s | %(message)s',
    datefmt='%H:%M:%S'
)
logger = logging.getLogger("BuildScript")


def install_pyinstaller() -> None:
    """
    Checks if PyInstaller is installed and installs it if not.
    """
    try:
        import PyInstaller  # noqa: F401
        logger.info("PyInstaller is already installed.")
    except ImportError:
        logger.info("PyInstaller not found, installing...")
        try:
            subprocess.check_call([sys.executable, "-m", "pip", "install", "pyinstaller"])
            logger.info("PyInstaller installed successfully.")
        except subprocess.CalledProcessError as e:
            logger.critical(f"Failed to install PyInstaller: {e}")
            sys.exit(1)


def build_executable(script_path: Path) -> None:
    """
    Builds the executable using PyInstaller.

    Args:
        script_path: The path to the main Python script.
    """
    executable_name = "minobot.exe"

    # PyInstaller command arguments
    # --onefile: Create a single executable file.
    # --noconsole: Do not provide a console window for the GUI application.
    # --name: The name of the executable.
    # --hidden-import: Explicitly include modules that PyInstaller might miss.
    cmd = [
        sys.executable, "-m", "PyInstaller",
        "--onefile",
        "--noconsole",
        f"--name={executable_name}",
        "--hidden-import=pystray._win32",
        "--hidden-import=win32api",
        "--hidden-import=win32gui",
        "--hidden-import=pywintypes",
        "--hidden-import=pyautogui",
        str(script_path)
    ]
    
    logger.info("Running PyInstaller command...")
    logger.debug(f"Command: {' '.join(cmd)}")
    
    try:
        subprocess.check_call(cmd)
        logger.info("Build process completed successfully.")
    except subprocess.CalledProcessError as e:
        logger.critical(f"PyInstaller build failed: {e}")
        sys.exit(1)


def main() -> None:
    """
    Main function to run the build process.
    """
    script_to_build = Path("main.py")

    if not script_to_build.exists():
        logger.critical(f"The main script '{script_to_build}' was not found.")
        sys.exit(1)

    install_pyinstaller()
    build_executable(script_to_build)
    
    logger.info(f"✅ Build complete. The executable is in the '{Path('dist').resolve()}' folder.")


if __name__ == "__main__":
    main()
