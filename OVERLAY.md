# Overlay — design

A single control panel, drawn over one game window, toggled by `F10`.

It shows the characters Minobot has detected, lets the player reorder them by drag & drop, toggle
behaviours, and rebind hotkeys. **Nothing it changes is written to `config.json`**: every change lives
for as long as the process does, and a restart goes back to the file. That is deliberate for now — a
config writer is a separate piece of work, and the overlay is useful without it.

## What the player sees

`F10` covers **the game window that holds the foreground at that moment**, filling it exactly — which
is what puts the panel on the right monitor when several are in use. `F10` again takes it away.

**`F10` pressed while the foreground is not a game window is ignored.** Not "fall back to the first
character", not "show it somewhere": nothing happens, and the log says why. The overlay belongs to a
character, and outside the game there is no character to belong to.

Once shown, **it stays shown across every focus change** — a notification pulling the focus to another
character, the player clicking into their browser from the taskbar, anything. It is always-on-top, and
always-on-top does not depend on the focus. That is the intended behaviour: `F10` is the only switch —
except that a character who leaves takes their panel with them, since a panel floating over a game
nobody is playing is a panel nobody can dismiss.

### It covers the game, not the frame

The panel is the **client area**, not the window's bounds: `WindowApi.clientArea` is `GetClientRect`
followed by `ClientToScreen`, and the client origin sits *below* the title bar. That is the whole
mechanism that keeps the panel off the minimize, maximize and close buttons — there is no arithmetic
guessing at how tall a title bar is, because Windows already knows and the client area already excludes
it.

The game is **dimmed, not hidden**: the player has to see the character they are configuring, so the
panel is a translucent sheet with the controls resting on it in a card, rather than an opaque board.

### It follows its window

A virtual thread re-reads the client area every 30 ms while the panel is up, and moves it if it has
changed — so dragging or resizing the game takes the panel with it. It lives exactly as long as the
panel does.

Polling rather than reacting is deliberate: Windows will not tell us a window moved without hooking its
messages, and hooking them means a message pump and a native callback for something a single call
answers. One `GetClientRect` thirty times a second is less than the multi-click makes in a burst.

### The price

**While the panel is up it takes the mouse over the whole game.** It is a window, and it covers the
client area, so a click meant for the game lands on it. That follows from covering the game rather than
floating in a corner of it, and it is why `F10` is a switch rather than a mode one plays in. It also
means the multi-click, which asks Windows what sits under the cursor, would find the panel there — so
do not expect the two to work together.

## The pieces

### `app/Settings` — the live configuration

`Config` stays the immutable record it is. `Settings` holds an `AtomicReference<Config>` and swaps a
whole new one in on every change, notifying its listeners.

`WindowManager` and `MultiWindowClicker` take a `Settings` instead of a `Config`. That is nearly the
whole of the drag & drop: `WindowManager.rank()` already re-reads `windowCycleOrder()` **on every
call**, so a reordered list is in effect on the very next `x2`. Nothing else has to know.

### `KeyboardMonitor` — rebinding while it runs

Today `hotkeys` is a bare `LinkedHashMap`, filled in the constructor *before* the polling thread
starts. That ordering is the only thing that makes it safe, and hot editing destroys it: the 50 Hz loop
would be iterating the map while the overlay mutates it.

So the map becomes `volatile` and is **replaced**, not mutated: an edit rebuilds it whole and swaps the
reference. The loop always reads a coherent map, and an edit costs one allocation, once per click.

A rebuild drops the per-hotkey cooldown state. That is harmless — the player just pressed a button in
a dialog, they are not mid-multiclick.

### `KeyboardMonitor.captureNext()` — reading a keybind without the focus

The overlay never holds the keyboard focus (see below), so a `JTextField` would be deaf. It does not
need one: `captureNext()` waits for any key of `MAIN_KEYS` to go down, reads the modifiers held at that
same instant, and returns `"shift+F7"`. It is the same `GetAsyncKeyState` poll as everything else, and
it works while Dofus is in the foreground.

The capturable vocabulary is exactly the registrable one (`MAIN_KEYS`: `F1`–`F12` and the mouse
buttons), which makes an invalid keybind impossible to enter.

**The registered hotkeys are silenced while it waits.** A key being *named* is not a key being *used*:
pressing F9 to bind it must not also rebuild the taskbar.

**And silencing them is not enough.** The key that closes the capture is still held when it closes, and
the polling loop may not have ticked once since it went down — so the loop's next tick sees a key that
was up and is now down, calls it an edge, and fires the very feature the player was only naming. That
is why both `captureNext()` (on its way out) and `rebuild()` (on a rebind) **seed each bound key's state
from the keyboard as it is**. A key already held is not a fresh press. Two tests hold this down, and
neither is theoretical: the first version failed exactly this way.

### `feature/OverlayController` — the logic

Which window anchors the overlay, which characters are listed, what a drag & drop means, what a rebind
means. It speaks of `GameWindow`s, it takes a `WindowApi`, and it is tested with `FakeWindowApi` like
the other five features.

### `ui/SwingOverlay` — the only class that knows about Swing

Behind an `OverlayView` interface, with a fake for the tests. Swing is a third door to the outside
world, next to `WindowApi` and `Input`, and it is kept as narrow as they are.

## Why Swing, and why no `SetWindowLongPtr` yet

Swing already does everything the overlay needs, at zero cost to the distribution — `java.desktop` is
already in `dist.modules`:

- `setAlwaysOnTop(true)` — stays above the game whatever holds the focus.
- `setType(Window.Type.UTILITY)` — out of the taskbar and out of Alt+Tab.
- `setFocusableWindowState(false)` — never takes the activation. **This is the one that matters**: an
  overlay that steals the foreground breaks `FocusManager`, the cycler, and the invitation relay that
  holds `takeOver()` from end to end.
- `setBackground(new Color(0, 0, 0, 0))` — per-pixel translucency.

A non-focusable window still **receives mouse events**, which is why the drag & drop and the toggles
work without ever taking the focus.

If a click on the overlay turns out to steal the foreground from Dofus anyway, then — and only then —
`WS_EX_NOACTIVATE` goes on its HWND through `User32`. There is no reason to widen `WindowApi` for a
problem we may not have.

JavaFX was considered and dropped: 50–70 MB of natives, OpenJFX jmods to add to the `dist` profile, and
an FX `Stage` takes the focus on click anyway — so the Win32 workaround would still be needed. The
weight buys nothing here.

## Known interaction: `F9`

`WindowReorder` hides every game window (`SW_HIDE`) and shows them again in order. For that second, the
overlay stays painted over nothing, anchored to a window that is not on screen. Harmless, and expected.

## Order of work

1. **Done.** `app/Settings`, `app/Feature`, and the hot rebinding in `KeyboardMonitor`. The only part
   that touches the core.
2. **Done.** `win32.Rect` and `GetWindowRect` (the anchor), `KeyboardMonitor.captureNext()`, the `ui`
   interfaces, and `feature/OverlayController` — the whole overlay but the drawing, tested with no
   screen at all through `FakeOverlayView`.
3. **Done.** `ui/SwingOverlay`, the `overlay_hotkey` setting (`F10`), and `Feature.OVERLAY`.

## The foreground: settled, and measured

**Swing alone is enough. `WS_EX_NOACTIVATE` is not needed, and must not be added.**

Both halves were measured against the real game, not reasoned about. *Showing* the panel does not take
the foreground (`GetForegroundWindow` reads the same window before and after). *Clicking* it — dragging
a character, rebinding a key — does not take it either: the character the player is on keeps the
screen throughout.

`setFocusableWindowState(false)` is what buys that, and it is load-bearing: **do not remove it**, and
do not reach for a Win32 extended style to "make sure". That ground has been walked. A panel that took
the foreground would land between two keystrokes of the invitation relay and send the rest of an
`/invite` into another character's window.

The two design choices that follow from it are not decoration either. **Nothing in the panel is
typed** — a keybind is read from the keyboard by `captureNext()`, because a text field needs the focus
the panel does not have. And **reordering is three mouse events**, not Swing's drag-and-drop stack,
which expects a focused window. Both would have to be undone to go back on this.

## What is left

**`F9` shows through it.** `WindowReorder` hides every game window and shows them again; for that
second the panel is painted over a window that is not on screen. Harmless, and expected.
