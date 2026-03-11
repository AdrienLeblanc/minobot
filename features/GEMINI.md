# Features

When a new feature is added, make sure all others are still operational.

## Notification listener
> notification_listener.py

Minobot is able to listen Windows notifications, since the game produces some when actions occur.
We use them to their full potential for our set of features.

The notifications always follow the same scheme : a **title** and a **text**.

### Auto focus
When a character receives a notification, minobot must place the window at the foreground and set focus on it.

## Group invitation
> group_manager.py

The player is able to invite of all his characters just by pressing a hotkey.

The current implementation is the following:
1. List all active characters
2. The first invites the second by typing ``/invite characterName2`` in chat
3. The focus is set to second character which accepts by pressing ``Enter``
4. The focus is set back to the first character
5. etc.

## Multi-click
> multi_window_clicker.py

The player is able to move all his characters just by pressing a hotkey.

The current implementation must follow these guidelines:
1. Make it as quick as possible
2. The focus on the main character mustn't be lost (smoothest experience)
3. All characters are expected to move

A current issue is known: notifications on a background window produces a flash on the taskbar.

## Window Cycler
> window_cycler.py

Allows the player to cycle through game windows in a predefined order, which is much more efficient than Alt+Tab when playing with multiple accounts.

The cycle order is defined in the `config.json` file.

## Window Reorder
> window_reorder.py

Allows the player to visually reorder the game windows in the Windows taskbar according to the predefined cycle order.

This is useful when windows are launched in the wrong order, causing visual chaos in the taskbar. The feature works by hiding and showing all windows in the correct sequence.
