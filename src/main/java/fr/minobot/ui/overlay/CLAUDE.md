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
  classes. It is pinned to its width so a long character name never moves the console beside it. It has
  no button of its own: the list keeps itself true, and the two things a player does to it (drag a row,
  open the picker) are both done to the rows.
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

**What the rebuild throws away, `draw()` carries across by hand.** So far that is one thing: **how far the
character list is scrolled**. A new table comes back with a new scroll bar at zero, and a player who drags
two rows into place at the foot of a long list would be answered by being thrown to the top of it — which
reads as the panel undoing what they just did. `draw()` reads `team.scrollOffset()` before laying out and
hands it back after, **inside an `invokeLater`**: the restore must sit behind the layout that `revalidate()`
queued, because a scroll bar whose extent is not settled yet clamps every value to zero.

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

## One thing must leave the EDT

Swing lives on the event dispatch thread, and `SwingOverlay`'s public methods hand their work to it. But
`KeybindsDrawer.capture()` waits *seconds* for a human to press a key, and a wait like that on the EDT
freezes the very panel the player is looking at — so it runs on a virtual thread (`overlay-capture`) and
hands its result back with `invokeLater`.

**Nothing here asks the desktop to be re-read.** The card used to carry a `Reload` button, which was the
other thing that had to leave the EDT; the panel now re-reads the desktop when it opens and every couple
of seconds while it is up — see `feature/overlay/CLAUDE.md` — from a thread that was never the EDT to
begin with.

## The `Sheet` is a hand-written layout

One sheet centred on a dimmed game, the drawer beside it, the picker scrim over everything. The sheet
*slides* left to make room for an open drawer, it never *shrinks* (a panel that resizes when a button is
clicked reads as a bug); and the whole panel is capped to the scale the game window can actually hold —
the character list gives way first, because it scrolls. That logic stays in the orchestrator, because it
reasons about the sheet and the drawer together.

## The rows

### Where a click on a row lands

`CharacterList.onRowClick` reads a click off the cell it landed in, and there is an order to it. The
**status** cell is settled first and ends the routing whatever it decides — it is the only cell that can
*remove* the row under the pointer, and a second click there must not be taken for a double-click on
whichever row moved up into its place. Everywhere else a click asks for the class picker: on the **class**
cell, or **anywhere on the row twice**. The double-click is the way *back* — once a class is pinned, its
cell is a quiet grey word with no invitation left to aim at, and a player who picked the wrong one needs a
target that does not depend on the row still looking unconfigured. Opening the picker changes nothing on
its own, which is what makes the single click that precedes a double one harmless.

### A row's state is its stripe

`CharacterRow` says *on screen* or *not* before a word of it is read: the ember down the left edge, and
the fill, the name, the portrait and the index all stepping back a shade with it. The green dot only
confirms what the stripe already said — which is why the row has a **dot and not a word**: eight rows each
labelled *connected* is eight labels nobody reads, where eight dots are one column the eye runs down. The
forget cross takes the dot's place on a disconnected row, so the two can never both be offered: a
connected character would come straight back from the list they were dropped from.
