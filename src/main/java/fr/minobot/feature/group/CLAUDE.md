# The party

Forming the group. One feature for now: the invitation relay. It **speaks of characters**
(`GameWindow`) and keeps whatever Windows demands in one named leaf at the bottom — see the root
`CLAUDE.md`. It waits on the same game toasts the `notification/` features live on, and it competes
with them for the foreground; read `core/CLAUDE.md` (*The foreground is a single resource*) first.

## Group invitation — `F8`

A relay: the first character invites the second, who accepts and invites the third, and so on. Each
step waits for the game's own **Windows toast** to confirm the invitation landed, rather than guessing
a delay. No toast within five seconds and the relay **stops there**: the ENTER that accepts an
invitation, pressed in a window that has none on screen, opens the chat instead — and the next
`/invite` is then typed into the game rather than into the chat. The relay owns the foreground from
end to end (`focus.takeOver()`); the same toasts feed the auto-focus, which must not answer them here.

**This is why the relay is tested against the auto-focus.** `GroupManagerTest` hands one toast to both
`GroupManager` and the `notification/NotificationListener` — the way `NotificationManager` dispatches
to every listener — and proves the relay keeps the foreground while it types. That cross-feature test
is the reason `NotificationListener.onNotification` is `public` where the other toast handlers are
package-private: the relay lives in another package and must drive it directly.
