# Minobot

A quality-of-life application for **Dofus Retro**, for players running several accounts at once. It
sits silently in the background, listens for hotkeys and Windows notifications, and acts on the game
windows.

## Build

```shell
./mvnw verify              # compile + tests
./mvnw -Pdist verify       # → target/dist/Minobot/Minobot.exe (portable, runtime included)
java -jar target/minobot.jar
```

The Maven wrapper downloads Maven itself; only a **JDK 25** is required. If `JAVA_HOME` does not
point at it, prefix the command: `JAVA_HOME=C:\path\to\jdk-25 ./mvnw verify`.

## Architecture

```
fr.minobot
├── Main            Entry point; single-instance lock on a ServerSocket (127.0.0.1:12345).
├── app/            MinobotApp (wires everything), Config, ConfigLoader, LoggerSetup.
├── win32/          WindowApi (the interface), User32 (the FFM implementation), Win32, Point.
├── core/           The Windows mechanics: WindowManager, FocusManager, KeyboardMonitor,
│                   NotificationManager, SystemTrayManager, Input / InputSimulator.
└── feature/        The five user-facing features (below).
```

**Two interfaces are the only doors to the outside world:** `win32.WindowApi` (the screen) and
`core.Input` (the keyboard and mouse). Everything else is code that runs in a test, on any OS, with no
game running — which is why there are 82 tests.

**Keep it that way.** Do not call `user32.dll` or `java.awt.Robot` from anywhere but their
implementations: a feature that reaches past `WindowApi` becomes untestable.

### Config

`Config` holds what a *player* changes: their `window_cycle_order`, their `multiclick_exclude`, their
hotkeys, their `log_level`. Nothing else. A timing, a mouse button, a keyword of the game's window
titles, a dry-run flag — those are dictated by Windows or by the game, not by the player, and belong
in a constant next to the code that reads it. **Adding a field to `Config` is a claim that a player
has a reason to change it**; a knob nobody turns is a knob that silently breaks a feature when someone
does. An empty hotkey disables its feature; there is no `*_enabled` flag.

### Concurrency

- `KeyboardMonitor` polls on a dedicated **platform** thread (a 50 Hz loop of native calls — a virtual
  thread would buy nothing and pinning its carrier would be a trap).
- **Every hotkey and notification callback runs on its own virtual thread**, the equivalent of
  `asyncio.create_task`. A long `inviteAll` must never deafen the other hotkeys.
- Anything that sleeps (the focus sequence, the invitation relay) is therefore free to do so.

### The foreground is a single resource

There is one screen, and several threads want it. `FocusManager` is what keeps them from fighting:

- `focus(hwnd)` is **deliberate** — a hotkey asked for it. It waits its turn, and it **returns whether
  the window really holds the foreground**. A caller that goes on to type *must* check it: keystrokes
  sent to a window that never came up land in whichever one did.
- `focusIfIdle(hwnd)` is **opportunistic** — a toast suggested it. It stands aside rather than queue up.
- A feature that focuses *and types* holds `focus.takeOver()` for its whole sequence. Without it the
  notification auto-focus, which reacts to the same toasts the invitation relay waits on, lands between
  two keystrokes and sends the rest of a `/invite` to another character's window.

## The features

When adding one, **make sure the others still work**: they all compete for the focus.

### Multi-window click — `x1`, and `shift+x1` to reset

Replays one click on every game window at the same spot. It must be (1) as fast as possible, (2) never
cost the main character its focus, and (3) reach every character.

The click is **posted** (`PostMessage`), not simulated, so the windows never take the focus. The
position travels through the **client** area, not the screen: the same client coordinates land on the
same in-game spot in every window, wherever they sit on the desktop.

A posted click makes a background window flash orange in the taskbar. `FlashWindowEx(FLASHW_STOP)`
brackets the click to cancel it, and `shift+x1` clears whatever slipped through by visiting each
window in turn.

### Group invitation — `F8`

A relay: the first character invites the second, who accepts and invites the third, and so on. Each
step waits for the game's own **Windows toast** to confirm the invitation landed, rather than guessing
a delay. No toast within five seconds and the relay **stops there**: the ENTER that accepts an
invitation, pressed in a window that has none on screen, opens the chat instead — and the next
`/invite` is then typed into the game rather than into the chat. The relay owns the foreground from
end to end (`focus.takeOver()`); the same toasts feed the auto-focus, which must not answer them here.

### Window cycler — `x2` / `shift+x2`

Cycles the focus through the windows in the order of `window_cycle_order`. Only the windows of the
**current monitor** take part. Far better than Alt+Tab with several accounts.

### Window reorder — `F9`

Rebuilds the taskbar order: Windows offers no way to reorder its buttons, so the sequence hides every
game window and shows them again in the configured order. **The windows are hidden in the middle of
this** — every failure path must end by showing them again, or the player loses them.

### Notification auto-focus — no hotkey

The game raises a toast when a background character is attacked, messaged or invited; that toast pulls
the focus to them. It is a *smart* focus: if the player is typing at that moment, their keystrokes are
not stolen.

## Conventions

- **Code and comments in English.**
- A comment states a constraint the code cannot show — a Windows quirk, a race, a reason. Never what
  the next line does.
- Windows handles are plain `long` (`Win32.NULL_HANDLE` for none), which is what an `HWND` is on the
  Win64 ABI.
- A native call that *cannot be made* (a wrong FFM signature) throws `Win32Exception`. A call that
  *fails* (a dead handle, a refused focus) returns `false` / `Optional.empty()` — that is normal, and
  the callers tolerate it.
- Never pack a mouse `lParam` by hand: call `Win32.makeLParam`. It is the likeliest silent bug of the
  whole codebase, and it is locked down by `Win32Test`.
- Tests drive `FakeWindowApi` (an in-memory desktop) and `FakeInput`. If a change is hard to test,
  that is a signal about the change, not about the tests.
