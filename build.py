import subprocess
import sys
import os

def install_pyinstaller():
    try:
        import PyInstaller  # noqa: F401
    except ImportError:
        print("PyInstaller not found, installing...")
        subprocess.check_call([sys.executable, "-m", "pip", "install", "pyinstaller"])

def build_exe(script_name):
    cmd = [
        sys.executable, "-m", "PyInstaller",
        "--onefile",
        "--noconsole",
        "--name=minobot",
        script_name
    ]
    print("Running:", " ".join(cmd))
    subprocess.check_call(cmd)

if __name__ == "__main__":
    script = "main.py"
    if not os.path.exists(script):
        print(f"The file {script} was not found.")
        sys.exit(1)
    install_pyinstaller()
    build_exe(script)
    print("✅ Build complete. The executable is in the 'dist' folder.")
