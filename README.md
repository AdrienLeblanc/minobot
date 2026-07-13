# Minobot

Minobot is a quality-of-life application for the game **Dofus Retro**, designed to streamline multi-account gameplay. It runs as a silent background process, listening for hotkeys and system events to automate tedious actions.

## Setup

### Using a release

Minobot ships as a self-contained folder: unzip it anywhere and run `Minobot.exe`. There is nothing
to install — not even Java, which travels with it.

On first launch it writes a `config.json` next to the executable. Edit it (see **Configuration**
below), then restart. A system tray icon shows that Minobot is running; right-click it and choose
**Quit** to stop.

### Building from source

Requirements: **JDK 25** and Windows. Maven is not needed — the wrapper downloads it.

```shell
# Run the test suite
./mvnw verify

# Build the distributable folder: target/dist/Minobot/Minobot.exe
./mvnw -Pdist verify
```

To run it straight from the build without packaging:

```shell
./mvnw package && java -jar target/minobot.jar
```

> If `JAVA_HOME` is not set to your JDK 25, prefix the commands with it:
> `JAVA_HOME=C:\path\to\jdk-25 ./mvnw verify`

---

## Features

### 1. Multi-Window Clicker

Synchronizes mouse clicks across all game windows. When you click in one window, the same click is replicated in all other windows at the same relative position.

- **Hotkey**: `X1` (mouse side button "Back")
- **Configuration**:
  - `multiclick_enabled`: `true` or `false` to enable/disable.
  - `multiclick_hotkey`: The key that triggers the action (default `x1`).
  - `multiclick_button`: The mouse button that gets *sent* to the windows — `left`, `right` or
    `middle` (default `left`). This is not the trigger; the trigger is `multiclick_hotkey`.
  - `multiclick_exclude`: Character names to leave out of the click.

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
  "multiclick_hotkey": "x1",
  "multiclick_button": "left",
  
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
