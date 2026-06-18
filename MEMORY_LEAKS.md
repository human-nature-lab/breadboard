# Memory Leak Review — Breadboard

A review of memory-retention issues in the Java/Play + Groovy backend, with
particular attention to **`ScriptBoard`** and the **script-engine reload
process**, as requested. **Nothing in this document has been fixed** — it is a
catalogue of findings with locations, mechanisms, triggers, severity, and
suggested directions. No production files were changed while producing it.

> Scope note: this is a static read of the code, not a profiler run. Severities
> reflect how unbounded the growth is and how often the trigger fires in normal
> use. Reproduction sketches are included where a leak is easy to demonstrate.

---

## How "reload" works (context for the findings)

The script engine is rebuilt by `ScriptBoard.rebuildScriptBoard()` →
`ScriptBoard.resetEngine()` (`app/models/ScriptBoard.java:219`, `:85`). A reload
is triggered far more often than one might expect — by **every** one of:

- `ChangeExperiment` (`ScriptBoard.java:418-420`) — i.e. selecting an experiment
- `LaunchGame` (`:489-493`)
- `SelectInstance` (`:513-515`)
- `ReloadEngine` (`:618-620`) — also reached from `GameFinish` (`:615-617`)
- the `ScriptBoard` constructor (`:63-65`)

On each reload, `resetEngine()`:

1. Creates a **brand-new** engine: `engine = manager.getEngineByName("gremlin-groovy")` (`:126`).
2. Re-evaluates the 10 bootstrap Groovy scripts (`:138-159`) and then all steps
   via `loadSteps()` (`:208-213`).
3. Reuses the long-lived `static` collaborators (`graphChangedListener`,
   `fileWatcher`, `eventBus`, `clients`, `admins`, …) rather than rebuilding them.

The combination of "new engine every time" + "reused static collaborators that
are never pruned" is the source of most of the leaks below.

---

## Critical

### L1 — The Groovy `ScriptEngine` / `GroovyClassLoader` is never disposed on reload (Metaspace/PermGen leak)

**Where:** `app/models/ScriptBoard.java:126` (and the eval loop `:138-159`,
`:211`, `:727`).

**Mechanism.** Each `resetEngine()` allocates a *new* `gremlin-groovy` engine,
which carries its own `GroovyClassLoader`. Re-evaluating the bootstrap scripts
defines dozens of Groovy classes (`BBTimer`, `SharedTimer`, `BreadboardGraph`,
the `*Base` hierarchy, …) and compiles each `eval`'d string into a fresh
`Script<N>` class inside that loader. The previous `engine` reference is simply
overwritten — there is no `engine = null`, no loader `clearCache()`, no attempt
to dispose it.

The old loader does **not** become collectable, because the bootstrap scripts
install **global `metaClass` mutations** that the Groovy runtime tracks in the
process-wide `MetaClassRegistry` / `ClassInfo` structures:

- `Vertex.metaClass.* = …` and notably `Vertex.metaClass.off << { … }` (`groovy/events.groovy:21-64`)
- `EventEdge.metaClass.* = …` (`groovy/util.groovy:22-48`)
- `BreadboardBase.metaClass.* = …` (`groovy/util.groovy:89-104`)
- `BBTimer.metaClass.* = …`, `SharedTimer.metaClass.* = …` (`groovy/timer.groovy:22,26,342,346`)
- plus `chat.groovy`, `form.groovy`, `ready.groovy`

`ExpandoMetaClass` entries are keyed on the target class and hold the closure
instances, which belong to the *old* `GroovyClassLoader`. Because those entries
live in a static registry, the old loader (and every `Script<N>` class it
compiled) stays reachable and **is never unloaded**. Net effect: **Metaspace
grows on every reload** (PermGen on the pinned Java 7/8 runtime — see
`TESTING.md`), trending toward `OutOfMemoryError: Metaspace`.

The `<<` append on `Vertex.metaClass.off` (`events.groovy:52`) is a secondary
concern: it *adds* a method variant instead of replacing one, so repeated
reloads can also pile up method implementations on the surviving global `Vertex`
metaclass.

**Trigger.** Every experiment select / instance select / game launch / game
finish / explicit reload. In an authoring session this is constant.

**Severity:** Critical — unbounded, non-heap (won't be relieved by GC), and the
trigger is routine.

**Reproduction sketch.** Drive `resetEngine()` (or the existing
`ScriptTestHarness`) in a loop of, say, 200 reloads with
`-XX:MaxMetaspaceSize=128m -verbose:class`; watch loaded-class count climb and
Metaspace exhaust. A heap dump will show many `GroovyClassLoader` instances each
rooted in `MetaClassRegistryImpl`.

**Investigation (2026-06-03).** Two fixes were prototyped and measured against the
test engine:

1. Dispose the old engine's global metaclasses on reload —
   `GroovySystem.getMetaClassRegistry().removeMetaClass(...)` for the two library
   classes the scripts mutate (`Vertex`, `EventEdge`) and the six script-defined
   classes (`BreadboardBase`, `BBTimer`, `SharedTimer`, `BaseChatManager`,
   `PageSection`, `ReadyUpSequence`, resolved by `eval`-ing their names). All 8
   metaclasses were removed successfully.
2. Additionally clear the engine's `GroovyClassLoader` cache (`clearCache()`), found
   via reflection (it had ~135 loaded classes per engine).

**Neither released the loader.** A `WeakReference` to the engine's
`GroovyClassLoader` did **not** clear after dispose, after dispose+`clearCache()`,
or with no dispose at all. Residual global Groovy state —
`org.codehaus.groovy.reflection.ClassInfo`, a default `MetaClassImpl` cached per
compiled `Script`/script class, and call-site caches — keeps the loader reachable,
and **Groovy 1.8.6 exposes no safe public API to sweep it**. This is the documented
Groovy-1.8 script-engine reload leak.

**Decision: accept + mitigate operationally (no code change).** A correct fix needs
an architectural change — load the class-defining bootstrap scripts **once** and, on
"reload", reset only bindings + clear the user graph/state + re-run steps, instead of
rebuilding the whole `gremlin-groovy` runtime each time (`ScriptBoard.resetEngine`,
`ScriptBoard.java:126`). That is a non-trivial refactor with real behavior risk and is
deferred. Until then:

- Run with a generous, bounded Metaspace, e.g. `-XX:MaxMetaspaceSize=512m` (PermGen
  equivalent on the Java 7/8 runtime: `-XX:MaxPermSize`), so growth fails loudly and
  predictably rather than silently exhausting the default.
- **Restart the JVM periodically** (and/or after heavy authoring sessions that trigger
  many experiment selects / instance selects / game launches / reloads).
- Treat the "reuse one engine" refactor above as the real fix; revisit if reload
  frequency grows.

---

### L2 — `EventGraphChangedListener.clientListeners` is `static` and never pruned

**Where:** `app/models/EventGraphChangedListener.java:15` (declaration, `static`)
and `:35` (`addClientListener` puts; nothing ever removes).

**Mechanism.** Every websocket connection routes through
`ScriptBoard.addClient()` → `graphChangedListener.addClientListener(client)`
(`ScriptBoard.java:319-321`). There is **no** `removeClientListener`: the map
only ever grows. On disconnect, `disconnectClients()` (`ScriptBoard.java:77-83`)
clears `ScriptBoard.clients` but leaves this map untouched; the reload path also
leaves it untouched (the listener is reused via `setGraph`, `ScriptBoard.java:183-186`).

Because the field is **`static`**, the map is shared across *all* `ScriptBoard`
actors / all users, so it accumulates every `Client` that ever connected, across
disconnects, reloads, instances and experiments. Each retained `Client` pins a
`WebSocket.In`, a `ThrottledWebSocketOut`, and an `ExperimentInstance`
(`app/models/Client.java:21-33`).

**Trigger.** Every player/admin connection over the server's lifetime.

**Severity:** Critical — unbounded heap growth keyed on total historical
connections, plus a correctness hazard (stale clients receive `updateGraph`).

**Status (2026-06-03): FIXED.** `clientListeners` is now an instance field (was
`static`); `removeClientListener` was added to the `BreadboardGraphChangedListener`
interface and both implementations; and `ScriptBoard.disconnectClients()` prunes the
registry on reload. Tests:
`WebSocketTest.{registeredClientReceivesUpdatesThenRemovedClientDoesNot,
clientListenerRegistryShrinksOnRemove, clientRegistriesAreIsolatedPerListener}`; the
reflective `@After` workaround was removed (its removal is itself evidence the shared
state is gone). **Deviation:** clients registered *before* a reload are now removed
and no longer dispatched to — previously they leaked and kept receiving `updateGraph`
writes to their already-closed sockets until a same-id client reconnected.

---

## High

### L3 — `IteratedBreadboardGraphChangedListener.clientListeners` likewise never pruned

**Where:** `app/models/IteratedBreadboardGraphChangedListener.java:23` (map),
`:94` (`addClientListener` puts).

**Mechanism.** Same shape as L2 but on the polling listener (used when
`breadboard.clientUpdateRate` is configured, `ScriptBoard.java:179-181`). No
removal method exists; the listener instance is reused across reloads
(`setGraph`, `:185`), so the map persists and accumulates `Client`s across
disconnects and reloads. The scheduled `ClientUpdate` tick
(`actors/ClientUpdateActor.java:30`) iterates this map forever, so stale clients
are also repeatedly serviced.

**Severity:** High — unbounded, though scoped to the listener instance rather
than `static`.

**Status (2026-06-03): FIXED** together with L2 — `removeClientListener` was added
here too (the map was already instance-scoped) and is called from
`ScriptBoard.disconnectClients()` on reload.

---

### L4 — Akka scheduler `Cancellable`s discarded; `static` actor refs overwritten

**Where:**
- `app/models/FileWatcher.java:47-56` — `fileWatcherActor` (`static`, `:30`) +
  `scheduler().schedule(...)` whose return value is ignored.
- `app/models/IteratedBreadboardGraphChangedListener.java:29-42` —
  `clientUpdateActor` (`static`, `:25`) + ignored `schedule(...)`.

**Mechanism.** Both schedule a *recurring* task and throw away the `Cancellable`,
so nothing can ever stop them. Both store their actor in a **`static`** field.
They're guarded by a null check so normally created once, but the construction
sites live in objects that *are* recreated: a new `FileWatcher` is only made when
`fileWatcher == null` (`ScriptBoard.java:190-192`), yet `graphChangedListener`
can switch implementation based on config, and any second construction
**overwrites the `static` ref**. The previously scheduled task keeps firing
forever against the orphaned actor, pinning the previous `FileWatcher` /
`IteratedBreadboardGraphChangedListener` (and through it the previous graph,
client map, and `WatchService`). Nothing is cancelled even if the owning actor is
stopped.

**Severity:** High — leaked live threads/timers plus the object graphs they pin;
also a `WatchService` (native handle) on the `FileWatcher` path.

**Status (2026-06-03): DEFERRED (by decision).** This is a *latent* risk, not an
active leak: both schedulers are null-guarded singletons created exactly once and are
meant to run for the JVM's lifetime, so the static-overwrite path cannot trigger in
the current flow. The clean change (capture the `Cancellable`, make the actor refs
instance-scoped, add `stop()`) is correct hygiene but is **not unit-testable** under
the single-`FakeApplication` harness: `FileWatcher` needs a `dev/` directory that
doesn't exist; the polling listener needs a positive `breadboard.clientUpdateRate`,
but the suite pins it to `0` (→ a zero-interval schedule Akka rejects), and standing
up a second Play app would break the documented single-app design. Deferred pending
either that scaffolding or the L1 reuse-one-engine refactor.

**Original direction.** Keep the `Cancellable` and cancel it on reload/teardown; make
the actor refs instance-scoped; cancel-then-recreate rather than overwrite.

---

## Medium

### L5 — `UserDataChangeListener` registered on every launch/select, never removed

**Where:** `app/models/ScriptBoard.java:497` (`LaunchGame`) and `:533`
(`SelectInstance`): `instanceData.addPropertyChangeListener(new UserDataChangeListener(instance))`.

**Mechanism.** A new listener is attached to the `d` `ObservableMap` each launch
and each instance select; there is no matching `removePropertyChangeListener`.
Each listener captures an `ExperimentInstance`. Repeated selects/launches stack
duplicate listeners on the same map. In practice each of these paths runs
`rebuildScriptBoard()` first (so `instanceData` is usually a fresh map with a
single listener), which limits *live* accumulation — but the old map and its
listener chain are only released when the old engine is, which per **L1** never
happens. So this compounds the engine leak rather than standing fully alone.

**Severity:** Medium.

**Status (2026-06-03): SUBSUMED by the L1 decision.** Every
`addPropertyChangeListener` call is preceded by `rebuildScriptBoard` (a full engine
reset → a *fresh* `d` `ObservableMap`), so listeners never stack on the same map, and
the old map + its listener chain are released when the old engine is discarded. With
L1 accepted (engine rebuilt on every reload), there is no independent leak to fix
here. No code change.

---

### L6 — One-shot `java.util.Timer`s in `HitCreated` are never cancelled

**Where:** `app/models/ScriptBoard.java:375` and `:383` — two `new Timer().schedule(...)`
per AMT HIT.

**Mechanism.** Each creates a `Timer` (and its thread) that is never cancelled
and is unrelated to engine lifecycle. The tasks capture `breadboardOut` /
`gameListener`. After firing, an unreferenced one-shot `Timer` is eventually
collectable, but until the (potentially long) lifetime/tutorial delay elapses it
holds a stale `ThrottledWebSocketOut`; a reload or re-submit in the interim
leaves these orphaned tasks running against now-defunct state.

**Severity:** Medium (bounded per HIT, but unmanaged and tied to a stale socket).

**Status (2026-06-03): FIXED.** The two timers are now scheduled through a tracked
registry — `ScriptBoard.scheduleAmtTimers` / `cancelAmtTimers`, backed by a
`CopyOnWriteArrayList<Timer>`. A re-submit cancels the prior pair before scheduling,
and `resetEngine` calls `cancelAmtTimers()` on reload. The scheduling was extracted
out of the `HitCreated` actor handler so the contract is unit-testable without the
actor or a live engine: `models.ScriptBoardAmtTimerTest` (timers still fire on their
delays; `cancelAmtTimers` prevents firing and clears the registry; a re-submit
supersedes the previous pair). **Deviation:** pending AMT timers are now cancelled on
engine reload and superseded on re-submit; previously every scheduled pair fired
regardless. So a reload between HIT submission and firing will now stop the
(orphaned) `hasStarted()` / `initStep.start()` from running — the intended fix.

---

### L7 — `admins` and the listeners' admin lists are never pruned; `init()` is disabled

**Where:** `ScriptBoard.java:48` (`static admins`), populated at `:224-227`;
parallel lists in `EventGraphChangedListener.java:14` /
`IteratedBreadboardGraphChangedListener.java:22`. The reset routine that *would*
reset `admins` is the commented-out `//init();` at `ScriptBoard.java:220`
(definition `:67-75`).

**Mechanism.** Admins are added on instance creation but never removed on
disconnect, and `init()` (which reassigns `admins`, `clients`, `results`,
`eventTracker`, `manager`) is intentionally not called from `rebuildScriptBoard`.
Each retained `Admin` pins a `User`, an `ActorRef`, and a `ThrottledWebSocketOut`
(`app/models/Admin.java:21-30`). Growth is bounded by the number of distinct
authoring users, but it is never released for the process lifetime, and it is
duplicated across the `static` `admins` list and the listener's admin list.

**Severity:** Medium (bounded but permanent).

**Status (2026-06-03): DEFERRED (by decision).** Growth is bounded by the number of
distinct authoring users. There is no admin-disconnect hook today, so a real fix
requires adding a websocket `onClose` handler plus a `RemoveAdmin` actor message — a
feature change touching the websocket lifecycle, integration-tested, with more
behavior risk than the (bounded) leak currently warrants. Documented, not applied.

**Original direction.** Remove admins on disconnect; reconcile the duplicate lists;
decide whether `init()` should run on reset.

---

### L8 — Per-`eval` `Script` classes accumulate within a live engine (no reload required)

**Where:** `ScriptBoard.processScript` (`app/models/ScriptBoard.java:727`),
`loadSteps` (`:211`), and `FileWatcherActor` step reloads
(`app/actors/FileWatcherActor.java:78-84` → `processScript`).

**Mechanism.** Every `engine.eval(source)` compiles a new `Script<N>` class into
the *current* engine's `GroovyClassLoader`. Sending a script (`SendScript`),
running a step (`SendStep`/`RunStep`), and especially the file watcher re-loading
a step **on every file save** all add classes that the loader caches for its
lifetime. So even without a full reload, an active authoring/editing session
grows heap + Metaspace steadily. (When a reload eventually happens, that whole
loader is then pinned per **L1**.)

**Severity:** Medium — slower than L1 but driven by ordinary editing activity.

**Status (2026-06-03): SUBSUMED by the L1 decision.** This is the same Groovy
class/Metaspace growth as L1 (each `eval` compiles a `Script` class into the engine's
loader; the file-watcher recompiles steps on every save). It is covered by L1's
accept + operational mitigation; a real fix is step-compilation caching or the
reuse-one-engine refactor. No separate change.

**Original direction.** Compile steps once and cache compiled scripts; or
periodically reset the loader; avoid recompiling unchanged step source on every
file-watch tick.

---

## Design amplifier (not a single leak, but it multiplies the above)

### L9 — `ScriptBoard`'s `static` state vs. one actor per user

`ScriptBoard` holds ~15 `private static` fields — `engine`, `manager`,
`results`, `clients`, `admins`, `graphChangedListener`, `eventBus`,
`eventTracker`, `fileWatcher`, `playerActions`, `graphInterface`, `instanceData`
(`ScriptBoard.java:31-49`) — while `Breadboard` creates **one `ScriptBoard`
actor per user** (`app/models/Breadboard.java:232,242`). Because the state is
`static`, all those actors share it: every user's reload rebuilds the *same*
shared engine and stomps the *same* shared maps, so the leaks above are
**process-wide**, not per-user, and concurrent users corrupt each other's state.
The commented-out `//init();` (`ScriptBoard.java:220`) and the unused `init()`
method (`:67-75`) look like an intended per-reset cleanup that is currently
disabled.

This is primarily a correctness/architecture problem, but it's listed here
because it converts several "bounded per session" issues into "unbounded for the
JVM lifetime."

---

## Quick reference

| ID | Leak | Location | Bound | Severity | Status |
|----|------|----------|-------|----------|--------|
| L1 | Engine/`GroovyClassLoader` never disposed on reload | `ScriptBoard.java:126` | Unbounded (Metaspace) | Critical | Accepted + mitigated operationally (unfixable by a safe local change) |
| L2 | `static clientListeners` never pruned | `EventGraphChangedListener.java:15,35` | Unbounded (heap) | Critical | **Fixed** + tests |
| L3 | polling `clientListeners` never pruned | `IteratedBreadboardGraphChangedListener.java:23,94` | Unbounded (heap) | High | **Fixed** (with L2) |
| L4 | scheduler `Cancellable`s dropped; `static` actor refs overwritten | `FileWatcher.java:47`, `IteratedBreadboardGraphChangedListener.java:29` | Leaked threads/handles | High | Deferred (latent; not unit-testable here) |
| L5 | `UserDataChangeListener` added, never removed | `ScriptBoard.java:497,533` | Compounds L1 | Medium | Subsumed by L1 (no change) |
| L6 | one-shot `Timer`s in `HitCreated` never cancelled | `ScriptBoard.java:375,383` | Per-HIT, unmanaged | Medium | **Fixed** + tests |
| L7 | `admins` / listener admin lists never pruned; `init()` disabled | `ScriptBoard.java:48,220` | Per-user, permanent | Medium | Deferred (needs websocket onClose feature) |
| L8 | per-`eval` `Script` classes accumulate in live engine | `ScriptBoard.java:727,211`; `FileWatcherActor.java:78` | Grows with edits | Medium | Subsumed by L1 (no change) |
| L9 | `static` state shared across per-user actors (amplifier) | `ScriptBoard.java:31-49` vs `Breadboard.java:232` | Makes others process-wide | — | Noted (architectural) |
