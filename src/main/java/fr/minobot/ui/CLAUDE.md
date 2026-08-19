# UI — the interface/impl seam for the screen

`ui/` itself is **interfaces and data only**: `OverlayView` / `OverlayContent` / `OverlayActions` /
`CharacterEntry` for the panel, `ToastView` / `ToastContent` / `ToastActions` for the whisper stack,
`BannerView` / `BannerContent` / `BannerActions` for the auto-pass banner, and
`Theme` — the one palette every Swing surface draws from. All the deciding stays here as plain data and
ports, testable with no screen. The Swing that draws these surfaces lives in four subpackages, each with
a `CLAUDE.md` that loads only when you work in it:

- **`components/`** — the design system: reusable, theme-aware Swing pieces (a card, a flat button, a
  state pill, a key chip, a segmented control, a close cross, a slider) plus `Scale`, `Fonts`, `Metrics`
  and the `Draw` primitives shared shapes come from. **No knowledge of the game or the config** here.
- **`overlay/`** — the control panel, `SwingOverlay` (the `OverlayView` implementation) and the sections
  it lays out (`HeaderBar`, `TeamCard`, `ConsoleCard`, `KeybindsDrawer`, `ClassPicker`, …).
- **`toast/`** — `SwingToastStack`, the `ToastView` implementation.
- **`banner/`** — `SwingBanner`, the `BannerView` implementation (the auto-pass banner).

## One accent, and it is rationed

`Theme` is charcoal and **one ember**, spent only on what is *live*: the character being cycled, a switch
that is on, a class nobody has picked yet, the button that stops a running feature. Two exceptions earn
their place by saying something no grey can — `CONNECTED` (a window open right now) and `WHISPER` (the
amber of somebody talking to you). **A third accent is not a colour, it is a competing claim on the
player's eye**; everything else is a rung of the grey ladder (`TEXT` → `GHOST`), and reaching for a
literal instead is how two rows that mean the same end up a shade apart.

## The typefaces are shipped, and a weight is a file

The panel is set in Barlow, Barlow Semi Condensed and JetBrains Mono, loaded once by `utils/Fonts` off
`resources/fonts/` (OFL, licences shipped beside them). Surfaces name a **weight** — `Fonts.MEDIUM`,
`Fonts.SEMIBOLD` — never a `Font.BOLD` style bit: Java2D emboldens a regular face by smearing it, and at
the size a hint or a key chip is drawn, that smear is what the letters read as. Hence
`Scale.font(face, size)`, which derives the size and nothing else. A face that cannot be read falls back
to the desktop's own, the way the logo falls back to its wordmark.

## `clip`, never `setClip`, inside a cell renderer

`Graphics2D.setClip(shape)` **replaces** the clip; `Graphics2D.clip(shape)` narrows it. A row is a rubber
stamp — it is painted with its scroll pane's clip already in force — so a `setClip` inside one throws that
clip away and lets the shape paint anywhere on the panel. That is how the half-row at the foot of a
scrolled character list came to draw its ember stripe past the bottom of the viewport and across the hint
below: the stripe was the only part of the row drawn under a clip of its own, and so the only part that
escaped. Narrow with `clip(...)`, restore with `setClip(saved)`.

**Only draw characters the shipped faces have.** Barlow's latin cut covers `U+2000`–`U+206F`, so `…`,
`“ ”` and `›` are safe and `→` (`U+2192`) is not — Swing draws the missing-glyph box for it, which is how
the whisper list came to say *sender ▯ receiver* before it was caught.

**The classes under `components/`, `overlay/`, `toast/` and `banner/` are the only ones that know Swing exists.**
They all share one discipline, and it is not optional:

- **They must never take the foreground**, or they land between two keystrokes of the invitation relay
  or a turn-pass. `setFocusableWindowState(false)` is what buys that, and **it is enough** — measured
  against the real game, shown and clicked. So **do not remove it, and do not add `WS_EX_NOACTIVATE` to
  "make sure"**: Swing already does this one.
- Because they never hold the focus, **nothing in them is typed** and every click is routed by hand
  (a keybind is read by `KeyboardMonitor.captureNext()`, reordering is three raw mouse events, the class
  picker is an in-overlay grid, not a `JPopupMenu`). None of these are stylistic — they all fall out of
  the line above.
- **Every size is a natural size, not a pixel**, and reaches the screen multiplied by `overlay_scale`
  through a `components.Scale`: `Scale.px()` for a length, `Scale.font()` for a typeface. A size that
  skips them is a size that will be wrong on somebody's monitor.

Keep new drawing code behind the `OverlayView` / `ToastView` interfaces. A feature that reaches past them
into Swing becomes untestable — the whole reason the seam exists. A surface that is deliberately *not*
this theme keeps its own colours rather than bending `Theme`; sharing a palette is for surfaces that
share a look, not a rule that every surface must look the same.
