# Banner — the auto-pass banner, drawn

`SwingBanner` is the `BannerView` implementation — a sibling of `SwingOverlay` and `SwingToastStack`,
held to the same discipline. What the banner *means* — that it is up only while auto-pass is on, that it
follows the game, that its cross only hides it — lives in `feature/notification/` (`AutoPassBanner`); this
class only draws the one card and routes its single click. See `feature/notification/CLAUDE.md` (the
auto-pass section) for the behaviour.

It shares the design system with the overlay: **`Scale`** for every size, **`Draw`** for the smoothing
and the close cross (`Draw.cross`, the same routine the panel and the whisper cards use), and **`Theme`**
for the dark card — so the three surfaces read at the same size on the same monitor and their shapes
cannot drift apart. It keeps its own card literals (`HEIGHT`, `PADDING`, `RADIUS`, `TOP_PADDING`) next to
its own code; only the shared rhythm (`Metrics.GAP`) comes from `components/`.

The discipline is the overlay's, and not optional:

- **It must never take the foreground** — `setFocusableWindowState(false)`, and **nothing is typed**: the
  banner is read, or dismissed with the mouse. It announces the very feature — the turn-pass — whose
  keystrokes a stolen focus would land between. Do not add `WS_EX_NOACTIVATE` "to make sure": Swing
  already does this one.
- Unlike the panel, the window is **not** the whole game — it is sized to the one card and pinned to the
  game's **top edge, centred left to right** with a little padding below the top, so it blocks only a
  narrow band.
- Painting and hit-testing read the **same** close-cross rectangle, so a click can never land on a cross
  the paint did not draw there.
- The follow loop calls `show` / `moveTo` / `hide` from a virtual thread; each hands its work to the EDT.
