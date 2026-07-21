# Components — the design system

The reusable, theme-aware Swing pieces every surface is built from, so a button, a card or a chip is
defined **once** and drawn the same everywhere. Both the overlay (`ui/overlay/`) and the whisper stack
(`ui/toast/`) draw from here.

**One rule: a component knows Swing, `Theme` and `Scale` — and nothing else.** It has no notion of a
character, a feature, a hotkey or the config. It is handed a `Scale`, some `Theme` colours and a
callback, and it draws. A piece that needs to know what a class is, or what a keybind means, belongs in
`overlay/`, not here — that is the line that keeps this package reusable.

## The two primitives everything leans on

- **`Scale`** — the one place a natural size becomes a pixel. Every length goes through `px()`, every
  typeface through `font()`. It is an immutable value handed down at build time; a new scale is a new
  `Scale`. **A size that skips it will be wrong on somebody's monitor** (see `ui/CLAUDE.md`).
- **`Draw`** — the shared drawing primitives: `smooth(Graphics)` for an antialiased canvas, and
  `cross(...)` for the X drawn in three places (the panel's close cross, a disconnected row's forget
  cross, a toast's close). One routine, so the three cannot drift a pixel apart.

**`Metrics`** holds the shared tokens — the spacing rhythm (`GAP`, `PADDING`, `RADIUS`, `ROW`) and the
type scale (`BODY`, `SMALL`, `HEADING`, …), all *natural* sizes before `Scale`. A surface with a size of
its own (a card's width, the toast's tighter radius) keeps that literal next to its own code; only the
shared rhythm lives in `Metrics`.

## The pieces

`Card` (a rounded sheet + column helpers), `Toggle` (the ON/OFF switch), `Chip` (a status
dot + label, painted onto a canvas), `CloseCross`, `Caption` / `Hint` / `SectionHeader`,
`FlatSlider` and `SmartTableScrollBar` (the desktop's controls re-skinned dark). Each is theme-aware and
**not focusable** — the surfaces they live on never take the foreground, so a focus ring would be a lie
the panel cannot honour.

The buttons are **named for their emphasis, not their colour** — the same split the labels get. The
abstract `FlatButton` holds the shape (a rounded fill that lights on hover) once; `PrimaryButton` (deep
blue, light text — the chosen, the bound, the *on*) and `SecondaryButton` (soft blue, dark text — the
quiet everyday click) each pin one pair of `Theme` colours. A caller writes `new PrimaryButton(scale,
text)`, never a `Color`: one look per name, the same fill wherever it sits. To add a third emphasis, add
a `FlatButton` subclass — do not reopen a colour parameter.
