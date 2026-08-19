# The overlay

The control panel drawn over the game, and its `OverlayController`. It **speaks of characters**
(`GameWindow`) and keeps whatever Windows demands in one named leaf at the bottom — see the root
`CLAUDE.md`. Above all it **must never take the foreground** (see below): `core.FocusManager` is what
keeps the features from fighting over the one screen, and a panel that stole the focus would land in the
middle of the invitation relay's keystrokes.

## Overlay — `shift+space`

A control panel drawn over the game: the characters Minobot has found, dragged into the order the
cycler follows, and every feature's key, rebindable on the spot. `OverlayController` decides;
`ui/overlay/` draws — `SwingOverlay` and the sections it lays out, the Swing side of the panel (see
`ui/CLAUDE.md` and `ui/overlay/CLAUDE.md` for the Swing-only discipline that binds it).

It **belongs to a character**: it covers their game and pressing `shift+space` anywhere else — a browser, the
desktop — does nothing at all. Outside the game there is no character for it to belong to, and a panel
that appeared over a window nobody was looking at is a panel nobody asked for. Once up it *stays* up,
whatever takes the foreground next — it is always-on-top, and always-on-top owes nothing to the focus.
`shift+space` toggles it, and a **close cross** in the card's top-right corner does the same for the mouse — both
land on `OverlayView.hide()`, the one path down, so the follow thread stops and `shift+space` reopens it clean. A
character who leaves also takes their panel with them: minimized or closed, and it goes.

**It never has to be told to look again, and that is why it has no `Reload` button.** The desktop is
re-enumerated when the panel opens — the list the player is about to read is the desktop as it *is*, not
as the thirty-second sweep last left it — and again every two seconds while it is up, on the follow thread
below. The two rhythms are two different questions: where a window sits is one native call, the roster is
the whole desktop enumerated and every title read. **The poll redraws only when what came back differs
from what is on the screen** (`OverlayContent` compared whole, so a new activity line counts too). That
comparison is not an optimization: the panel is *rebuilt* at every draw and never patched, so a poll that
redrew unconditionally would drop the row the player has under the pointer twice a second.

It covers the **client area**, which is the game and nothing else: `WindowApi.clientArea` is
`GetClientRect` followed by `ClientToScreen`, and the client origin sits *below* the title bar — which
is what keeps the panel off the minimize, maximize and close buttons. And it **follows**: a virtual
thread re-reads that area every 30 ms while the panel is up, so dragging or resizing the window takes
the panel with it. There is nothing to react to instead — Windows will not say a window moved without a
message hook, and a hook means a message pump and a native callback for what one poll answers.

**While it is up, it takes the mouse over the whole game**: it is a window, and it covers the client
area. That is the price of covering the game rather than floating in a corner of it, and it is why
`shift+space` is a switch and not a mode you play in.

**The sheet sits in the middle of the game**, where the player is already looking — the window covers the
whole client area, but the controls are one sheet centred on it, over a dimmed game. It is **two halves
and one line**:

- a **header line** — `logo.png` from the classpath at a line's height, the `MINOBOT` wordmark (which
  stands alone when no such file was shipped, so the space is never a blank), and the panel's own
  `shift+space` drawn as key chips, because a player who found the panel with the mouse learns in the same
  glance how to open it without one. **The logo is a resource, not a loose file:** it lives in
  `src/main/resources/` so it rides inside the jar — a PNG in `assets/` (where the README's screenshots
  live) is lost the moment `Minobot.exe` is unpacked elsewhere.
- the **team**, left, at a fixed width: the characters and their drag-to-reorder. Fixed so a long
  character name never moves the half beside it.
- the **console**, right: the **Auto-pass turns** and **Auto-accept trades** switches, and — under one
  rule — **what those switches have been doing**. Each switch is a pill carrying its own name *and* its
  state, said in a word and a colour both, because a control that quietly ends every combat turn or
  accepts a trade has to be unmistakable. Auto-pass *also* has a key (its `shift+middle` toggle lives in
  the drawer), so the pill and the keybind are two views of the one switch; auto-accept is switch-only.
- the **size slider**, at the foot, narrow and quiet — the only way back from a panel drawn too large or
  too small to work with.

The eight keybinds are a **drawer** that unfolds to the right of the sheet — a `Keybinds ›` button at the
end of the switch row opens it, its own close cross folds it back — because they are edited once and
forgotten, and left inline they made the panel half as useful again as tall. The `Sheet` is a hand-written
layout, not a manager: one sheet centred, one card beside it. The sheet gives up the exact middle for one
reason only — a drawer that would open past the right edge of the game — and then it *slides* left by what
the drawer is short of, never *shrinks*: a panel that resizes when a button is clicked reads as a bug.

## The console: what Minobot did while the player was elsewhere

Every feature acts on a window the player is not looking at. Without a record, the panel would show what
Minobot is *set to* and never what it *did* — so `OverlayContent` carries two lists, both newest first,
straight off `core.ActivityLog` and `core.WhisperLog` (read that `CLAUDE.md` for why they are small and
forgetful):

- **ACTIVITY** — one line per act: when, what, and about whom. Read-only, deliberately: everything on it
  already happened, and a panel that let a player un-pass a turn would be lying about what it can do.
- **WHISPERS** — the private messages, after the ten-second cards that carried them are long gone. Each
  names a character, so a click on one is the same jump the card offered: `OverlayActions.openWhisper`
  takes the panel **down** first — the player asked to go and answer, and the panel covers the whole
  client area of the very window they are being sent to — then focuses the receiver on a virtual thread
  (`FocusManager` sleeps through its ALT dance). `clearWhispers` empties the list; nothing on disk
  remembers them either way.

`content()` reads both logs fresh at every draw, exactly as it reads the live configuration.

**Every size the panel draws is a natural size, not a pixel**, and it reaches the screen multiplied by
`overlay_scale` — through a `ui/components/Scale`: `px()` for a length, `font()` for a typeface. Swing's own defaults were drawn for a
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
`Sex`, male or female). Until a class is chosen the row shows an ember **`Pick a class`** beside a dashed
frame where the icon will go — the only ember on an otherwise finished row, which is what makes an
unconfigured character findable in a list of eight, and the reason the frame is kept rather than left
blank: the rows do not gain a ragged column the day one character is configured and another is not. A
click on it opens a **modal picker** — and so does a **double-click anywhere on the row**, which is the
way back: once a class is pinned that cell is a quiet grey word, and a player who picked the wrong class
would otherwise have no invitation left to aim at. (The status cell is the exception, because it can
delete the row under the pointer — see `ui/overlay/CLAUDE.md` for the routing.) The picker is the twelve
**six to a row**, so they make two lines and the picker
stays wider than it is tall, drawn over a scrim that dims the panel and catches every click, so a
mis-click closes the picker and touches nothing else. At the picker's head a **Male/Female segmented
control** sets the sex: picking a sex records it at once and leaves the picker open, so the class tiles
redraw in that sex and the class the player then picks is theirs in it. Picking a class pins it and
closes. Class and sex are two independent choices (`assignClass` / `assignSex`), both the player's, and
both live on the **`Character`** they belong to — the one entity carrying a character's name, class and
sex (`core/domain/Character`), so the config holds one `List<Character>` rather than an order beside a
map of classes beside a map of sexes. **A new thing a character owns is a field on `Character`, not
another map keyed by name.** The list is keyed by name where it must merge (a reorder, a fresh pin — a
`GameWindow` is a window, and a window is not a choice), so a reorder does not disturb the pins. A
character with no sex set is drawn as male (`Character.sexOrDefault()`).

**A pinned character does not vanish when its window closes.** The panel shows the windows on screen
*and* the characters the player has pinned a class or a sex to (`Character.isPinned()`) but is not
playing right now — the latter dimmed, keeping their place in the cycle order. Whether a row is on screen
is drawn as its **ember left stripe** and confirmed by a green **dot** (`ui/CharacterEntry`, a `Character`
beside a `connected` flag — connection is window state, not a field the domain persists); the row's fill,
name, portrait and index all step back a shade with the stripe. A dot and not a word, because eight rows
each labelled *connected* is eight labels nobody reads. A disconnected row carries a **forget cross** in
the dot's place (`OverlayActions.forget` → `Config.withoutCharacter`) that drops the character from the
roster, its class and sex with it — the one way a saved character leaves the list, and offered exactly
where it can do no harm, since a connected character would come straight back from the list they were
dropped from. An **unpinned** character is a bare name (a reorder artifact)
and still appears only while its window is open: a login placeholder and a character nobody configured
earn no greyed-out row. The controller builds this list in `content()`; the merge and the two rules —
what is kept, what its position is — are covered by `OverlayControllerTest`.

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
and the two switches are session-only** and a restart forgets them. The two switches drive features in
`notification/` (auto-pass, auto-accept); the controller only calls `settings.update` and never knows
which of the two happens.
