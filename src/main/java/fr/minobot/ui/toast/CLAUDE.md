# Toast — the whisper stack, drawn

`SwingToastStack` is the `ToastView` implementation — the twin of `SwingOverlay`, held to the same
discipline. What a whisper *is* and when it shows lives in `feature/notification/` (the whisper toaster);
this class only draws the stack and routes its clicks. See `feature/CLAUDE.md` (the whisper toast
section) for the behaviour.

It shares the design system with the overlay: **`Scale`** for every size, **`Fonts`** for its typefaces
and **`Draw`** for the smoothing, the close cross (`Draw.cross`, the same routine the panel's crosses use)
and the eliding, so the two surfaces read at the same size on the same monitor and their shapes cannot
drift apart. It keeps its own tighter card literals (`PADDING`, `RADIUS`, `MARGIN`, `STRIPE`) next to its
own code — it is deliberately a denser surface than the panel, so only the shared rhythm (`Metrics.GAP`,
the type scale) comes from `components/`.

**The amber is spent once, on the header.** `Theme.WHISPER` is what says *somebody is talking to you*
before a word of the card is read; the stripe down the left edge is therefore grey, not amber, because a
second use of the colour would stop the first one meaning anything. And the card is a **rectangle** where
the auto-pass banner is a pill — see `ui/banner/CLAUDE.md`: an event and a state must not look alike.

The discipline is the overlay's, and not optional:

- **It must never take the foreground** — `setFocusableWindowState(false)`, and **nothing is typed**: a
  whisper is read, gone to, or dismissed, all with the mouse. Do not add `WS_EX_NOACTIVATE` "to make
  sure": Swing already does this one.
- Unlike the panel, the window is **not** the whole game — it is sized to the stack alone and pinned to
  the game's left edge, centred top to bottom, so it blocks only a narrow band.
- Painting and hit-testing read the **same placement list** (`Placed`), so a click can never land on a
  card the paint did not draw there.
- The follow loop calls `show` / `moveTo` / `hide` from a virtual thread; each hands its work to the EDT.
