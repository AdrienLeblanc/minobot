# UI — the interface/impl seam for the screen

`ui/` itself is **interfaces and data only**: `OverlayView` / `OverlayContent` / `OverlayActions` /
`CharacterEntry` for the panel, `ToastView` / `ToastContent` / `ToastActions` for the whisper stack,
`BannerView` / `BannerContent` / `BannerActions` for the auto-pass banner, and
`Theme` — the one palette every Swing surface draws from. All the deciding stays here as plain data and
ports, testable with no screen. The Swing that draws these surfaces lives in four subpackages, each with
a `CLAUDE.md` that loads only when you work in it:

- **`components/`** — the design system: reusable, theme-aware Swing pieces (a card, a flat button, a
  toggle, a chip, a close cross, a slider) plus the `Scale` every size passes through and the `Draw`
  primitives shared shapes are drawn by. **No knowledge of the game or the config** lives here.
- **`overlay/`** — the control panel, `SwingOverlay` (the `OverlayView` implementation) and the sections
  it lays out (`CharacterList`, `KeybindsDrawer`, `ClassPicker`, …).
- **`toast/`** — `SwingToastStack`, the `ToastView` implementation.
- **`banner/`** — `SwingBanner`, the `BannerView` implementation (the auto-pass banner).

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
