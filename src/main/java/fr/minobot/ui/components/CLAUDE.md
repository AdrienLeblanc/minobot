# Components — the design system

The reusable, theme-aware Swing pieces every surface is built from, so a button, a card or a chip is
defined **once** and drawn the same everywhere. The overlay (`ui/overlay/`), the whisper stack
(`ui/toast/`) and the banner (`ui/banner/`) all draw from here.

**One rule: a component knows Swing, `Theme`, `Fonts` and `Scale` — and nothing else.** It has no notion
of a character, a feature, a hotkey or the config. It is handed a `Scale`, some `Theme` colours and a
callback, and it draws. A piece that needs to know what a class is, or what a keybind means, belongs in
`overlay/`, not here — that is the line that keeps this package reusable. `Segmented` is the shape of
that rule: it is handed labels and hands back an index, so the class picker can use it for a sex without
this package learning what a sex is.

## The primitives everything leans on (`ui/utils/`)

- **`Scale`** — the one place a natural size becomes a pixel. Every length goes through `px()`, every
  typeface through `font(face, size)`. It is an immutable value handed down at build time; a new scale is
  a new `Scale`. **A size that skips it will be wrong on somebody's monitor** (see `ui/CLAUDE.md`).
- **`Fonts`** — the six shipped faces, read once off the classpath. A caller names a **weight**, never a
  style bit; see `ui/CLAUDE.md` for why, and for which characters the faces actually have.
- **`Draw`** — the shared drawing primitives: `smooth(Graphics)` for an antialiased canvas, `cross(...)`
  for the X drawn in four places, `dot(...)` for a status, `grip(...)` for a drag handle (drawn rather
  than set as `⠿`, which no shipped face has), and `elide(...)` for every string that must stay inside a
  cell. One routine each, so the copies cannot drift a pixel apart.
- **`Metrics`** — the shared tokens: the spacing rhythm (`GAP`, `GUTTER`, `PADDING`, `ROW`), the type
  scale (`BODY`, `SMALL`, `HEADING`, …) and the tracking, all *natural* sizes before `Scale`. A surface
  with a size of its own (a card's width, the toast's tighter radius) keeps that literal next to its own
  code; only the shared rhythm lives here.

The **radii are a nesting order**, not a list of tastes: `RADIUS_SHEET` (the panel's own surface) →
`RADIUS` (a card on it) → `RADIUS_TILE` (a row, a button, a tile on that card) → `RADIUS_CHIP` (a key).
A tile drawn at the sheet's radius reads as a second panel rather than as something resting on the first.

## The pieces

`Card` (a rounded sheet, a column, and the `pinnedTo(width)` that holds the panel's halves in place),
`Divider`, `StatePill` (a switch carrying its own name and state), `Segmented`, `KeyChip` (a combination
as one tile per key), `CloseCross`, `Caption` / `Hint` / `SectionHeader`, `FlatSlider` and
`SmartTableScrollBar` (the desktop's controls re-skinned dark), `SmartTable` (the scrollable,
drag-to-reorder list shell). Each is theme-aware and **not focusable** — the surfaces they live on never
take the foreground, so a focus ring would be a lie the panel cannot honour.

**`Card.pinnedTo` holds a width, never a height.** The character list is resized under its card as the
game leaves the panel less room, so a card pinned to a height would ignore the one adjustment the panel
depends on to fit. It was a card pinned to a height of zero that made the first draw of the redesigned
panel come out invisible.

**A control whose size comes from its children is a panel with a mouse listener, not a `JButton`.** A
button's preferred size comes from its own text through its UI delegate and ignores what was laid out
inside it — which is why `StatePill` is a `JPanel`.

The buttons are **named for their emphasis, not their colour** — the same split the labels get. The
abstract `FlatButton` holds the shape (a rounded fill that lights on hover, with an optional hairline)
once; `PrimaryButton` (the ember — the chosen, the *stop this*) and `SecondaryButton` (a raised tile with
an edge — the quiet everyday click) each pin one pair of `Theme` colours. A caller writes
`new PrimaryButton(scale, text)`, never a `Color`: one look per name, the same fill wherever it sits. To
add a third emphasis, add a `FlatButton` subclass — do not reopen a colour parameter. And keep the ember
rationed: one primary on a card at a time, or the accent stops meaning anything.
