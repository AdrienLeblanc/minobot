# Overlay — the panel, drawn

`SwingOverlay` is the `OverlayView` implementation and the **orchestrator**: it owns the `JWindow`, the
threading, the `Sheet` layout and the panel's transient state, and it lays the sections out on the card.
Each *part* of the panel draws itself — `LogoTile`, `CharacterList`, `KeybindsDrawer`, `ClassPicker`,
`Preferences`, `SizeSlider`, with `ClassIcons` the shared icon asset. What the panel *means* — the
characters, the pins, what is persisted — lives one layer up in `feature/overlay/` (`OverlayController`);
read that `CLAUDE.md` for the behaviour. This one is about how the drawing is split.

## Everything is rebuilt, nothing is patched

`draw()` → `lay()` builds the whole card again at the scale of the moment: every size on it was computed
with that scale, so walking the tree to correct it would be the walk that builds it. A section is a
**stateless builder** — it is handed a fresh `Scale` (and the `OverlayContent`) at each `lay()` and
returns a fresh component. So a section can safely capture its `Scale` at build time; the scale never
changes under a component that is already on screen.

## The orchestrator owns the transient state; sections call back

Two pieces of state are the panel's, not the player's — there is nothing on disk for them:

- **`keybindsOpen`** — the drawer's fold. The `Keybinds ›` button (in the *Characters* heading) flips it
  and redraws.
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
- `CharacterList`'s reload button — enumerates every window and reads each title (`overlay-reload`).

Both run on a virtual thread and hand their result back to the EDT with `invokeLater`.

## The `Sheet` is a hand-written layout

One card centred on a dimmed game, the drawer beside it, the close cross pinned to the card's corner, the
picker scrim over everything. The card *slides* left to make room for an open drawer, it never *shrinks*
(a panel that resizes when a button is clicked reads as a bug); and the whole panel is capped to the
scale the game window can actually hold — the character list gives way first, because it scrolls. That
logic stays in the orchestrator, because it reasons about the card and the drawer together.
