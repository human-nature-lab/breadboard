// Tests for recruitment.groovy (the RecruitmentProvider interface + the start/end lifecycle), written
// as Groovy and registered through the `test` DSL bound by ScriptTestHarness. Production's ScriptLoader skips
// any file ending in `_test.groovy`, so this never loads into a real experiment. Run via:
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

// --- providers + start: stamp the source onto the player -----------------------------------------
// Each test uses a fresh `new RecruitmentController(g)` so the provider it installs (and any gate it
// closes) can't leak into another test.

test("start with a Prolific provider stamps _system.recruitment.source = 'prolific'") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  def v = g.addPlayer('rec-prolific-1')
  rc.start(v)
  assert v._system.recruitment.source == 'prolific'
}

test("start with an MTurk provider stamps the source and its sandbox flag") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new MturkProvider(sandbox: true))
  def v = g.addPlayer('rec-mturk-1')
  rc.start(v)
  assert v._system.recruitment.source == 'mturk'
  assert v._system.recruitment.sandbox == true
}

test("start throws when no provider is configured") {
  def rc = new RecruitmentController(g)
  def v = g.addPlayer('rec-noprovider')
  def threw = false
  try { rc.start(v) } catch (Exception e) { threw = true }
  assert threw : 'start without a configured provider must throw'
}

// --- end: completes via the provider recorded at start -------------------------------------------

test("end completes a Prolific participant and records its completion code") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  def v = g.addPlayer('rec-prolific-2')
  rc.start(v)
  rc.end(v, [completionCode: 'CODE123', message: 'Thanks for playing'])
  assert v._system.recruitment.completed == true
  assert v._system.recruitment.completionCode == 'CODE123'
  assert v._system.recruitment.message == 'Thanks for playing'
  assert v._system.recruitment.source == 'prolific'   // unchanged by completion
  assert v._system.status == 'completed'               // study-level lifecycle mirrors completion
}

test("end requires a Prolific completion code") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  def v = g.addPlayer('rec-prolific-3')
  rc.start(v)
  def threw = false
  try { rc.end(v, [:]) } catch (Exception e) { threw = true }
  assert threw : 'end without a completion code must throw for Prolific'
}

test("end throws if the participant was never started (no recorded source)") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  def v = g.addPlayer('rec-prolific-4')
  def threw = false
  try { rc.end(v, [completionCode: 'X']) } catch (Exception e) { threw = true }
  assert threw : 'end before start (no source recorded) must throw'
}

// --- end (MTurk) ---------------------------------------------------------------------------------

test("end completes an MTurk participant and records its bonus") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new MturkProvider())
  def v = g.addPlayer('rec-mturk-2')
  rc.start(v)
  rc.end(v, [bonus: 1.5, reason: 'Good work'])
  assert v._system.recruitment.completed == true
  assert v._system.recruitment.bonus == 1.5
  assert v._system.recruitment.reason == 'Good work'
  assert v._system.status == 'completed'
}

test("end requires an MTurk bonus") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new MturkProvider())
  def v = g.addPlayer('rec-mturk-3')
  rc.start(v)
  def threw = false
  try { rc.end(v, [:]) } catch (Exception e) { threw = true }
  assert threw : 'end without a bonus must throw for MTurk'
}

// --- the recruitment gate: start no-ops once recruitment is stopped -----------------------------

test("start no-ops once recruitment has been stopped (Prolific)") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  rc.stopRecruiting([completionCode: 'DONE'])   // closes the gate (no pending clients yet)
  assert rc.isRecruitmentActive() == false

  def v = g.addPlayer('gate-prolific-1')
  rc.start(v)
  // Gate is closed -> nothing stamped on the player and no client tracked.
  assert v._system?.recruitment == null
  assert rc.clients.find { it.id == 'gate-prolific-1' } == null
}

test("start no-ops once recruitment has been stopped (MTurk)") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new MturkProvider())
  rc.stopRecruiting([bonus: 1.0])
  assert rc.isRecruitmentActive() == false

  def v = g.addPlayer('gate-mturk-1')
  rc.start(v)
  assert v._system?.recruitment == null
}

// --- stopRecruiting: close the gate AND end everyone not yet finished ----------------------------

test("stopRecruiting completes still-pending Prolific participants and closes the gate") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  def v1 = g.addPlayer('stop-prolific-1')
  def v2 = g.addPlayer('stop-prolific-2')
  rc.start(v1)
  rc.start(v2)
  assert rc.clients.size() == 2

  rc.stopRecruiting([completionCode: 'FINAL', message: 'All done'])

  assert rc.isRecruitmentActive() == false
  assert v1._system.recruitment.completed == true
  assert v1._system.recruitment.completionCode == 'FINAL'
  assert v2._system.recruitment.completed == true
  assert rc.clients.every { it.state == 'completed' }
}

test("stopRecruiting surfaces a provider's missing completion opts via end") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new ProlificProvider())
  def v = g.addPlayer('stop-needs-code')
  rc.start(v)
  def threw = false
  try { rc.stopRecruiting([:]) } catch (Exception e) { threw = true }
  assert threw : 'stopRecruiting with a pending Prolific participant but no completion code must throw'
}

test("stopRecruiting completes still-pending MTurk participants and preserves sandbox") {
  def rc = new RecruitmentController(g)
  rc.setProvider(new MturkProvider(sandbox: true))
  def v1 = g.addPlayer('stop-mturk-1')
  rc.start(v1)

  rc.stopRecruiting([bonus: 2.0, reason: 'wrap up'])

  assert rc.isRecruitmentActive() == false
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
  assert rc.completedGames == 0            // abandoned, not completed
  assert rc.clients.find { it.id == "ab1" }.gameId == null

  rc.gameAbandoned("abGame")               // second call is a no-op
  assert rc.completedGames == 0
}
