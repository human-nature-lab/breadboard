# Group Steps + Waiting Room — Implementation Plan

Status: draft — for discussion.

This doc covers what was just dropped into the repo (the three "initial implementation"
files) and the work still needed to actually wire them up. The two big themes are
**(1) how group-scoped choices are routed end-to-end** (this is the
`PlayerChoices` change the user flagged), and **(2) how Groups/WaitingRoom integrate
with the existing `ScriptBoard` boot sequence**.

---

## 1. Files just added

| Path | Role |
|---|---|
| `groovy/groups.groovy` | `GroupRegistry`, `Group`, `BaseGroupStep`, `GroupActions` |
| `groovy/waiting_room.groovy` | `WaitingRoom`, `WaitingRoomReadyUp`, `RecruitmentClient`, `RecruitmentController` |
| `frontend/client/src/components/WaitingRoomStep.vue` | Player-facing waiting-room UI |

The Vue file lands under `frontend/client/src/components/` which is the directory
swept by `frontend/client/src/vue-components.ts` (`loadAllVueComponents` registers
every `*.vue` in that tree as a global component using the file's basename). So
the experiment template can reference `<WaitingRoomStep :player="player" />`
without an explicit import.

---

## 2. ScriptBoard load order — needs a patch

The script loader in `app/models/ScriptBoard.java:138-149` is a **hardcoded list**,
not a directory scan. New groovy files don't load until they're added there.

Proposed list (with the two new entries):

```java
String[] scriptFiles = {
  "/util.groovy",
  "/timer.groovy",
  "/graph.groovy",
  "/actions.groovy",
  "/step.groovy",
  "/test.groovy",
  "/events.groovy",
  "/chat.groovy",
  "/form.groovy",
  "/ready.groovy",
  "/groups.groovy",       // NEW — needs `a` (actions.groovy) + Vertex.on/off (events.groovy)
  "/waiting_room.groovy"  // NEW — needs BBTimer/SharedTimer (timer.groovy), BreadboardBase (util.groovy), Vertex.on/off (events.groovy)
};
```

Ordering rationale:
- `groups.groovy` references `player.on(SUBMIT_EVENT, …)` from `GroupActions.attachChoiceListener`. The `Vertex.metaClass.on` is installed by `events.groovy`, so groups must load after.
- `groups.groovy` doesn't import `a` directly, but `GroupRegistry.context('a')` is read lazily — fine.
- `waiting_room.groovy` extends `BreadboardBase` (util) and uses `BBTimer`/`SharedTimer` (timer). Both already load earlier.

---

## 3. The `GroupRegistry.bindContext(...)` bootstrap is missing

`groups.groovy` requires someone to call:

```groovy
GroupRegistry.bindContext([
  a: a,
  g: g,
  c: c,
  removePlayers: removePlayers,  // see §4
])
```

Otherwise `BaseGroupStep.g/c` are null and `GroupActions.addEvent` throws.

**Where to put this:** append a binding block to the end of `groups.groovy`
itself. The script-scope bindings (`a`, `g`, `c`) are available by the time
`groups.groovy` evaluates, since it loads after `actions.groovy`/`graph.groovy`
and `c` is put on the engine scope in `ScriptBoard.java:135`.

```groovy
// End of groups.groovy
GroupRegistry.bindContext([
  a: a,
  g: g,
  c: c,
  removePlayers: binding.hasVariable('removePlayers') ? removePlayers : null,
])
```

---

## 4. `removePlayers` global — does not exist yet

`Group.dropPlayers` looks up a global `removePlayers` closure via the registry,
but **no such closure is defined anywhere in `groovy/`** (verified via grep).
The comment in the source refers to a `0Globals.groovy` that doesn't exist in
this repo.

Options:
- **(a)** Define a default `removePlayers` in `groups.groovy` that just calls
  `gameListener.removePlayer(...)` or similar. Needs a quick read of
  `BreadboardGraphInterface`/`GameListener` to see what's actually available.
- **(b)** Make `dropPlayers` a no-op + log when no `removePlayers` is bound, and
  require each experiment to bind its own helper. Safest near-term.
- **(c)** Move the global closure into a new `globals.groovy` (or extend
  `util.groovy`) and load it before `groups.groovy`.

Recommendation: **(b)** for the first cut. Move to **(c)** once we know what an
experiment-side "drop with completion code + message" should actually do.

---

## 5. Player choices: the routing problem

This is the change the user already flagged. Here's the concrete picture.

### Current platform flow (single-graph mode)

1. Step code calls `a.add(player, [name: "Yes", result: { ... }])`.
2. `PlayerActions.add` stores `actions[uuid] = playerAction` AND sets
   `player.choices = [{uid, name, ...}]`.
3. Client renders `PlayerChoices.vue`, user clicks. The Vue calls
   `Breadboard.sendChoice(uid)` → WebSocket `MAKE_CHOICE` action with `choiceUID`.
4. Server resolves via `a.choose(uid)`, which looks `uid` up in `a.actions`.

### What `GroupActions` does today

`GroupActions.assignChoice` pushes `{uid, name}` directly onto `player.choices`
**but never registers anything in `a.actions`**. So if the client called
`sendChoice(uid)`, the server's `a.choose(uid)` would find nothing and the
click would silently no-op.

Instead the design routes through a custom event:
- Client must call `Breadboard.send('group-action-submit', { uid })`.
- That arrives as `CUSTOM_EVENT` → `events.emit(makePlayerEventHash(playerId, 'group-action-submit'), player, data)` (`events.groovy:86-103`).
- The listener installed by `GroupActions.attachChoiceListener` picks it up.

So **today** the existing `PlayerChoices.vue` will not work for group choices —
it always calls `sendChoice`.

### Three options for fixing this

#### Option A — Mark group choices in the choice map, branch in PlayerChoices

In `GroupActions.assignChoice`, set a flag on the choice we push to `player.choices`:

```groovy
private void assignChoice(Object player, Map choice) {
  if (player.choices == null) player.choices = []
  player.choices << (choice + [_route: 'group'])  // or [group: true]
}
```

Then in `PlayerChoices.vue:42-50`:

```js
submit (choice) {
  if (choice._route === 'group') {
    window.Breadboard.send('group-action-submit', { uid: choice.uid })
  } else if (choice.params) {
    window.Breadboard.sendChoice(choice.uid, choice.params)
  } else {
    window.Breadboard.sendChoice(choice.uid)
  }
  this.choicesAreEnabled = false
}
```

**Pros:** minimal blast radius, frontend stays a single component, easy to
explain. Closest to what the user's snippet already assumes.
**Cons:** the choice payload now carries a routing flag that's a leaky
implementation detail; we'd want it to be `_route`/underscore-prefixed so
experiment authors don't accidentally collide.

#### Option B — Server-side dispatch: extend `PlayerActions.choose` to also try groups

Leave the wire format alone. Have `a.choose(uid)` fall through to a group
lookup when the uid isn't in `a.actions`:

```groovy
// In actions.groovy
def choose(String uid, Map parsedParams) {
  PlayerAction action = actions[uid]
  if (action != null) {
    // ... existing path ...
    return
  }
  // Fallback: maybe it's a group action.
  GroupRegistry.dispatchChoice(uid, parsedParams)
}
```

`GroupActions` would register its uids in a static index on `GroupRegistry`
when `add()` is called, and look them up on dispatch.

**Pros:** frontend changes zero. One code path on the client. Group steps and
regular steps are interchangeable from the player's POV.
**Cons:** couples `actions.groovy` and `groups.groovy`. Adds a static
registry of in-flight choice uids that needs cleanup on `dispose`/`remove`.

#### Option C — Route everything through a custom event

Replace `Breadboard.sendChoice` with `Breadboard.send('choice', { uid, params })`
project-wide. Server picks both up via the existing custom-event channel.

**Pros:** philosophically clean (one wire protocol).
**Cons:** breaks every existing experiment's `a.add(...).result` flow until
we re-implement it on top of the custom-event bus. Out of scope.

### Recommendation

**Option B** (server-side fallback dispatch). It keeps the frontend as a
single dumb renderer of `player.choices`, doesn't ask experiment authors to
think about routing, and makes group steps a transparent superset of the
existing step model. The frontend `PlayerChoices.vue` would not need any
changes.

We'd still want a small `_routing.md` comment in `actions.groovy` and
`groups.groovy` explaining the fallback path.

If we go with **Option A** instead because B's coupling is undesirable, the
`PlayerChoices` patch is ~5 lines and is the only frontend change.

---

## 6. WaitingRoom — known gaps in the initial implementation

These are issues I noticed reading the file but didn't fix; they're worth a pass
before any real use.

1. **`requiredPlayers` is never propagated to players.**
   `WaitingRoomStep.vue` reads `this.waitingRoom.requiredPlayers`, but
   `WaitingRoom.addPlayers` never writes it. Add
   `requiredPlayers: this.minPlayers` to the `_system.waitingRoom = [...]`
   map in `addPlayers`.

2. **`_foundGroupTimer` not cancelled on the failure path.**
   `startPendingGroups()` resets `foundGroup`/`foundGroupAt` when the
   ready-start window expires without enough players, but `_foundGroupTimer`
   keeps running until its own `result` closure fires. Should cancel it when
   we reset.

3. **`_loopTimer.cancel()` vs `BBTimer.cancel()`.**
   `BBTimer.cancel()` unregisters and the timer can't be reused. If `start()`
   is ever called twice in the lifetime of a WaitingRoom, `scheduleAtFixedRate`
   on a cancelled timer will throw. Either reinstantiate or gate `start` with a
   "started" flag.

4. **`handleReadyUpResult` modifies `waitingPlayers` outside the lock.**
   The `waitingPlayers.removeAll(selectedPlayers)` happens between the
   per-player removals (which take the lock per-call) and the SharedTimer
   scheduling. The window is small but worth tightening.

5. **`WaitingRoomReadyUp.players` is typed `ArrayList<Vertex>`.**
   The file does not `import com.tinkerpop.blueprints.Vertex`. `ready.groovy`
   gets away with bare `Vertex` because the type resolves at runtime, but
   that's fragile. Add the import or rely on `def`.

6. **`player.off("waiting-room:ready")` after timer fires** — currently called
   inside the result closure. If a player is added late (within 5s of the
   end), the listener is registered after `players.each { off(...) }` already
   ran, so the listener may persist into the next round. Re-using `once` is
   already protective on the listener side; the stale flag in
   `_system.waitingRoom.isReady` is the real concern. Worth a second look.

7. **`isLoopRunning` skip-if-busy.** The TODO at the top of the file already
   flags this. For the first cut it's fine; revisit if any loop step is ever
   long-running.

8. **`groupCompleted` is a public method but is never called from anything.**
   Whoever owns the group lifecycle needs to call this when a Group finishes
   so `RecruitmentController.completedGames` advances. The natural call site
   is in the experiment's "game over" handler.

---

## 7. Wiring WaitingRoom → Group

The current `WaitingRoom.onGroupReady` callback is `cb(selectedPlayers, groupId)`.
The intended consumer is the experiment, which should instantiate a `Group`:

```groovy
def wr = new WaitingRoom(15, 25)
wr.onGroupReady { players, groupId ->
  def group = new Group(groupId as String, players)
  GroupRegistry.put(group)
  group.register("intro",  IntroStep)
  group.register("trial",  TrialStep)
  group.register("debrief", DebriefStep)
  group.start("intro")
}
wr.onReadyUpFailure { player ->
  // hand the player back to the queue, drop them, etc.
}
wr.start()
```

This wiring lives in the experiment, not in core. We may want a thin helper
(`WaitingRoom.bindGroupFactory(Closure)`) to make it boilerplate-free, but
that's a follow-up.

---

## 8. Open questions

1. **Step name "waiting-room" on the client.** The current setup pushes
   `player.private.step = "waiting-room"`. Does the existing frontend
   dispatcher render a step component by the value of `player.private.step`?
   If so, we need to confirm the component lookup is case-sensitive and that
   `WaitingRoomStep` (PascalCase, filename-based) is reachable by the
   `"waiting-room"` key. Likely we need a small mapping step → component, or
   we rename the file to `waiting-room.vue` to match the key directly.

2. **Multiple concurrent groups.** `GroupRegistry.byId` supports it, but
   `_system.groupId` is a single string; nothing prevents one player from
   being in two groups. We should decide if that's an invariant we want to
   enforce or just document.

3. **AI players.** `PlayerActions.add` triggers the AI on choice add. Group
   choices bypass that path entirely — AI players won't progress. Do we need
   group-AI support or is the matchmaker only for human studies?

4. **Event names.** `'waiting-room:ready'` (colon) and
   `'group-action-submit'` (hyphenated) are arbitrary strings on the client →
   server custom-event bus. Worth centralizing them in a single constants
   file shared between groovy and TS so a typo doesn't silently break a
   round.

5. **Choice routing decision (§5).** Need to pick Option A vs B before
   writing any group steps in earnest.

---

## 9. Suggested order of work

1. **Decide §5: A or B** (the choice-routing question). Everything else
   compiles either way.
2. Patch `ScriptBoard.java` load list (§2).
3. Add `GroupRegistry.bindContext` block at the end of `groups.groovy` (§3).
4. Implement chosen routing approach.
5. Fix `requiredPlayers` propagation (§6.1) — minimum to make the Vue
   component show a useful number.
6. Write one tiny smoke-test experiment: enter waiting room, ready up,
   create a Group with two trivial steps, dispose. Confirm:
   - choice submit triggers the group handler
   - `done` fires automatically when the pending queue empties
   - `dispose` cleans up `_system.groupId`
7. Address WaitingRoom punch list (§6.2–6.8) opportunistically.
