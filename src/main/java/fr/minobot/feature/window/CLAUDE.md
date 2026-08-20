# Window mechanics

The multi-boxer's window features: replay one click across every character, walk the focus from one to
the next, and put the taskbar buttons back in order. Each **speaks of characters** (`GameWindow`) and
keeps whatever Windows demands in one named leaf at the bottom — see the root `CLAUDE.md`. They all
compete for the foreground with everything else; read `core/CLAUDE.md` (*The foreground is a single
resource*) before adding to them.

## Multi-window click — `x1`, and `shift+x1` to reset

Replays one click on every game window at the same spot. It must be (1) as fast as possible, (2) never
cost the main character its focus, and (3) reach every character.

The click is **posted** (`PostMessage`), not simulated, so the windows never take the focus. The
position travels through the **client** area, not the screen: the same client coordinates land on the
same in-game spot in every window, wherever they sit on the desktop.

(3) is why the click **sweeps the desktop itself** instead of reading the thirty-second one, and why it
**waits for every posted click** before recording anything. Both look like a cost against (1) and are
not: the sweep is the one the panel already pays twice a second, and the wait costs the slowest click,
not the sum. What they buy is a character who logged in a moment ago — unranked, therefore *last* in the
order — and a window whose first post was refused: dropped from the click and from `FlashSuppressor`'s
list respectively, either one reads to the player as *the last window is never clicked*.

A clicked background window flashes orange in the taskbar — and **it is the game that lights it up,
not us**: it asks for the foreground when it drains the click, Windows refuses (the same rule the ALT
trick works around), and a flashing taskbar button is the consolation prize Windows hands a refused
application.

That button does **two** things, and `core.FlashSuppressor` needs one lever for each. Both were
measured against the real game; neither is guesswork, and neither alone is enough.

*It blinks*, and that blinking **cannot be cut short**: `FlashWindowEx(FLASHW_STOP)` is ignored while
the shell is playing the animation, however fast it is swept — which is why cancelling at the moment of
the click cancels nothing. All that can be done is to make the animation short. Windows exposes how
many blinks it plays on a refusal (`SPI_*FOREGROUNDFLASHCOUNT`, seven by default) and the quietest it
goes is **one**. **Zero is not silence** — like `uCount` in `FlashWindowEx`, zero means "blink until
the window is activated", and setting it makes things *worse*. It is a setting of the whole Windows
session, borrowed by `suppress()` at startup, so **every exit path must go through `restore()`**
(`SystemParametersInfo` without `SPIF_UPDATEINIFILE`: the change dies with the session, and a killed
Minobot leaves nothing a logout does not undo).

*Then it stays orange* — a separate state, the one that actually annoys. `FlashWindowEx(FLASHW_STOP)`
**does** clear it, but only once the blinking is over, so `watch()` sweeps the clicked windows for three
seconds afterwards on a virtual thread — never on the click's own, which must stay as fast as it is.
`shift+x1` remains the last resort.

**A clicked character goes deaf, and `shift+x1` is the only cure.** The game raises its toast only for
a character nobody is watching; a posted click is, seen from inside the game, a click like any other,
so it concludes the player is there and stops notifying. The notification auto-focus lives on those
toasts and dies with them. This is a limit of the game client, not a bug to be fixed here — and it was
chased to the end before being written down: a single posted click deafens the window it hits and no
other; no toast is raised at all (the Windows notification database stays *empty*, so nothing is being
missed); and the game cannot be talked out of it. `WM_ACTIVATE`, `WM_NCACTIVATE`, `WM_KILLFOCUS`,
`WM_ACTIVATEAPP`, a full activation/deactivation cycle, a real `SetFocus` through `AttachThreadInput` —
all measured against the real game, all ignored. Only an activation that genuinely brings the window up
on screen re-arms it, which is exactly what `shift+x1` does. **Do not post messages at the game hoping
to undo this: that ground is burnt.**

## Window cycler — `x2` / `shift+x2`

Cycles the focus through the windows in the player's character order (`Config.characterOrder()`). Only the windows of the
**current monitor** take part. Far better than Alt+Tab with several accounts.

## Window reorder — `F9`

Rebuilds the taskbar order: Windows offers no way to reorder its buttons, so the sequence hides every
game window and shows them again in the configured order. **The windows are hidden in the middle of
this** — every failure path must end by showing them again, or the player loses them.
