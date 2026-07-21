# Core — the Windows mechanics

`core/` is the machinery every feature stands on: `WindowManager`, `FocusManager`, `KeyboardMonitor`,
`NotificationManager`, `FlashSuppressor`, `SystemTrayManager`, `input/` (`Input`, `InputSimulator`),
and `domain/` (`GameWindow`, `Character`, `DofusClass`, `Sex`, `Notification`). It speaks of windows,
handles and messages so the features above it can speak of characters.

## Concurrency

- `KeyboardMonitor` polls on a dedicated **platform** thread (a 50 Hz loop of native calls — a virtual
  thread would buy nothing and pinning its carrier would be a trap). Its hotkey table is **replaced,
  never mutated**: a rebind assembles the next one aside and swaps the reference, because the loop is
  reading it at the very moment the player edits a key. And a rebind seeds each key's state from the
  keyboard as it *is* — a key still held is not a fresh press, or naming a hotkey would fire it.
- **Every hotkey and notification callback runs on its own virtual thread**, the equivalent of
  `asyncio.create_task`. A long `inviteAll` must never deafen the other hotkeys.
- Anything that sleeps (the focus sequence, the invitation relay) is therefore free to do so.

## The foreground is a single resource

There is one screen, and several threads want it. `FocusManager` is what keeps them from fighting:

- `focus(hwnd)` is **deliberate** — a hotkey asked for it. It waits its turn, and it **returns whether
  the window really holds the foreground**. A caller that goes on to type *must* check it: keystrokes
  sent to a window that never came up land in whichever one did.
- `focusIfIdle(hwnd)` is **opportunistic** — a toast suggested it. It stands aside rather than queue up.
- A feature that focuses *and types* holds `focus.takeOver()` for its whole sequence. Without it the
  notification auto-focus, which reacts to the same toasts the invitation relay waits on, lands between
  two keystrokes and sends the rest of a `/invite` to another character's window.
