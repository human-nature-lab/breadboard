// Tests for recruitment.groovy (the prolific/mturk register + complete helpers), written as Groovy
// and registered through the `test` DSL bound by ScriptTestHarness. Production's ScriptLoader skips
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

// --- register: stamps the source onto the player -------------------------------------------------

test("registerProlific stamps _system.recruitment.source = 'prolific'") {
  def v = g.addPlayer('rec-prolific-1')
  recruitment.registerProlific(v)
  assert v._system.recruitment.source == 'prolific'
}

test("registerMturk stamps _system.recruitment.source = 'mturk'") {
  def v = g.addPlayer('rec-mturk-1')
  recruitment.registerMturk(v)
  assert v._system.recruitment.source == 'mturk'
}

// --- completeProlific ----------------------------------------------------------------------------

test("completeProlific marks the player completed and records its completion code") {
  def v = g.addPlayer('rec-prolific-2')
  recruitment.registerProlific(v)
  recruitment.completeProlific(v, [completionCode: 'CODE123', message: 'Thanks for playing'])
  assert v._system.recruitment.completed == true
  assert v._system.recruitment.completionCode == 'CODE123'
  assert v._system.recruitment.message == 'Thanks for playing'
  assert v._system.recruitment.source == 'prolific'   // unchanged by completion
}

test("completeProlific requires a completion code") {
  def v = g.addPlayer('rec-prolific-3')
  recruitment.registerProlific(v)
  def threw = false
  try { recruitment.completeProlific(v, [:]) } catch (Exception e) { threw = true }
  assert threw : 'completeProlific without a completion code must throw'
}

test("completeProlific refuses a non-prolific player") {
  def v = g.addPlayer('rec-prolific-4')
  recruitment.registerMturk(v)
  def threw = false
  try { recruitment.completeProlific(v, [completionCode: 'X']) } catch (Exception e) { threw = true }
  assert threw : 'completeProlific on an mturk player must throw'
}

// --- completeMturk -------------------------------------------------------------------------------

test("completeMturk marks the player completed and records its bonus") {
  def v = g.addPlayer('rec-mturk-2')
  recruitment.registerMturk(v)
  recruitment.completeMturk(v, [bonus: 1.5, reason: 'Good work'])
  assert v._system.recruitment.completed == true
  assert v._system.recruitment.bonus == 1.5
  assert v._system.recruitment.reason == 'Good work'
}

test("completeMturk requires a bonus") {
  def v = g.addPlayer('rec-mturk-3')
  recruitment.registerMturk(v)
  def threw = false
  try { recruitment.completeMturk(v, [:]) } catch (Exception e) { threw = true }
  assert threw : 'completeMturk without a bonus must throw'
}

test("completeMturk refuses a non-mturk player") {
  def v = g.addPlayer('rec-mturk-4')
  recruitment.registerProlific(v)
  def threw = false
  try { recruitment.completeMturk(v, [bonus: 1.0]) } catch (Exception e) { threw = true }
  assert threw : 'completeMturk on a prolific player must throw'
}

// --- the recruitment gate: register no-ops once recruitment is stopped --------------------------
// These use a FRESH `new RecruitmentController(g)` rather than the shared global `recruitment`, so
// flipping the gate here can't leak into the register/complete tests above.

test("registerProlific no-ops once recruitment has been stopped") {
  def rc = new RecruitmentController(g)
  rc.stopRecruitingProlific([completionCode: 'DONE'])   // closes the gate (no pending clients yet)
  assert rc.isRecruitmentActive() == false

  def v = g.addPlayer('gate-prolific-1')
  rc.registerProlific(v)
  // Gate is closed -> nothing stamped on the player and no client tracked.
  assert v._system?.recruitment == null
  assert rc.clients.find { it.id == 'gate-prolific-1' } == null
}

test("registerMturk no-ops once recruitment has been stopped") {
  def rc = new RecruitmentController(g)
  rc.stopRecruitingMturk([bonus: 1.0])
  assert rc.isRecruitmentActive() == false

  def v = g.addPlayer('gate-mturk-1')
  rc.registerMturk(v)
  assert v._system?.recruitment == null
}

// --- stopRecruiting*: close the gate AND complete everyone not yet finished ---------------------

test("stopRecruitingProlific completes still-pending prolific players and closes the gate") {
  def rc = new RecruitmentController(g)
  def v1 = g.addPlayer('stop-prolific-1')
  def v2 = g.addPlayer('stop-prolific-2')
  rc.registerProlific(v1)
  rc.registerProlific(v2)
  assert rc.clients.size() == 2

  rc.stopRecruitingProlific([completionCode: 'FINAL', message: 'All done'])

  assert rc.isRecruitmentActive() == false
  assert v1._system.recruitment.completed == true
  assert v1._system.recruitment.completionCode == 'FINAL'
  assert v2._system.recruitment.completed == true
  assert rc.clients.every { it.state == 'completed' }
}

test("stopRecruitingProlific requires a completion code") {
  def rc = new RecruitmentController(g)
  def threw = false
  try { rc.stopRecruitingProlific([:]) } catch (Exception e) { threw = true }
  assert threw : 'stopRecruitingProlific without a completion code must throw'
}

test("stopRecruitingMturk completes still-pending mturk players and closes the gate") {
  def rc = new RecruitmentController(g)
  def v1 = g.addPlayer('stop-mturk-1')
  rc.registerMturk(v1, [sandbox: true])

  rc.stopRecruitingMturk([bonus: 2.0, reason: 'wrap up'])

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
