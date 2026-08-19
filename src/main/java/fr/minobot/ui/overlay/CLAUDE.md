# Overlay — the panel, drawn

`SwingOverlay` is the `OverlayView` implementation and the **orchestrator**: it owns the `JWindow`, the
threading, the `Sheet` layout and the panel's transient state, and it lays the sections out on the sheet.
Each *part* of the panel draws itself — `HeaderBar`, `characters/TeamCard`, `console/ConsoleCard`,
`keybinds/KeybindsDrawer`, `characters/clazz/ClassPicker`, `SizeSlider`, with `ClassIcons` the shared
icon asset. What the panel *means* — the characters, the pins, what is persisted — lives one layer up in
`feature/overlay/` (`OverlayController`); read that `CLAUDE.md` for the behaviour. This one is about how
the drawing is split.

## Two halves and one line

The panel says three things, and they are laid out in the order a player needs them:

- **`HeaderBar`** names the surface and shows the key that summons it. It is a *line*, not a banner: the
  logo used to have a 300-pixel band of its own, which announced the application's name to somebody who
  had already opened the application. The room went to what the panel is for.
- **`TeamCard`** (left, fixed width) is what the player **edits** — the roster, the cycle order, the
  classes. It is pinned to its width so a long character name never moves the console beside it.
- **`ConsoleCard`** (right) is what Minobot has been **doing** — the two switches above a rule, and below
  it the activity and the whispers. The switches and the record share one card because they are the same
  question asked twice: *is auto-pass on?* and *did it just pass a turn?*

The **`SizeSlider`** sits at the foot of the sheet, narrow and quiet. It is not in the design that this
panel came from, and it is kept anyway: it is the only way back from a panel drawn too large or too small
to work with, and `overlay_scale` reaches no other control.

Every card's width comes from `Card.pinnedTo`, and `SHEET_WIDTH` is **computed from its parts**
(`TeamCard.WIDTH + GUTTER + ConsoleCard.WIDTH + 2 * PADDING`) rather than written down: a literal there
would be a number to keep in step with four others, and it would lose.

## Everything is rebuilt, nothing is patched

`draw()` → `lay()` builds the whole sheet again at the scale of the moment: every size on it was computed
with that scale, so walking the tree to correct it would be the walk that builds it. A section is a
**stateless builder** — it is handed a fresh `Scale` (and the `OverlayContent`) at each `lay()` and
returns a fresh component. So a section can safely capture its `Scale` at build time; the scale never
changes under a component that is already on screen.

## The orchestrator owns the transient state; sections call back

Two pieces of state are the panel's, not the player's — there is nothing on disk for them:

- **`keybindsOpen`** — the drawer's fold. The `Keybinds ›` button (at the end of the console's switch row)
  flips it, and the drawer's own close cross flips it back through the same callback.
- **`classPickerFor`** — which character the modal picker is open for. `CharacterList` reports a click on
  a class cell through its `openPicker` callback; `ClassPicker` reports a dismiss or a chosen class back
  through `SwingOverlay`, which clears the state. Picking a **sex** leaves the picker open (it just calls
  `actions.assignSex`), so the tiles redraw in that sex.

Sections never hold this state and never redraw themselves for it — they hand the decision back to the
orchestrator, which is the only place the two flags live.

## Two things must leave the EDT

Swing lives on the event dispatch thread, and `SwingOverlay`'s public methods hand their work to it. But
two waits must **not** run on it, or they freeze the very panel the player is looking at:

- `KeybindsDrawer.capture()` — waits seconds for a human to press a key (`overlay-capture`).
- `TeamCard`'s reload button — enumerates every window and reads each title (`overlay-reload`).

Both run on a virtual thread and hand their result back to the EDT with `invokeLater`.

## The `Sheet` is a hand-written layout

One sheet centred on a dimmed game, the drawer beside it, the picker scrim over everything. The sheet
*slides* left to make room for an open drawer, it never *shrinks* (a panel that resizes when a button is
clicked reads as a bug); and the whole panel is capped to the scale the game window can actually hold —
the character list gives way first, because it scrolls. That logic stays in the orchestrator, because it
reasons about the sheet and the drawer together.

## A row's state is its stripe

`CharacterRow` says *on screen* or *not* before a word of it is read: the ember down the left edge, and
the fill, the name, the portrait and the index all stepping back a shade with it. The green dot only
confirms what the stripe already said — which is why the row has a **dot and not a word**: eight rows each
labelled *connected* is eight labels nobody reads, where eight dots are one column the eye runs down. The
forget cross takes the dot's place on a disconnected row, so the two can never both be offered: a
connected character would come straight back from the list they were dropped from.
