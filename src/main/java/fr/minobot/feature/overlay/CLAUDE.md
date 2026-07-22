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
trade has to be unmistakable. Auto-pass now *also* has a key (its `shift+middle` toggle lives in the
drawer below), so the pill and the keybind are two views of the one switch; auto-accept is switch-only. **The logo is a resource, not a loose file:**
it lives in `src/main/resources/` so it rides inside the jar — a PNG in `assets/` (where the README's
screenshots live) is lost the moment `Minobot.exe` is unpacked elsewhere. The eight keybinds are a
**drawer** that unfolds to the right of the card — a `Keybinds ›` button opens it — because they are
edited once and forgotten, and left inline they made the panel twice as tall as what it is for. The
`Sheet` is a hand-written layout, not a manager: one card centred, one card beside it. The card gives up
the exact middle for one reason only — a drawer that would open past the right edge of the game — and
then it *slides* left by what the drawer is short of, never *shrinks*: a panel that resizes when a button
is clicked reads as a bug.

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
`Sex`, male or female). Until a class is chosen the row shows a muted `pick class…`; a click on it
opens a **modal picker** — a grid of the twelve, drawn over a scrim that dims the panel and catches every
click, so a mis-click closes the picker and touches nothing else. At the picker's head a **male/female
toggle** sets the sex: picking a sex records it at once and leaves the picker open, so the class tiles
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
playing right now — the latter greyed-out, keeping their place in the cycle order. Each row carries a
**status chip** (`ui/CharacterEntry`, a `Character` beside a `connected` flag — connection is window
state, not a field the domain persists): green *connected* while the window is open, light-grey
*disconnected* when it is not. A disconnected row also carries a **forget cross** (`OverlayActions.forget`
→ `Config.withoutCharacter`) that drops the character from the roster, its class and sex with it — the
one way a saved character leaves the list. An **unpinned** character is a bare name (a reorder artifact)
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
