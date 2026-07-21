# UI — the interface/impl seam for the screen

`ui/` is **interfaces only** by default: `OverlayView` / `OverlayContent` / `OverlayActions` for the
panel, `ToastView` / `ToastContent` / `ToastActions` for the whisper stack. All the deciding stays here
as plain data and ports, testable with no screen. The full behaviour of each surface is specced with the
feature that owns it — see `feature/CLAUDE.md` (the **Overlay** and **Whisper toast** sections).

**`SwingOverlay` and `SwingToastStack` are the only two classes that know Swing exists.** They share one
discipline, and it is not optional:

- **They must never take the foreground**, or they land between two keystrokes of the invitation relay
  or a turn-pass. `setFocusableWindowState(false)` is what buys that, and **it is enough** — measured
  against the real game, shown and clicked. So **do not remove it, and do not add `WS_EX_NOACTIVATE` to
  "make sure"**: Swing already does this one.
- Because they never hold the focus, **nothing in them is typed** and every click is routed by hand
  (a keybind is read by `KeyboardMonitor.captureNext()`, reordering is three raw mouse events, the class
  picker is an in-overlay grid, not a `JPopupMenu`). None of these are stylistic — they all fall out of
  the line above.
- **Every size is a natural size, not a pixel**, reaching the screen multiplied by `overlay_scale`:
  `px()` for a length, `font()` for a typeface. A size that skips `px()` is a size that will be wrong on
  somebody's monitor.

Keep new drawing code behind these interfaces. A feature that reaches past `OverlayView`/`ToastView` into
Swing becomes untestable — the whole reason the seam exists.
