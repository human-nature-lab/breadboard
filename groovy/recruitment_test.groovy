// Tests for recruitment.groovy (the RecruitmentProvider hooks + the admit/complete lifecycle),
// written as Groovy and registered through the `test` DSL bound by ScriptTestHarness. Production's
// ScriptLoader skips any file ending in `_test.groovy`, so this never loads into a real experiment.
// Run via:
//   sbt -java-home "$JAVA8_HOME" "testOnly GroovyScriptTests"
//
//   test("name") { ... }                            -> synchronous
//   test.async("name", timeoutMs) { done -> ... }   -> async; signal completion with done()
//   test.skip("name") { ... }                       -> reported as ignored, not run
//
// `g`, `a`, `events` etc. are the same engine bindings the platform scripts see; `recruitment` and
// `RecruitmentSource` come from recruitment.groovy (a core script, already loaded). `_system` reads
// (`v._system...`) and `v.id` are safe; only `v.<gremlin-pipe-method>` reads (e.g. `v.step`) throw.
//
// Groovy 1.8.6 target: no closure->functional-interface coercion, no lambdas / `::` refs.

// --- RecruitmentSource: the wire contract --------------------------------------------------------

test("RecruitmentSource exposes the exact lowercase strings the frontend compares against") {
  // Replaced the old enum with a plain string map precisely so these values are unambiguous on the
  // wire (BBMain.vue checks recruitment.source === 'prolific' / 'mturk').
  assert RecruitmentSource.PROLIFIC == 'prolific'
  assert RecruitmentSource.MTURK == 'mturk'
}

// --- provider + admit: stamp the source onto the player ------------------------------------------
// Each test uses a fresh `new RecruitmentController(g)` so the provider it installs (and any gate it
// pauses) can't leak into another test.

test("admit with a Prolific provider stamps _system.recruitment.source = 'prolific'") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  def v = g.addPlayer('rec-prolific-1')
  rc.admit(v)
  assert v._system.recruitment.source == 'prolific'
}

test("admit with an MTurk provider stamps the source and its sandbox flag") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new MturkProvider(sandbox: true))
  def v = g.addPlayer('rec-mturk-1')
  rc.admit(v)
  assert v._system.recruitment.source == 'mturk'
  assert v._system.recruitment.sandbox == true
}

test("admit throws when no provider is configured") {
  def rc = new RecruitmentController(g)
  def v = g.addPlayer('rec-noprovider')
  def threw = false
  try { rc.admit(v) } catch (Exception e) { threw = true }
  assert threw : 'admit without a configured provider must throw'
}

// --- complete: completes via the configured provider ---------------------------------------------

test("complete completes a Prolific participant and records its completion code") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  def v = g.addPlayer('rec-prolific-2')
  rc.admit(v)
  rc.complete(v, [completionCode: 'CODE123', message: 'Thanks for playing'])
  assert v._system.recruitment.completed == true
  assert v._system.recruitment.completionCode == 'CODE123'
  assert v._system.recruitment.message == 'Thanks for playing'
  assert v._system.recruitment.source == 'prolific'   // unchanged by completion
  assert v._system.status == 'completed'               // study-level lifecycle mirrors completion
}

test("complete requires a Prolific completion code") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  def v = g.addPlayer('rec-prolific-3')
  rc.admit(v)
  def threw = false
  try { rc.complete(v, [:]) } catch (Exception e) { threw = true }
  assert threw : 'complete without a completion code must throw for Prolific'
}

test("complete throws if the participant was never admitted (no recorded source)") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  def v = g.addPlayer('rec-prolific-4')
  def threw = false
  try { rc.complete(v, [completionCode: 'X']) } catch (Exception e) { threw = true }
  assert threw : 'complete before admit (no source recorded) must throw'
}

// --- complete (MTurk) ----------------------------------------------------------------------------

test("complete completes an MTurk participant and records its bonus") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new MturkProvider())
  def v = g.addPlayer('rec-mturk-2')
  rc.admit(v)
  rc.complete(v, [bonus: 1.5, reason: 'Good work'])
  assert v._system.recruitment.completed == true
  assert v._system.recruitment.bonus == 1.5
  assert v._system.recruitment.reason == 'Good work'
  assert v._system.status == 'completed'
}

test("complete requires an MTurk bonus") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new MturkProvider())
  def v = g.addPlayer('rec-mturk-3')
  rc.admit(v)
  def threw = false
  try { rc.complete(v, [:]) } catch (Exception e) { threw = true }
  assert threw : 'complete without a bonus must throw for MTurk'
}

// --- the admission gate: admit no-ops while admission is paused ----------------------------------

test("admit no-ops while admission is paused (Prolific)") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  rc.pauseAdmission()
  assert rc.isAdmitting() == false

  def v = g.addPlayer('gate-prolific-1')
  rc.admit(v)
  // Gate is closed -> nothing stamped on the player and no client tracked.
  assert v._system?.recruitment == null
  assert rc.clients.find { it.id == 'gate-prolific-1' } == null
}

test("admit no-ops while admission is paused (MTurk)") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new MturkProvider())
  rc.pauseAdmission()
  assert rc.isAdmitting() == false

  def v = g.addPlayer('gate-mturk-1')
  rc.admit(v)
  assert v._system?.recruitment == null
}

test("resumeAdmission re-opens the gate after pauseAdmission") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())

  rc.pauseAdmission()
  def gated = g.addPlayer('resume-gated')
  rc.admit(gated)
  assert gated._system?.recruitment == null      // paused -> not admitted

  rc.resumeAdmission()
  assert rc.isAdmitting() == true
  def admitted = g.addPlayer('resume-admitted')
  rc.admit(admitted)
  assert admitted._system.recruitment.source == 'prolific'   // admitted once re-opened
}

test("closeAdmission permanently stops admission and resumeAdmission cannot re-open it") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())

  rc.closeAdmission()
  assert rc.isAdmitting() == false
  def v1 = g.addPlayer('close-1')
  rc.admit(v1)
  assert v1._system?.recruitment == null          // closed -> not admitted

  rc.resumeAdmission()                              // must NOT re-open a closed gate
  assert rc.isAdmitting() == false
  def v2 = g.addPlayer('close-2')
  rc.admit(v2)
  assert v2._system?.recruitment == null
}

// --- completeAll: complete everyone not yet finished (does NOT touch the gate) -------------------

test("completeAll completes still-pending Prolific participants and leaves the gate open") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  def v1 = g.addPlayer('stop-prolific-1')
  def v2 = g.addPlayer('stop-prolific-2')
  rc.admit(v1)
  rc.admit(v2)
  assert rc.clients.size() == 2

  rc.completeAll([completionCode: 'FINAL', message: 'All done'])

  assert rc.isAdmitting() == true     // completeAll is orthogonal to the admission gate
  assert v1._system.recruitment.completed == true
  assert v1._system.recruitment.completionCode == 'FINAL'
  assert v2._system.recruitment.completed == true
  assert rc.clients.every { it.state == 'completed' }
}

test("completeAll surfaces a provider's missing completion opts via complete") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  def v = g.addPlayer('stop-needs-code')
  rc.admit(v)
  def threw = false
  try { rc.completeAll([:]) } catch (Exception e) { threw = true }
  assert threw : 'completeAll with a pending Prolific participant but no completion code must throw'
}

test("completeAll completes still-pending MTurk participants and preserves sandbox") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new MturkProvider(sandbox: true))
  def v1 = g.addPlayer('stop-mturk-1')
  rc.admit(v1)

  rc.completeAll([bonus: 2.0, reason: 'wrap up'])

  assert v1._system.recruitment.completed == true
  assert v1._system.recruitment.bonus == 2.0
  assert v1._system.recruitment.sandbox == true
}

// --- client / game lifecycle (migrated here from waiting_room_test) -----------------------------
// The controller used to live in waiting_room.groovy; it now backs the lobby's tracking too.

test("clientPending/Waiting/Completed track a client through its lifecycle without duplicating it") {
  def rc = new RecruitmentController(g)
  rc.clientPending("c1")
  assert rc.clients.find { it.id == "c1" }.state == "pending"
  rc.clientWaiting("c1")
  assert rc.clients.find { it.id == "c1" }.state == "waiting"
  rc.clientCompleted("c1")                  // regression: this method previously didn't exist
  assert rc.clients.find { it.id == "c1" }.state == "completed"
  assert rc.clients.size() == 1            // same client updated in place (no duplicate)
}

test("removeClient marks the client removed and the vertex _system.status = 'dropped'") {
  def rc = new RecruitmentController(g)
  def v = g.addPlayer('rm1')
  rc.clientWaiting('rm1')
  rc.removeClient('rm1')
  assert rc.clients.find { it.id == 'rm1' }.state == 'removed'
  assert v._system.status == 'dropped'     // study-level lifecycle mirrors the controller removal
}

test("gameStarted/gameCompleted move clients and count completed games") {
  def rc = new RecruitmentController(g)
  rc.clientWaiting("g1")
  rc.clientWaiting("g2")

  rc.gameStarted("game-1", ["g1", "g2"])
  assert rc.clients.find { it.id == "g1" }.state == "active"
  assert rc.clients.find { it.id == "g1" }.gameId == "game-1"
  assert rc.activeGames.containsKey("game-1")

  rc.gameCompleted("game-1")
  assert rc.clients.find { it.id == "g1" }.state == "completed"
  assert rc.clients.find { it.id == "g1" }.gameId == null
  assert !rc.activeGames.containsKey("game-1")
  assert rc.completedGames == 1
}

test("canStartGame is false once maxSimultaneousGames are active") {
  def rc = new RecruitmentController(g)
  rc.maxSimultaneousGames = 2
  rc.clientWaiting("ca")
  rc.clientWaiting("cb")
  assert rc.canStartGame() == true
  rc.gameStarted("ga", ["ca"])
  rc.gameStarted("gb", ["cb"])
  assert rc.canStartGame() == false        // two active games == the limit
  rc.gameCompleted("ga")
  assert rc.canStartGame() == true         // one freed up
}

// --- game-lifecycle idempotency (so the lobby AND the Games registry can both report) -----------

test("gameStarted is idempotent: a repeat call keeps startedGameAt and doesn't re-activate twice") {
  def rc = new RecruitmentController(g)
  rc.clientWaiting("idemS1")
  rc.gameStarted("idemSGame", ["idemS1"])
  def firstStartedAt = rc.clients.find { it.id == "idemS1" }.startedGameAt
  assert firstStartedAt != null
  rc.gameStarted("idemSGame", ["idemS1"])  // e.g. WaitingRoom.gameStarted then Games.create
  assert rc.clients.find { it.id == "idemS1" }.startedGameAt.is(firstStartedAt)   // not overwritten
  assert rc.activeGames.size() == 1        // still one active game, not two
}

test("gameCompleted is idempotent: a repeat call does not double-count") {
  def rc = new RecruitmentController(g)
  rc.clientWaiting("idem1")
  rc.gameStarted("idemGame", ["idem1"])
  rc.gameCompleted("idemGame")
  rc.gameCompleted("idemGame")             // e.g. WaitingRoom.groupCompleted AND Game.finish
  assert rc.completedGames == 1            // counted exactly once
  assert !rc.activeGames.containsKey("idemGame")
}

test("gameAbandoned frees the slot without counting, and is idempotent") {
  def rc = new RecruitmentController(g)
  rc.clientWaiting("ab1")
  rc.gameStarted("abGame", ["ab1"])
  assert rc.activeGames.containsKey("abGame")

  rc.gameAbandoned("abGame")
  assert !rc.activeGames.containsKey("abGame")
  assert rc.completedGames == 0            // abandoned, not counted
  assert rc.clients.find { it.id == "ab1" }.gameId == null

  rc.gameAbandoned("abGame")               // second call is a no-op
  assert rc.completedGames == 0
}

// --- kick timer: optional post-completion redirect -----------------------------------------------
// complete(..., kickAfter: seconds) schedules a timer that, on expiry, stamps the completion code onto
// v.immediatelySubmitCode -- the field the client watches (registerForceSubmitEvent, gated by the
// forceSubmit opt) to force a redirect to the Prolific submit URL. `v.timers` / `v.immediatelySubmit-
// Code` are plain top-level vertex props (not gremlin-pipe names), so reading them here is safe.

test.async("complete with kickAfter stamps immediatelySubmitCode onto the vertex after the delay", 4000) { done ->
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  def v = g.addPlayer('kick-prolific-1')
  rc.admit(v)
  rc.complete(v, [completionCode: 'KICKCODE', kickAfter: 0.1])   // 100ms
  assert v.immediatelySubmitCode == null    // not stamped synchronously -- the timer hasn't fired yet
  timers.newTimer().runAfter(700) {
    done {
      assert v.immediatelySubmitCode == 'KICKCODE'
    }
  }
}

test("complete without kickAfter schedules no kick timer and leaves immediatelySubmitCode unset") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  def v = g.addPlayer('kick-prolific-2')
  rc.admit(v)
  rc.complete(v, [completionCode: 'NOKICK'])
  assert v.immediatelySubmitCode == null
  assert v.timers == null || v.timers.isEmpty()
}

test("complete with kickAfter <= 0 schedules no kick timer") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  def v = g.addPlayer('kick-prolific-3')
  rc.admit(v)
  rc.complete(v, [completionCode: 'ZERO', kickAfter: 0])
  assert v.immediatelySubmitCode == null
  assert v.timers == null || v.timers.isEmpty()
}

test("kickAfter on an MTurk completion is skipped (no completion code to redirect with)") {
  // The immediatelySubmitCode redirect is Prolific-specific: without a completion code there is nothing
  // to build ?cc=<code> from, so _scheduleKick bails rather than sending the participant to ?cc=null.
  // (Also confirms kickAfter is stripped before the provider -- MTurkCompleteOpts has no such field.)
  def rc = new RecruitmentController(g)
  rc.setProvider(new MturkProvider())
  def v = g.addPlayer('kick-mturk-1')
  rc.admit(v)
  rc.complete(v, [bonus: 1.0, kickAfter: 0.1])
  assert v._system.recruitment.completionCode == null
  assert v.immediatelySubmitCode == null
  assert v.timers == null || v.timers.isEmpty()
}
