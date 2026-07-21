# App — wiring and configuration

`app/` is where everything is wired (`MinobotApp`) and where the player's settings live (`Config`,
`Settings`, `Feature`, `ConfigLoader`, `OverlayState`, `LoggerSetup`).

## Config

`Config` holds what a *player* changes: their `characters` (each a `Character` — name, and the class and
sex they pinned — in cycle order), their `multiclick_exclude`, their
hotkeys, their `log_level`, their `overlay_scale`, their `auto_pass_turn`, their `auto_accept_trade`. Nothing else. A timing, a mouse button, a keyword of the game's window
titles, a dry-run flag — those are dictated by Windows or by the game, not by the player, and belong
in a constant next to the code that reads it. **Adding a field to `Config` is a claim that a player
has a reason to change it**; a knob nobody turns is a knob that silently breaks a feature when someone
does. An empty hotkey disables its feature; there is no `*_enabled` flag — save two: `auto_pass_turn`
and `auto_accept_trade`, the two overlay switches, because those features have no hotkey to blank. They
are the only booleans here, and they earn their place by being things the player deliberately flips on
the overlay.

`Feature` enumerates the features that own a hotkey, and each one's slot in `Config` and cooldown. The
hotkeys are therefore walked, not listed by hand: `MinobotApp` walks them to bind, and the overlay will
walk them to show. A feature added to the enum and forgotten in the switch that gives it its action is
a compile error, which is the point.

**The configuration is live.** `Config` is still the immutable record it always was; `Settings` holds
the current one and swaps a whole new one in, so a reader never sees one field of the old and one of
the new. A feature that reads it *at every use* — `WindowManager.rank()`, the multi-click's exclusions
— is current for free and must stay that way: **do not hoist a config value into a field at
construction**, that is precisely what makes a setting un-editable. A feature that must *react* to a
change (`MinobotApp` re-registering the hotkeys) listens through `Settings.onChange`.

The configuration has **two tiers on disk**. `config.json` is the player's **read-only defaults** —
generated once, edited by hand, never rewritten by the application. `overlay.json` (see `OverlayState`)
holds the two overlay edits that must survive a restart — the `characters` (their order, and the class
and sex pinned to each) and the seven
hotkeys — and it overrides `config.json` at load, key by key, so a default the player never touched
still shows through. It holds *only* that subset: everything else the overlay changes — the
`overlay_scale`, the two switches — reaches no disk and a restart forgets it, the switches deliberately
(an `auto_pass_turn` found already on would end turns nobody asked it to).

`Settings` itself still writes **nothing**: persistence is one more `onChange` listener wired in
`MinobotApp`, exactly like the hotkey rebinding, and it serializes only the persisted subset. Note the
consequence: once a keybind is rebound on the overlay it lives in `overlay.json`, so editing it by hand
in `config.json` no longer has any effect — the persisted choice wins. To go back to the defaults,
delete `overlay.json`.
