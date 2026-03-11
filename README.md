# Minobot

Minobot is a quality-of-life application for the game **Dofus Retro**, designed to streamline multi-account gameplay. It runs as a silent background process, listening for hotkeys and system events to automate tedious actions.

## Setup

### Requirements

- Python 3.x
- Windows OS

### Installation

1. Clone the repository or download the source code.
2. Install the required Python packages:
   ```shell
   pip install -r requirements.txt
   ```
3. Configure the application by editing the `config.json` file. See the **Configuration** section below for details.
4. Run the application:
   ```shell
   python main.py
   ```
   A system tray icon will appear, indicating that Minobot is running. To stop the application, right-click the icon and select "Exit".

---

## Features

### 1. Multi-Window Clicker

Synchronizes mouse clicks across all game windows. When you click in one window, the same click is replicated in all other windows at the same relative position.

- **Hotkey**: `X1` (mouse side button "Back")
- **Configuration**:
  - `multiclick_enabled`: `true` or `false` to enable/disable.
  - `multiclick_button`: The mouse button to trigger the action (e.g., "x1", "x2", "middle").

### 2. Group Invitation

Automatically invites all your characters to a group with a single key press. The character in the foreground window will become the leader and invite all others.

- **Hotkey**: `F8`
- **Configuration**:
  - `group_invite_enabled`: `true` or `false`.
  - `group_invite_hotkey`: The key to trigger the action.

### 3. Window Cycler

Cycles focus through your game windows in a predefined, consistent order, making it much faster than `Alt+Tab`.

- **Hotkeys**:
  - **Next Window**: `X2` (mouse side button "Forward")
  - **Previous Window**: `SHIFT+X2`
- **Configuration**:
  - `window_cycle_order`: **(Required)** A list of your character names in the desired cycle order. Example: `["Cra-Char", "Eni-Char", "Panda-Char"]`.
  - `window_cycle_next_hotkey`: Hotkey for the "next" action.
  - `window_cycle_prev_hotkey`: Hotkey for the "previous" action.

### 4. Window Reorder

Visually rearranges the game windows in your Windows taskbar to match the order defined in `window_cycle_order`. This is useful if your windows launch in a messy order.

- **Hotkey**: `F9`
- **How it works**: It quickly hides and shows all game windows in the correct sequence, forcing Windows to re-draw the taskbar buttons in order.
- **Configuration**:
  - `window_reorder_hotkey`: The key to trigger the action.

### 5. Notification Auto-Focus

Automatically brings the corresponding character's window to the foreground when a game notification is received (e.g., "end of fight", "trade received").

- **No Hotkey**: This is a background process.
- **Configuration**:
  - `game_keywords`: Keywords to identify game notifications (default: `["Dofus"]`).

---

## Configuration (`config.json`)

This file contains all the settings for the application.

```json
{
  "log_level": "INFO",
  
  "multiclick_enabled": true,
  "multiclick_button": "x1",
  
  "group_invite_enabled": true,
  "group_invite_hotkey": "F8",
  
  "window_cycle_order": [
    "CharacterOne",
    "CharacterTwo",
    "CharacterThree"
  ],
  "window_cycle_next_hotkey": "x2",
  "window_cycle_prev_hotkey": "shift+x2",
  
  "window_reorder_hotkey": "F9",
  
  "focus_cooldown": 0.1
}
```
*This is a simplified example. The actual file contains more technical settings.*
