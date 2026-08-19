# Banner — the auto-pass banner, drawn

`SwingBanner` is the `BannerView` implementation — a sibling of `SwingOverlay` and `SwingToastStack`,
held to the same discipline. What the banner *means* — that it is up exactly while auto-pass is on, that
it follows the game, that its button stops the feature — lives in `feature/notification/`
(`AutoPassBanner`); this class only draws the one pill and routes its single click. See
`feature/notification/CLAUDE.md` (the auto-pass section) for the behaviour.

It shares the design system with the overlay: **`Scale`** for every size, **`Fonts`** for its typefaces,
**`Draw`** for the smoothing and the status dot, and **`Theme`** for the palette — so the three surfaces
read at the same size on the same monitor and their shapes cannot drift apart. It keeps its own literals
(`PAD_X`, `PAD_Y`, `MARGIN`, `TOP_PADDING`, `DOT`) next to its own code; only the shared rhythm
(`Metrics.GAP`, the type scale) comes from `components/`.

## It is a pill, and the whisper cards are rectangles

The two surfaces are on screen at the same time, over the same game, and they mean opposite things. A
whisper is an **event** — it arrived, it is read, it goes — so it is a card at the left edge that stacks
and expires. The banner is a **state** — it is true for as long as it is drawn — so it is one fully
rounded pill at the top centre, in the ember, with a live dot and its own way out. **A player must never
have to read either one to know which it is**, which is why the shape, the place and the colour all say
it three times over. Do not make them look alike.

The discipline is the overlay's, and not optional:

- **It must never take the foreground** — `setFocusableWindowState(false)`, and **nothing is typed**: the
  banner is read, or turned off with the mouse. It announces the very feature — the turn-pass — whose
  keystrokes a stolen focus would land between. Do not add `WS_EX_NOACTIVATE` "to make sure": Swing
  already does this one.
- Unlike the panel, the window is **not** the whole game — it is sized to the one pill and pinned to the
  game's **top edge, centred left to right** with a little padding below the top, so it blocks only a
  narrow band.
- Painting and hit-testing read the **same** button rectangle, so a click can never land on a button the
  paint did not draw there.
- The follow loop calls `show` / `moveTo` / `hide` from a virtual thread; each hands its work to the EDT.
