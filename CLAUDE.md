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
├── app/            MinobotApp (wires everything), Config, Settings, Feature, ConfigLoader, LoggerSetup.
├── win32/          WindowApi (the interface), User32 (the FFM implementation), Win32, Point.
├── core/           The Windows mechanics: WindowManager, FocusManager, KeyboardMonitor,
│                   NotificationManager, FlashSuppressor, SystemTrayManager, Input / InputSimulator.
│   └── domain/     GameWindow (a character's window, and their name), Character (the player's part of
│                   a character: name, class, sex), DofusClass, Sex, Notification (a toast).
├── ui/             OverlayView (the panel), OverlayContent, OverlayActions. Interfaces only.
└── feature/        The user-facing features (below), and OverlayController.
```

**Three interfaces are the only doors to the outside world:** `win32.WindowApi` (the screen),
`core.Input` (the keyboard and mouse), and `ui.OverlayView` (the panel we draw). Everything else is
code that runs in a test, on any OS, with no game running — which is why there are 122 tests.

**Keep it that way.** Do not call `user32.dll`, `java.awt.Robot` or Swing from anywhere but their
implementations: a feature that reaches past `WindowApi` becomes untestable.

### A feature speaks of characters; only the core speaks of windows

A `long` is a handle, `PostMessage` is a message, `SW_HIDE` is a flag — and none of them are what the
player asked for. The player asked for *every character to click the same spot*, for *the next character*,
for *their characters back in order in the taskbar*. A feature that reads like the story the player would
tell is a feature whose bugs are visible: `GroupManager` says a leader invites, the invitee accepts, and
the relay stops — and the reason the relay must stop is right there to be seen.

So a feature loops over **characters** (`GameWindow`, which carries a `name()`), and it keeps whatever
Windows demands — the retry on a refused `PostMessage`, the `lParam`, the `SW_RESTORE` — in one named
leaf at the bottom of the file. **The technical detail is not hidden, it is placed**: it stops being the
first thing read, and the constraint that justifies it is the comment above it.

### Config

`Config` holds what a *player* changes: their `characters` (each a `Character` — name, and the class and
sex they pinned — in cycle order), their `multiclick_exclude`, their
hotkeys, their `log_level`, their `overlay_scale`, their `auto_pass_turn`, their `auto_accept_trade`. Nothing else. A timing, a mouse button, a keyword of the game's window
titles, a dry-run flag — those are dictated by Windows or by the game, not by the player, and belong
in a constant next to the code that reads it. **Adding a field to `Config` is a claim that a player
has a reason to change it**; a knob nobody turns is a knob that silently breaks a feature when someone
does. An empty hotkey disables its feature; there is no `*_enabled` flag — save two: `auto_pass_turn`
and `auto_accept_trade`, the two overlay switches, because those features have no hotkey to blank. They
are the only booleans here, and they earn their place by being things the player deliberately flips on
the overlay.

`Feature` enumerates the features that own a hotkey, and each one's slot in `Config` and cooldown. The
hotkeys are therefore walked, not listed by hand: `MinobotApp` walks them to bind, and the overlay will
walk them to show. A feature added to the enum and forgotten in the switch that gives it its action is
a compile error, which is the point.

**The configuration is live.** `Config` is still the immutable record it always was; `Settings` holds
the current one and swaps a whole new one in, so a reader never sees one field of the old and one of
the new. A feature that reads it *at every use* — `WindowManager.rank()`, the multi-click's exclusions
— is current for free and must stay that way: **do not hoist a config value into a field at
construction**, that is precisely what makes a setting un-editable. A feature that must *react* to a
change (`MinobotApp` re-registering the hotkeys) listens through `Settings.onChange`.

The configuration has **two tiers on disk**. `config.json` is the player's **read-only defaults** —
generated once, edited by hand, never rewritten by the application. `overlay.json` (see `OverlayState`)
holds the two overlay edits that must survive a restart — the `characters` (their order, and the class
and sex pinned to each) and the seven
hotkeys — and it overrides `config.json` at load, key by key, so a default the player never touched
still shows through. It holds *only* that subset: everything else the overlay changes — the
`overlay_scale`, the two switches — reaches no disk and a restart forgets it, the switches deliberately
(an `auto_pass_turn` found already on would end turns nobody asked it to).

`Settings` itself still writes **nothing**: persistence is one more `onChange` listener wired in
`MinobotApp`, exactly like the hotkey rebinding, and it serializes only the persisted subset. Note the
consequence: once a keybind is rebound on the overlay it lives in `overlay.json`, so editing it by hand
in `config.json` no longer has any effect — the persisted choice wins. To go back to the defaults,
delete `overlay.json`.

### Concurrency

- `KeyboardMonitor` polls on a dedicated **platform** thread (a 50 Hz loop of native calls — a virtual
  thread would buy nothing and pinning its carrier would be a trap). Its hotkey table is **replaced,
  never mutated**: a rebind assembles the next one aside and swaps the reference, because the loop is
  reading it at the very moment the player edits a key. And a rebind seeds each key's state from the
  keyboard as it *is* — a key still held is not a fresh press, or naming a hotkey would fire it.
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

A clicked background window flashes orange in the taskbar — and **it is the game that lights it up,
not us**: it asks for the foreground when it drains the click, Windows refuses (the same rule the ALT
trick works around), and a flashing taskbar button is the consolation prize Windows hands a refused
application.

That button does **two** things, and `core.FlashSuppressor` needs one lever for each. Both were
measured against the real game; neither is guesswork, and neither alone is enough.

*It blinks*, and that blinking **cannot be cut short**: `FlashWindowEx(FLASHW_STOP)` is ignored while
the shell is playing the animation, however fast it is swept — which is why cancelling at the moment of
the click cancels nothing. All that can be done is to make the animation short. Windows exposes how
many blinks it plays on a refusal (`SPI_*FOREGROUNDFLASHCOUNT`, seven by default) and the quietest it
goes is **one**. **Zero is not silence** — like `uCount` in `FlashWindowEx`, zero means "blink until
the window is activated", and setting it makes things *worse*. It is a setting of the whole Windows
session, borrowed by `suppress()` at startup, so **every exit path must go through `restore()`**
(`SystemParametersInfo` without `SPIF_UPDATEINIFILE`: the change dies with the session, and a killed
Minobot leaves nothing a logout does not undo).

*Then it stays orange* — a separate state, the one that actually annoys. `FlashWindowEx(FLASHW_STOP)`
**does** clear it, but only once the blinking is over, so `watch()` sweeps the clicked windows for three
seconds afterwards on a virtual thread — never on the click's own, which must stay as fast as it is.
`shift+x1` remains the last resort.

**A clicked character goes deaf, and `shift+x1` is the only cure.** The game raises its toast only for
a character nobody is watching; a posted click is, seen from inside the game, a click like any other,
so it concludes the player is there and stops notifying. The notification auto-focus lives on those
toasts and dies with them. This is a limit of the game client, not a bug to be fixed here — and it was
chased to the end before being written down: a single posted click deafens the window it hits and no
other; no toast is raised at all (the Windows notification database stays *empty*, so nothing is being
missed); and the game cannot be talked out of it. `WM_ACTIVATE`, `WM_NCACTIVATE`, `WM_KILLFOCUS`,
`WM_ACTIVATEAPP`, a full activation/deactivation cycle, a real `SetFocus` through `AttachThreadInput` —
all measured against the real game, all ignored. Only an activation that genuinely brings the window up
on screen re-arms it, which is exactly what `shift+x1` does. **Do not post messages at the game hoping
to undo this: that ground is burnt.**

### Group invitation — `F8`

A relay: the first character invites the second, who accepts and invites the third, and so on. Each
step waits for the game's own **Windows toast** to confirm the invitation landed, rather than guessing
a delay. No toast within five seconds and the relay **stops there**: the ENTER that accepts an
invitation, pressed in a window that has none on screen, opens the chat instead — and the next
`/invite` is then typed into the game rather than into the chat. The relay owns the foreground from
end to end (`focus.takeOver()`); the same toasts feed the auto-focus, which must not answer them here.

### Window cycler — `x2` / `shift+x2`

Cycles the focus through the windows in the player's character order (`Config.characterOrder()`). Only the windows of the
**current monitor** take part. Far better than Alt+Tab with several accounts.

### Window reorder — `F9`

Rebuilds the taskbar order: Windows offers no way to reorder its buttons, so the sequence hides every
game window and shows them again in the configured order. **The windows are hidden in the middle of
this** — every failure path must end by showing them again, or the player loses them.

### Overlay — `shift+space`

A control panel drawn over the game: the characters Minobot has found, dragged into the order the
cycler follows, and every feature's key, rebindable on the spot. `feature/OverlayController` decides;
`ui/SwingOverlay` draws, and is the only class that knows Swing exists.

It **belongs to a character**: it covers their game and pressing `shift+space` anywhere else — a browser, the
desktop — does nothing at all. Outside the game there is no character for it to belong to, and a panel
that appeared over a window nobody was looking at is a panel nobody asked for. Once up it *stays* up,
whatever takes the foreground next — it is always-on-top, and always-on-top owes nothing to the focus.
`shift+space` toggles it, and a **close cross** in the card's top-right corner does the same for the mouse — both
land on `OverlayView.hide()`, the one path down, so the follow thread stops and `shift+space` reopens it clean. A
character who leaves also takes their panel with them: minimized or closed, and it goes.

It covers the **client area**, which is the game and nothing else: `WindowApi.clientArea` is
`GetClientRect` followed by `ClientToScreen`, and the client origin sits *below* the title bar — which
is what keeps the panel off the minimize, maximize and close buttons. And it **follows**: a virtual
thread re-reads that area every 30 ms while the panel is up, so dragging or resizing the window takes
the panel with it. There is nothing to react to instead — Windows will not say a window moved without a
message hook, and a hook means a message pump and a native callback for what one poll answers.

**While it is up, it takes the mouse over the whole game**: it is a window, and it covers the client
area. That is the price of covering the game rather than floating in a corner of it, and it is why
`shift+space` is a switch and not a mode you play in.

**The card sits in the middle of the game**, where the player is already looking — the window covers the
whole client area, but the controls are one card centred on it, over a dimmed game. On the card: the
application's tile at the top (it draws `logo.png` from the classpath, centred and scaled to fit; the
`MINOBOT` wordmark stands in when no such file was shipped, so the space is never a blank), then the
characters and their drag-to-reorder, then the **Auto-pass turns** and **Auto-accept trades** switches,
then the size slider. Those switches are the panel's *states* rather than keys — explicit ON/OFF pills,
said in a word and a colour both, because a control that quietly ends every combat turn or accepts a
trade has to be unmistakable. **The logo is a resource, not a loose file:**
it lives in `src/main/resources/` so it rides inside the jar — a PNG in `assets/` (where the README's
screenshots live) is lost the moment `Minobot.exe` is unpacked elsewhere. The seven keybinds are a
**drawer** that unfolds to the right of the card — a `Keybinds ›` button opens it — because they are
edited once and forgotten, and left inline they made the panel twice as tall as what it is for. The
`Sheet` is a hand-written layout, not a manager: one card centred, one card beside it. The card gives up
the exact middle for one reason only — a drawer that would open past the right edge of the game — and
then it *slides* left by what the drawer is short of, never *shrinks*: a panel that resizes when a button
is clicked reads as a bug.

**Every size in `SwingOverlay` is a natural size, not a pixel**, and it reaches the screen multiplied by
`overlay_scale` — `px()` for a length, `font()` for a typeface. Swing's own defaults were drawn for a
96-DPI desktop, which on the screen the game is actually played on is a panel nobody can read; the
default is therefore **150%**, and the slider on the card moves it. A size that skips `px()` is a size
that will be wrong on somebody's monitor.

The slider lands **when the player lets go**, not while they drag: the scale rebuilds the card, and the
card carries the slider. And the panel **never grows past the game it covers** — the window is the
game's and is not scaled with it, so a small window scaled up far enough would push the slider off the
edge of the screen, which is to say off the one control that could bring it back. The list of characters
gives way first (it scrolls), and past that the drawn scale is capped to what the window holds — in
width and in height, the drawer counted in when it is open: the slider then reads what the player is
looking at, not what they asked for.

**It must never take the foreground**, or it would land between two keystrokes of the invitation relay.
`setFocusableWindowState(false)` is what buys that, and **it is enough** — measured against the real
game, both when the panel is shown and when it is clicked: the character keeps the screen throughout.
So **do not remove it, and do not add `WS_EX_NOACTIVATE` to "make sure"**: Swing already does this one.

It is also why **nothing in the panel is typed** — a keybind is read from the keyboard by
`KeyboardMonitor.captureNext()`, not typed into a text field, which would need the focus the panel does
not have. And why reordering is three mouse events rather than Swing's drag-and-drop stack, which
expects a focused window. And why the class picker (below) is an **in-overlay grid, not a `JPopupMenu`**:
a popup menu expects the focus too. Neither is a stylistic choice; all three fall out of the line above.

Each character row also carries its **class and sex** (`DofusClass`, the twelve of Dofus Retro, and
`Sex`, male or female). Until a class is chosen the row shows a muted `choose class…`; a click on it
opens a **modal picker** — a grid of the twelve, drawn over a scrim that dims the panel and catches every
click, so a mis-click closes the picker and touches nothing else. At the picker's head a **male/female
toggle** sets the sex: picking a sex records it at once and leaves the picker open, so the class tiles
redraw in that sex and the class the player then picks is theirs in it. Picking a class pins it and
closes. Class and sex are two independent choices (`assignClass` / `assignSex`), both the player's, and
both live on the **`Character`** they belong to — the one entity carrying a character's name, class and
sex (`core/domain/Character`), so the config holds one `List<Character>` rather than an order beside a
map of classes beside a map of sexes. **A new thing a character owns is a field on `Character`, not
another map keyed by name.** The list is keyed by name where it must merge (a reorder, a fresh pin — a
`GameWindow` is a window, and a window is not a choice), so a reorder does not disturb the pins, and a
character saved but not launched right now keeps its `Character` in the config though the panel — which
speaks only of what is in front of the player — does not show it. A character with no sex set is drawn
as male (`Character.sexOrDefault()`).

**Each class's logo is a resource, not a loose file:** one **SVG per class and sex** under
`src/main/resources/classes/` (`iop_m.svg`, `iop_f.svg`, `cra_m.svg`, …, the leaf being the class's own
name in lower case and the sex's one-letter suffix), so it rides inside the jar. They are drawn by
**jsvg** (`com.github.weisj:jsvg`), the one third-party rendering dependency: a vector fits the panel,
which scales every size through `px()`, so the icon stays crisp at 200% where a PNG would blur — the
icon is rendered afresh at each size, never off a cached bitmap. jsvg needs only `java.desktop` and
`java.logging`, both already in `dist.modules`, so the packaged runtime is unchanged. A class/sex with no
SVG shipped falls back to a lettered badge, exactly as the app logo falls back to its wordmark. The
picker is the seed of class-aware actions later; for now it only records the choice.

What it changes splits in two (see `Settings` and `OverlayState`): the **character order, the keybinds
and each character's class and sex are persisted** to `overlay.json` and survive a restart; the **scale
and the two switches are session-only** and a restart forgets them.

### Auto-pass turns — no hotkey, the overlay's switch

Plays a table of alts on its own. In a fight the game raises a toast for whoever's turn it is —
`C'est à "Bravo" de jouer` — and when the switch is on, `TurnPasser` turns that toast into a keypress:
it brings the character up and presses the game's end-turn key (`F1`, a constant — the key is the
game's, not the player's). A background character's turn ends without the player lifting a finger.

It passes **every** character's turn, the foreground one included: the switch is the player saying they
have stepped away, not that they are playing one of the seats — so there is no "except the one I am on".
That is why it is **off by default**, and why the switch is drawn to be unmistakable.

Like the invitation relay it focuses a window and then types into it, so it holds the foreground for the
length of that (`focus.takeOver()`): the notification auto-focus answers the same toast, and without the
takeover its focus would land between the focus and the keypress and end a turn in the wrong window. A
`ReentrantLock` serializes one pass against the next for the same reason. It is a **combat** feature and
the group invitation is done **before** the fight — the two are not meant to share the screen, and do
not. The switch lives on the overlay and only there, read live at every toast; unlike the order and the
keybinds it is **session-only** — not persisted, so a restart forgets it and finds it off, which is the
safe default for a feature that ends turns on its own.

### Auto-accept trades — no hotkey, the overlay's switch

Accepts a trade one of the player's own characters asks of another. When A opens an exchange with B,
it is **B** that the game raises a toast for — `Alpha te propose de faire un échange` — and that toast
**names the asker** (in the message, no quotes). That name is the whole discriminator: the trade is
*internal* when the message carries the name of one of the player's own windows (the receiver's own name
excepted, since it is one of ours too), and it accepts it for them; a stranger's is left exactly as it
was.

The accept presses the game's accept key (`ENTER`, a constant). Sending a keystroke needs the window to
hold the keyboard, so — for now — the accept is a **blink**: `takeOver()`, focus B, press, then focus
straight back to where the player was (read before the blink). `accept()` is the one method to change if
a truly background posted keystroke is ever shown to work against the game.

**This is where it "thinks differently" from the auto-focus.** The notification auto-focus answers
*every* game toast, so on its own it would jump to B and undo the point of accepting in place. So
`NotificationListener` now takes a predicate — *is this toast answered silently elsewhere?* — and stands
aside when it is. `ExchangeAccepter.claims()` is that predicate, a **pure function** of the toast, the
switch and the windows, so both features decide the same without a handshake and without a race. A
**stranger's** trade is not claimed: the auto-focus takes the player to the receiving window and does
nothing else, which is exactly what a real trade offer deserves. The asker's name is matched as a
**whole word** (`SuperAlpha` is not our `Alpha`). **On by default** — passing items between one's own
accounts is the daily bread of multi-boxing, and it only ever fires on a trade a player's own character
asked for — and session-only like the other switch (not persisted to `overlay.json`).

### Whisper toast — no hotkey

Turns a private message into a quiet card instead of a jump. A whisper — a game toast whose message reads
`de <sender> : <line>` — would otherwise pull the whole screen to the whispered character through the
notification auto-focus, which is too much for one line of chat. So `feature/WhisperToaster` stands in
front of that toast: it recognises the whisper by the **shape of its message** (a constant pattern kept
by the feature, the French wording being the game's), and rather than move the player it shows a small
card at the **left edge of the game**, centred top to bottom — who was whispered (`GameWindow.nameIn`
the title), by whom, and what they said. The card lives five seconds (`TOAST_LIFETIME`), a **close cross**
takes it down early, and a **click** on it focuses the receiver so the player can answer (on a virtual
thread — `FocusManager` sleeps). Several whispers **stack**, newest at the bottom.

It is the mirror of `ExchangeAccepter` and `TurnPasser`: a feature that registers with
`NotificationManager`, speaks of characters, and exposes a **pure `claims(Notification)`** the auto-focus
consults. Its one novelty is a **UI surface**, cut on the overlay's interface/impl seam so all the
deciding stays testable with no screen: `ui/ToastContent` (the immutable snapshot), `ui/ToastActions`
(`open`/`dismiss`), `ui/ToastView` (the narrow port), and `ui/SwingToastStack` (the only whisper class
that knows Swing — the **same anti-focus discipline** as the overlay: `JWindow`, always-on-top,
`setFocusableWindowState(false)`, nothing typed, clicks routed by hand; **do not add `WS_EX_NOACTIVATE`**).
Unlike the panel the window is sized to the **stack alone**, so it blocks only a narrow band, not the
whole game. Like the panel it **follows**: one virtual thread re-reads the foreground game window every
30 ms and keeps the stack pinned to it, or takes it away out of the game — and the same poll is where a
card **dies of old age**, so there is no timer thread per card. The stack is drawn at `overlay_scale`,
the panel's, so it reads at the same size on the same monitor. It is **always active**: a whisper always
toasts instead of focusing, so there is no `Config` field and no switch — it is a direct replacement, and
adds nothing to the player's settings.

### Notification auto-focus — no hotkey

The game raises a toast when a background character is attacked, messaged or invited; that toast pulls
the focus to them. It is a *smart* focus: if the player is typing at that moment, their keystrokes are
not stolen.

It **stands aside**, though, for a toast another feature answers in place — see *Auto-accept trades* and
*Whisper toast*: a `Predicate<Notification>` it is built with tells it which toasts to leave alone, so it
never jumps to a character whose exchange is being accepted silently, nor to one whose whisper is already
a card at the edge. The predicate is the **OR** of the trade-accepter's and the whisper toaster's
`claims`, combined in `MinobotApp`; every other toast, an attack or an invitation, it still takes.

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
