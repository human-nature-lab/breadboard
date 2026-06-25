// Tests for groups.groovy (the Game/Games model), written as Groovy and registered through the
// `test` DSL bound by ScriptTestHarness. Production's ScriptLoader skips any file ending in
// `_test.groovy`, so this never loads into a real experiment. Run via:
//   sbt -java-home <jdk8> "testOnly GroovyScriptTests"
//
//   test("name") { ... }                            -> synchronous
//   test.async("name", timeoutMs) { done -> ... }   -> async; signal completion with done()
//   test.skip("name") { ... }                       -> reported as ignored, not run
//
// `g`, `a`, `events`, `timers`, `results` etc. are the same engine bindings the platform scripts
// see; Games / Game / GroupContext come from groups.groovy, already loaded (it's a core script).
//
// Groovy 1.8.9 target: no closure->functional-interface coercion, no lambdas / `::` refs.

import java.util.concurrent.atomic.AtomicInteger

// A sample experiment parameters object. @Immutable makes every property final, so writing one
// throws ReadOnlyPropertyException and reading an unknown one throws MissingPropertyException --
// which is exactly the read-only contract `Game.parameters` is meant to preserve.
@groovy.transform.Immutable
class SampleParams {
  int rounds
  String mode
}

// A 3-variable params object for the factorial builder test (the recommended pattern: an immutable
// object so the per-treatment parameters can't be mutated by a game and corrupt its condition).
@groovy.transform.Immutable
class ABC {
  int a
  int b
  double c
}

// Simulate a player picking their queued group choice. This drives the real client path: a
// CustomEvent on the bus -> events.groovy routes it to the player-scoped listener that Game.ask
// installed. The uid is passed in (not read from p.choices here) so callers can read it on the
// main thread -- reading the choice map from a timer thread races with the thread that wrote it.
def submit = { p, uid ->
  events.emit("CustomEvent",
    [playerId: p.id, eventName: Games.SUBMIT_EVENT, data: [uid: uid]],
    [clientId: p.id])
}

// A reusable single-step game definition: every active player gets one decision with a single
// option, and the step's `done` bumps the supplied counter when the pending queue drains.
def defineCounterGame = { AtomicInteger doneCount ->
  Games.define { game ->
    game.step('trial', [
      run:  { game.players.each { p -> game.ask(p, [name: 'go', result: { v, data -> }]) } },
      done: { doneCount.incrementAndGet() },
    ])
  }
}

test("done fires once when every queued ask is submitted") {
  def doneCount = new AtomicInteger(0)
  defineCounterGame(doneCount)
  def p1 = g.addPlayer('p1')
  def p2 = g.addPlayer('p2')
  def game = Games.create('grp1', [p1, p2])
  game.go('trial')

  assert game.pendingCount() == 2          // both players have a pending choice
  submit(p1, p1.choices[0].uid)
  assert game.pendingCount() == 1          // one resolved, step not done yet
  assert doneCount.get() == 0
  submit(p2, p2.choices[0].uid)
  assert game.pendingCount() == 0          // queue empty -> done should have fired
  assert doneCount.get() == 1
}

// Two games in the same engine must not share a pending queue, and resolving one must not fire
// the other's done.
test("two games keep independent queues; resolving one fires done only for that game") {
  def doneIds = []
  Games.define { game ->
    game.step('trial', [
      run:  { game.players.each { p -> game.ask(p, [name: 'go', result: { v, data -> }]) } },
      done: { doneIds << game.id },
    ])
  }
  def a1 = g.addPlayer('iso-a1')
  def a2 = g.addPlayer('iso-a2')
  def b1 = g.addPlayer('iso-b1')
  def gA = Games.create('grpIsoA', [a1, a2])
  def gB = Games.create('grpIsoB', [b1])
  gA.go('trial')
  gB.go('trial')

  assert gA.pendingCount() == 2
  assert gB.pendingCount() == 1

  submit(a1, a1.choices[0].uid)
  submit(a2, a2.choices[0].uid)            // game A fully resolved

  assert gA.pendingCount() == 0
  assert gB.pendingCount() == 1            // game B's queue is untouched by A's resolution
  assert doneIds == ['grpIsoA']            // ...and only A's step completed
}

// The defining feature: ONE ask presents MULTIPLE mutually-exclusive options (the a.add model).
// Every option is a button; picking ANY option resolves the single decision -- it runs that
// option's OWN result closure and clears ALL of the player's buttons for that decision. This is the
// prisoner's-dilemma case the old single-choice ask could not express.
test("a multi-option ask is one decision: picking an option runs its result and clears the buttons") {
  def picked = []
  def doneCount = new AtomicInteger(0)
  Games.define { game ->
    game.step('trial', [
      run: { game.players.each { p ->
        game.ask(p,
          [name: 'Cooperate', result: { v, data -> picked << [id: v.id, choice: 'C'] }],
          [name: 'Defect',    result: { v, data -> picked << [id: v.id, choice: 'D'] }])
      } },
      done: { doneCount.incrementAndGet() },
    ])
  }
  def p1 = g.addPlayer('mc1')
  def p2 = g.addPlayer('mc2')
  def game = Games.create('grpMC', [p1, p2])
  game.go('trial')

  assert game.pendingCount() == 2                          // one decision per player...
  assert p1.choices.size() == 2                            // ...each showing BOTH option buttons
  assert p2.choices.size() == 2

  // p1 cooperates (picks the first button), p2 defects (picks the second).
  def coopUid   = p1.choices.find { it.name == 'Cooperate' }.uid
  def defectUid = p2.choices.find { it.name == 'Defect' }.uid

  submit(p1, coopUid)
  assert game.pendingCount() == 1                          // p1's single decision resolved...
  assert p1.choices.size() == 0                            // ...and BOTH of p1's buttons were cleared
  assert doneCount.get() == 0                              // p2 still outstanding -> step not done

  submit(p2, defectUid)
  assert game.pendingCount() == 0
  assert p2.choices.size() == 0
  assert doneCount.get() == 1                              // step drained -> done fired exactly once
  assert picked.sort { it.id } == [[id: 'mc1', choice: 'C'], [id: 'mc2', choice: 'D']]
}

// After a decision resolves, a stale submit naming the OTHER (already-cleared) option must be a
// no-op: the decision is gone from the pending set, so its result must not run a second time.
test("a second submit for an already-resolved decision is ignored") {
  def resultCount = new AtomicInteger(0)
  Games.define { game ->
    game.step('trial', [
      run: { game.players.each { p ->
        game.ask(p,
          [name: 'Cooperate', result: { v, data -> resultCount.incrementAndGet() }],
          [name: 'Defect',    result: { v, data -> resultCount.incrementAndGet() }])
      } },
      done: { },
    ])
  }
  def p1 = g.addPlayer('stale1')
  def game = Games.create('grpStale', [p1])
  game.go('trial')

  def coopUid   = p1.choices.find { it.name == 'Cooperate' }.uid
  def defectUid = p1.choices.find { it.name == 'Defect' }.uid
  submit(p1, coopUid)
  submit(p1, defectUid)                                    // stale: the decision already resolved

  assert resultCount.get() == 1                            // exactly one option's result ran
  assert game.pendingCount() == 0
}

test("ask before any go() throws") {
  def p1 = g.addPlayer('pre1')
  def game = Games.create('grpPre', [p1])
  // No step is current yet -> ask() must refuse to queue a decision.
  def threw = false
  try { game.ask(p1, [name: 'x', result: { v, data -> }]) } catch (IllegalStateException e) { threw = true }
  assert threw : 'ask() before a step is current must throw'
}

test("go() on an unknown step throws") {
  def game = Games.create('grpUnknown', [g.addPlayer('unk1')])
  def threw = false
  try { game.go('nope') } catch (IllegalArgumentException e) { threw = true }
  assert threw : 'go() on a step that was never defined must throw'
}

test("step() rejects a duplicate step name") {
  def game = Games.create('grpDup', [g.addPlayer('dup1')])
  game.step('trial', [run: { }, done: { }])
  def threw = false
  try { game.step('trial', [run: { }, done: { }]) } catch (IllegalStateException e) { threw = true }
  assert threw : 'defining the same step name twice must throw'
}

// Dropping a player drains their pending ask and, if that empties the step's queue, completes the
// step -- exactly as if they had submitted. drop also sets _system.status='dropped' so `players` excludes them.
test("dropping a player drains their ask, completes the step, and excludes them from players") {
  def doneCount = new AtomicInteger(0)
  defineCounterGame(doneCount)
  def p1 = g.addPlayer('drp1')
  def p2 = g.addPlayer('drp2')
  def game = Games.create('grpDrop', [p1, p2])
  game.go('trial')

  assert game.pendingCount() == 2
  submit(p1, p1.choices[0].uid)            // p1 answers normally (their decision's only option)
  assert game.pendingCount() == 1          // p2 still outstanding...
  assert doneCount.get() == 0              // ...so done has NOT fired yet

  game.drop(p2)                            // p2 drops; their queued choice is drained
  assert game.pendingCount() == 0          // queue now empty
  assert doneCount.get() == 1              // ...so the step completed exactly once
  assert p2._system.status == 'dropped'    // drop set the terminal study-level status
  assert !game.players.contains(p2)        // ...so players excludes the dropped member
  assert game.players.contains(p1)
}

// `parameters` holds an opaque, read-only object. Using an @Immutable params class, writing a
// property throws ReadOnlyPropertyException and an unknown property throws MissingPropertyException.
test("parameters is read-only and rejects unknown keys") {
  def params = new SampleParams(3, 'pgg')   // @Immutable positional constructor: (rounds, mode)
  def game = Games.create('grpParams', [], params)

  assert game.parameters.rounds == 3
  assert game.parameters.mode == 'pgg'

  def threwReadOnly = false
  try { game.parameters.rounds = 5 } catch (groovy.lang.ReadOnlyPropertyException e) { threwReadOnly = true }
  assert threwReadOnly : 'writing an @Immutable parameter must throw ReadOnlyPropertyException'

  def threwMissing = false
  try { game.parameters.nope } catch (groovy.lang.MissingPropertyException e) { threwMissing = true }
  assert threwMissing : 'reading an unknown parameter must throw MissingPropertyException'
}

// An AI player (player.ai == 1) auto-resolves its own decision: Game.ask schedules a submit on a
// timer, which emits the same CustomEvent a real client would. Async because the AI fires off-thread.
test.async("an AI player auto-resolves its decision", 4000) { done ->
  def doneCount = new AtomicInteger(0)
  Games.define { game ->
    game.step('trial', [
      run:  { game.players.each { p -> game.ask(p, [name: 'go', result: { v, data -> }]) } },
      done: { doneCount.incrementAndGet(); done { assert doneCount.get() == 1 } },
    ])
  }
  def game = Games.create('grpAI', [])
  game.addAI(1)                            // fill the cohort to 1 player with AI
  check { assert game.players.size() == 1 }
  game.go('trial')                         // asks the AI; it auto-submits after a short delay
}

// An AI faced with a MULTI-OPTION decision picks one option at random and resolves the single
// decision -- so the step drains and done fires exactly once regardless of which option it lands on.
test.async("an AI resolves a multi-option decision by picking one option", 4000) { done ->
  def doneCount = new AtomicInteger(0)
  def resultCount = new AtomicInteger(0)
  Games.define { game ->
    game.step('trial', [
      run:  { game.players.each { p ->
        game.ask(p,
          [name: 'Cooperate', result: { v, data -> resultCount.incrementAndGet() }],
          [name: 'Defect',    result: { v, data -> resultCount.incrementAndGet() }])
      } },
      done: { doneCount.incrementAndGet(); done {
        assert doneCount.get() == 1            // the one decision drained the step exactly once...
        assert resultCount.get() == 1          // ...running exactly one option's result
      } },
    ])
  }
  def game = Games.create('grpAIChoice', [])
  game.addAI(1)
  check { assert game.players.size() == 1 }
  game.go('trial')
}

// An AI asked MORE THAN ONCE in a single step (two separate decisions) must auto-resolve EVERY
// decision: each ask drives a submit for one of its OWN option uids, so the step's pending queue
// drains and done fires exactly once. The two decisions also resolve off two separate timer
// threads, exercising the concurrent-resolve path that must fire done exactly once.
test.async("an AI asked multiple times in one step resolves every decision", 4000) { done ->
  def doneCount = new AtomicInteger(0)
  def resultCount = new AtomicInteger(0)
  Games.define { game ->
    game.step('trial', [
      run:  { game.players.each { p ->
        game.ask(p, [name: 'q1', result: { v, data -> resultCount.incrementAndGet() }])
        game.ask(p, [name: 'q2', result: { v, data -> resultCount.incrementAndGet() }])
      } },
      done: { doneCount.incrementAndGet(); done {
        assert doneCount.get() == 1            // step completed exactly once...
        assert resultCount.get() == 2          // ...with both decisions answered
      } },
    ])
  }
  def game = Games.create('grpAIMulti', [])
  game.addAI(1)
  check { assert game.players.size() == 1 }
  game.go('trial')
}

// With drop enabled and short warn/drop times, a player who never responds is dropped after the
// warn->drop sequence; dropping the only player empties the cohort and abandons the game.
test.async("idle/drop drops a non-responder and abandons the emptied game", 6000) { done ->
  def abandonCount = new AtomicInteger(0)
  Games.define { game ->
    game.setDropPlayers(true)
    game.setWarnTime(0.05)                 // 50ms warn
    game.setDropTime(0.05)                 // then 50ms to drop
    game.onAbandon { abandonCount.incrementAndGet(); done { assert abandonCount.get() == 1 } }
    game.step('trial', [
      run:  { game.players.each { p -> game.ask(p, [name: 'go', result: { v, data -> }]) } },
      done: { },                           // never reached: the lone player is dropped, not resolved
    ])
  }
  def p1 = g.addPlayer('idle1')
  def game = Games.create('grpIdle', [p1])
  game.go('trial')                         // p1 never submits -> warn -> drop -> abandon
}

// A full define -> create -> multi-round loop: each round asks both players; when both submit, the
// step's done advances the round and either loops (go again) or finishes. onFinish must fire once.
// Fully synchronous: every submit drives resolve -> done -> go -> run -> ask on the same thread.
test("a multi-round define/create loop fires onFinish exactly once and disposes the game") {
  def finishCount = new AtomicInteger(0)
  def rounds = 3
  Games.define { game ->
    game.state.round = 0
    game.onFinish { finishCount.incrementAndGet() }
    game.step('round', [
      run:  { game.players.each { p -> game.ask(p, [name: 'go', result: { v, data -> }]) } },
      done: {
        game.state.round = game.state.round + 1
        if (game.state.round >= rounds) {
          game.finish()
        } else {
          game.go('round')                 // next round: re-ask everyone
        }
      },
    ])
  }
  def p1 = g.addPlayer('loop1')
  def p2 = g.addPlayer('loop2')
  def game = Games.create('grpLoop', [p1, p2])
  game.go('round')

  rounds.times {
    submit(p1, p1.choices[0].uid)          // fresh uids each round (prior choices were unassigned)
    submit(p2, p2.choices[0].uid)
  }

  assert finishCount.get() == 1            // onFinish fired exactly once
  assert Games.get('grpLoop') == null      // finish() disposed and unregistered the game
  assert p1._system.groupId == null        // ...and untagged its players
  assert p2._system.groupId == null
}

// game.a wraps the global action queue's event tracking so every group event is tagged with the
// game's id (and current step), without clobbering caller-supplied keys; other calls forward to
// the global `a`. We stand in a recording double for `a` because the harness's real eventTracker is
// disabled and has no ExperimentInstance, so nothing would actually persist to assert on.
test("game.a.addEvent tags events with the group id and current step") {
  def recorded = []
  // GroupContext.a is a JVM-static field shared across every test case; swap in a recording
  // double, but restore the real `a` in a finally so later cases (and addAI, which reads
  // GroupContext.a) aren't left pointing at the double regardless of test execution order.
  def realA = GroupContext.a
  GroupContext.a = new Expando(addEvent: { String name, data -> recorded << [name: name, data: data] })
  try {
    def game = Games.create('grpEvt', [g.addPlayer('evt1')])
    game.step('play', [run: { }, done: { }])
    game.go('play')

    game.a.addEvent('scored', [points: 5])
    assert recorded.size() == 1
    assert recorded[0].name == 'scored'
    assert recorded[0].data.groupId == 'grpEvt'        // tagged with the game id
    assert recorded[0].data.step == 'play'             // ...and the current step
    assert recorded[0].data.points == 5                // caller data preserved

    game.a.addEvent('scored', [groupId: 'explicit'])   // a caller-supplied groupId is not clobbered
    assert recorded[1].data.groupId == 'explicit'
  } finally {
    GroupContext.a = realA
  }
}

// a.add parity for ask options: a leading init closure runs once before the buttons are shown, and
// the CHOSEN option's `event` (and only that one) is tracked via game.a.addEvent (so it's tagged
// with the group id), while the option's `result` still runs.
test("ask runs the leading init closure and tracks only the chosen option's event") {
  def initRan = new AtomicInteger(0)
  def resultRan = new AtomicInteger(0)
  def recorded = []
  // Swap in a recording double for the static GroupContext.a (the harness eventTracker is disabled),
  // restoring the real `a` in a finally so later cases aren't left pointing at the double.
  def realA = GroupContext.a
  GroupContext.a = new Expando(addEvent: { String name, data -> recorded << [name: name, data: data] })
  try {
    Games.define { game ->
      game.step('trial', [
        run: { game.players.each { p ->
          game.ask(p, { initRan.incrementAndGet() },
            [name: 'Cooperate', event: [name: 'chose', data: [opt: 'C']], result: { v, data -> resultRan.incrementAndGet() }],
            [name: 'Defect',    event: [name: 'chose', data: [opt: 'D']], result: { v, data -> resultRan.incrementAndGet() }])
        } },
        done: { },
      ])
    }
    def p1 = g.addPlayer('ie1')
    def game = Games.create('grpIE', [p1])
    game.go('trial')

    assert initRan.get() == 1                            // init ran once, before the buttons appeared
    def defectUid = p1.choices.find { it.name == 'Defect' }.uid
    submit(p1, defectUid)

    assert resultRan.get() == 1                          // the chosen option's result ran
    assert recorded.size() == 1                          // exactly one event tracked (not both options)
    assert recorded[0].name == 'chose'
    assert recorded[0].data.opt == 'D'                   // ...the DEFECT event, since that's what was picked
    assert recorded[0].data.groupId == 'grpIE'           // ...tagged by game.a.addEvent
  } finally {
    GroupContext.a = realA
  }
}

// Group choices submitted from a java.util.Timer thread must resolve the queue and fire done,
// exactly like the synchronous case. This exercises the real cross-thread path: a timer callback
// emits CustomEvent on the bus -> events.groovy routes it to the player-scoped listener Game.ask
// installed on the eval thread. There is no thread-binding hazard: `events` and `g` are the SAME
// instances on both threads (gremlin-groovy resolves a bare `events` against a FIXED ScriptContext
// captured at closure-creation), g.getVertex works on the timer thread, and the EventBus is
// thread-safe. (The harness shares one cleared EventBus across cases for the same reason; see
// ScriptTestHarness.SHARED_EVENTS.)
test.async("group choices submitted from a timer thread resolve and fire done", 4000) { done ->
  def doneCount = new AtomicInteger(0)
  defineCounterGame(doneCount)
  def p1 = g.addPlayer('tp1')
  def p2 = g.addPlayer('tp2')
  def game = Games.create('grpTimer', [p1, p2])
  game.go('trial')

  // Read the uids on THIS (eval) thread -- the choice maps are written here; the timer only emits.
  def uid1 = p1.choices[0].uid
  def uid2 = p2.choices[0].uid
  check { assert game.pendingCount() == 2 }

  timers.newTimer().runAfter(50) {
    submit(p1, uid1)
    submit(p2, uid2)
    done {
      assert game.pendingCount() == 0      // queue drained from the timer thread
      assert doneCount.get() == 1          // done fired exactly once
    }
  }
}

// --- TreatmentManager ---------------------------------------------------------
// A TreatmentManager assigns cohorts to experimental conditions. Assignment is uniform-random
// among eligible treatments; each treatment has a completion target; abandoned games release their
// slot for reassignment; assignment stops once every target is met.

// Completing one game at a time drains the quotas deterministically regardless of the random pick
// order: each next()+complete() consumes exactly one slot, so the loop runs sum(targets) times.
test("TreatmentManager assigns within completion quotas and stops when all are met") {
  def tm = new TreatmentManager()
  tm.treatment('A', new SampleParams(1, 'a'), 2)
  tm.treatment('B', new SampleParams(2, 'b'), 1)

  def assigned = 0
  def t
  while ((t = tm.next()) != null) {
    assigned++
    assert assigned <= 10 : 'quota loop must terminate'
    tm.complete(t)
  }

  assert assigned == 3                       // 2 for A + 1 for B
  assert tm.get('A').completed == 2
  assert tm.get('B').completed == 1
  assert tm.isComplete()
  assert tm.next() == null                   // nothing left to assign
}

// next() reserves a slot (inFlight), so a treatment at capacity isn't handed out again until its
// in-flight game resolves; releasing an abandoned game reopens the slot.
test("TreatmentManager reserves a slot per assignment and refills on release") {
  def tm = new TreatmentManager()
  tm.treatment('only', new SampleParams(1, 'x'), 1)

  def t1 = tm.next()
  assert t1 != null && t1.name == 'only'
  assert tm.next() == null                   // slot in-flight -> treatment is full

  tm.release(t1)                             // abandoned -> slot frees
  def t2 = tm.next()
  assert t2 != null && t2.name == 'only'     // reassigned
  tm.complete(t2)
  assert tm.isComplete()
  assert tm.next() == null
}

test("TreatmentManager validates targets/names and exposes a treatment's parameters") {
  def tm = new TreatmentManager()
  tm.treatment('A', new SampleParams(3, 'a'), 1)

  def threwTarget = false
  try { tm.treatment('bad', new SampleParams(1, 'z'), 0) } catch (IllegalArgumentException e) { threwTarget = true }
  assert threwTarget : 'target must be >= 1'

  def threwDup = false
  try { tm.treatment('A', new SampleParams(3, 'a'), 1) } catch (IllegalStateException e) { threwDup = true }
  assert threwDup : 'duplicate treatment name must throw'

  def t = tm.next()
  assert t.name == 'A'
  assert t.parameters.rounds == 3 && t.parameters.mode == 'a'   // the opaque object passed in
}

// The wiring from the design preview: next() -> Games.create(parameters) -> onFinish completes the
// quota. Synchronous: the submit drives done -> finish -> onFinish -> complete on the same thread.
test("TreatmentManager + Games: next -> create -> onFinish completes the quota") {
  def tm = new TreatmentManager()
  tm.treatment('solo', new SampleParams(1, 'x'), 1)

  def t = tm.next()
  assert t != null
  def p1 = g.addPlayer('tm1')
  def game = Games.create('gTM', [p1], t.parameters)
  game.state.treatment = t.name
  game.onFinish  { tm.complete(t) }
  game.onAbandon { tm.release(t) }
  game.step('only', [
    run:  { game.players.each { pl -> game.ask(pl, [name: 'go', result: { v, data -> }]) } },
    done: { game.finish() },
  ])
  game.go('only')
  submit(p1, p1.choices[0].uid)              // -> done -> finish -> onFinish -> complete(t)

  assert game.state.treatment == 'solo'
  assert game.parameters.rounds == 1         // the game ran under the treatment's parameters
  assert tm.get('solo').completed == 1
  assert tm.isComplete()
  assert tm.next() == null
}

// Abandoning a game (its cohort empties) must release the treatment slot so it can be reassigned.
test("TreatmentManager + Games: abandon releases the slot for reassignment") {
  def tm = new TreatmentManager()
  tm.treatment('solo', new SampleParams(1, 'x'), 1)

  def t = tm.next()
  def p1 = g.addPlayer('tmA1')
  def game = Games.create('gTMa', [p1], t.parameters)
  game.onFinish  { tm.complete(t) }
  game.onAbandon { tm.release(t) }
  game.step('only', [
    run:  { game.players.each { pl -> game.ask(pl, [name: 'go', result: { v, data -> }]) } },
    done: { },
  ])
  game.go('only')
  assert tm.next() == null                   // slot reserved while the game runs

  game.drop(p1)                              // only player drops -> abandon -> release(t)
  def t2 = tm.next()
  assert t2 != null && t2.name == 'solo'     // slot reopened for reassignment
  assert tm.get('solo').completed == 0       // ...and it was never counted as completed
}

// --- Games -> recruitment auto-tracking -------------------------------------------------------
// Games.create / Game.finish / Game.abandon report the game lifecycle to whatever recruitment
// controller is bound into GroupContext (in production that's the global `recruitment`). These
// tests swap in a fresh controller and restore the bound one in `finally` so global state is left
// untouched. The updates are idempotent with WaitingRoom's, so the lobby + Games can both drive them.

test("Games.create + Game.finish report start/completion to the bound recruitment controller") {
  def saved = GroupContext.recruitment
  def rc = new RecruitmentController(g)
  try {
    GroupContext.recruitment = rc
    def p1 = g.addPlayer('recGameP1')
    rc.clientWaiting('recGameP1')              // a known recruitment client
    def game = Games.create('recGame', [p1], null, { })   // explicit no-op builder

    // create -> gameStarted: client is active and the game holds a slot
    assert rc.activeGames.containsKey('recGame')
    assert rc.clients.find { it.id == 'recGameP1' }.state == 'active'
    assert rc.clients.find { it.id == 'recGameP1' }.gameId == 'recGame'

    game.finish()
    // finish -> gameCompleted: slot freed, client completed, one completed game counted
    assert !rc.activeGames.containsKey('recGame')
    assert rc.clients.find { it.id == 'recGameP1' }.state == 'completed'
    assert rc.completedGames == 1
  } finally {
    GroupContext.recruitment = saved
  }
}

test("Game.abandon frees the recruitment slot without counting a completed game") {
  def saved = GroupContext.recruitment
  def rc = new RecruitmentController(g)
  try {
    GroupContext.recruitment = rc
    def p1 = g.addPlayer('recGameAbP1')
    rc.clientWaiting('recGameAbP1')
    def game = Games.create('recGameAb', [p1], null, { })
    assert rc.activeGames.containsKey('recGameAb')

    game.drop(p1)                              // only player drops -> cohort empty -> abandon
    assert !rc.activeGames.containsKey('recGameAb')   // slot freed
    assert rc.completedGames == 0                     // abandoned, NOT counted as completed
    assert rc.clients.find { it.id == 'recGameAbP1' }.gameId == null
  } finally {
    GroupContext.recruitment = saved
  }
}

test("Games auto-tracking is a no-op when no recruitment controller is bound") {
  def saved = GroupContext.recruitment
  try {
    GroupContext.recruitment = null            // nothing bound
    def p1 = g.addPlayer('recGameNoneP1')
    def game = Games.create('recGameNone', [p1], null, { })
    game.finish()                              // must not throw even with no controller
    assert Games.get('recGameNone') == null    // finished + disposed cleanly
  } finally {
    GroupContext.recruitment = saved
  }
}

// --- TreatmentManager.factorial ----------------------------------------------
// factorial enumerates the cartesian product of {var: [values]} into one treatment per cell.

test("TreatmentManager.factorial enumerates the full product with read-only map params") {
  def tm = new TreatmentManager()
  def made = tm.factorial([a: [1, 2, 3], b: [5, 50, 500], c: [4, 3.5, 3]], 1)

  assert made.size() == 27                                  // 3 x 3 x 3
  assert tm.all().size() == 27
  assert made.collect { it.name }.unique().size() == 27     // names are unique
  assert tm.get('a=1,b=5,c=4') != null                      // deterministic "k=v,..." naming
  def t = tm.get('a=2,b=50,c=3.5')
  assert t.parameters.a == 2 && t.parameters.b == 50 && t.parameters.c == 3.5

  def threw = false
  try { t.parameters.a = 99 } catch (UnsupportedOperationException e) { threw = true }
  assert threw : 'default factorial params are a read-only map'
}

test("TreatmentManager.factorial maps each cell through a params builder when given") {
  def tm = new TreatmentManager()
  def made = tm.factorial([a: [1, 2], b: [5, 50]], 1) { combo -> new ABC(combo.a, combo.b, 4) }

  assert made.size() == 4
  def t = tm.get('a=1,b=50')
  assert t != null
  assert t.parameters instanceof ABC
  assert t.parameters.a == 1 && t.parameters.b == 50 && t.parameters.c == 4.0
}

test("TreatmentManager.factorial validates variables and target") {
  def tm = new TreatmentManager()
  def e1 = false; try { tm.factorial([:], 1) }      catch (IllegalArgumentException e) { e1 = true }
  assert e1 : 'empty variable map must throw'
  def e2 = false; try { tm.factorial([a: []], 1) }  catch (IllegalArgumentException e) { e2 = true }
  assert e2 : 'a variable with no values must throw'
  def e3 = false; try { tm.factorial([a: [1]], 0) } catch (IllegalArgumentException e) { e3 = true }
  assert e3 : 'target must be >= 1'
}

test("TreatmentManager.factorial gives every cell the completion target") {
  def tm = new TreatmentManager()
  tm.factorial([a: [1, 2], b: [5, 50]], 1)                  // 4 cells, target 1 each

  def assigned = 0
  def t
  while ((t = tm.next()) != null) { assigned++; tm.complete(t); assert assigned <= 20 }
  assert assigned == 4
  assert tm.isComplete()
}

// --- TreatmentManager: persistence + pluggable sampling ----------------------
// File-backed progress (completed counts + sampling seed/cursor) survives a fresh manager instance
// (i.e. an engine reload / server restart). inFlight is ephemeral and never persisted. Sampling is a
// pure function pick(seed, cursor, order) -> Treatment, selectable via named functions. Each test
// uses its own temp dir (the harness has no temp fixture, and cwd is the repo root) and cleans up.

// No key => persistence disabled => identical to the historical in-memory behavior, and nothing is
// ever written to disk (even if a dir is supplied).
test("TreatmentManager without a key persists nothing") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-nokey').toFile()
  try {
    def tm = new TreatmentManager(dir: tmp.absolutePath)   // no key -> persistence off
    tm.treatment('A', new SampleParams(1, 'a'), 1)
    def t = tm.next()
    assert t != null && t.name == 'A'
    tm.complete(t)
    assert tm.isComplete()
    assert tmp.listFiles().length == 0 : 'no key -> no file written'
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// completed counts are written on complete() and adopted by a fresh manager on the same dir/key.
test("TreatmentManager persists completed counts and resumes in a fresh instance") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-resume').toFile()
  try {
    def tm1 = new TreatmentManager(key: 'exp', dir: tmp.absolutePath)
    tm1.treatment('A', new SampleParams(1, 'a'), 5)
    tm1.treatment('B', new SampleParams(2, 'b'), 5)
    3.times { tm1.complete(tm1.get('A')) }     // simulate 3 finished A-games
    2.times { tm1.complete(tm1.get('B')) }     // and 2 finished B-games
    assert tm1.get('A').completed == 3 && tm1.get('B').completed == 2

    // a fresh manager (== an engine reload) with the SAME definitions adopts the file counts
    def tm2 = new TreatmentManager(key: 'exp', dir: tmp.absolutePath)
    tm2.treatment('A', new SampleParams(1, 'a'), 5)
    tm2.treatment('B', new SampleParams(2, 'b'), 5)
    assert tm2.get('A').completed == 3 : 'completed adopted from file'
    assert tm2.get('B').completed == 2
    assert tm2.get('A').inFlight == 0
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// inFlight is a runtime reservation: a fresh manager (reload) always starts it at 0 so a slot held
// by a game that died on reload is reassignable.
test("TreatmentManager does not persist inFlight; reload frees reserved slots") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-inflight').toFile()
  try {
    def tm1 = new TreatmentManager(key: 'inf', dir: tmp.absolutePath)
    tm1.treatment('A', new SampleParams(1, 'a'), 3)
    def t = tm1.next()
    assert t.name == 'A' && tm1.get('A').inFlight == 1

    def tm2 = new TreatmentManager(key: 'inf', dir: tmp.absolutePath)
    tm2.treatment('A', new SampleParams(1, 'a'), 3)
    assert tm2.get('A').inFlight == 0 : 'inFlight is not persisted'
    assert tm2.get('A').completed == 0
    assert tm2.next() != null : 'the slot is assignable again after reload'
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// "Defined once": re-running the same definitions on reload adopts progress (no reset, no throw);
// but defining the same name twice within ONE run still throws.
test("TreatmentManager defines once: reload adopts; in-run duplicate still throws") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-once').toFile()
  try {
    def tm1 = new TreatmentManager(key: 'once', dir: tmp.absolutePath)
    tm1.treatment('A', new SampleParams(1, 'a'), 5)
    2.times { tm1.complete(tm1.get('A')) }

    def tm2 = new TreatmentManager(key: 'once', dir: tmp.absolutePath)
    tm2.treatment('A', new SampleParams(1, 'a'), 5)      // same name across reload -> adopt, no throw
    assert tm2.get('A').completed == 2

    def threw = false
    try { tm2.treatment('A', new SampleParams(1, 'a'), 5) } catch (IllegalStateException e) { threw = true }
    assert threw : 'duplicate within a single run must throw'
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// The same explicit seed yields the identical assignment sequence (no persistence needed): sampling
// is a pure function of (seed, cursor, eligible set). release() keeps every treatment eligible.
test("TreatmentManager sampling is reproducible from the same seed") {
  def build = { ->
    def tm = new TreatmentManager(distribution: simpleRandom(), seed: 42L)
    tm.treatment('A', new SampleParams(1, 'a'), 1000)
    tm.treatment('B', new SampleParams(2, 'b'), 1000)
    tm.treatment('C', new SampleParams(3, 'c'), 1000)
    return tm
  }
  def tmA = build(); def tmB = build()
  def seqA = []; def seqB = []
  20.times { def t = tmA.next(); seqA << t.name; tmA.release(t) }
  20.times { def t = tmB.next(); seqB << t.name; tmB.release(t) }
  assert seqA == seqB : "same seed -> identical sequence ($seqA vs $seqB)"
  assert seqA.unique().size() > 1 : 'sanity: the sequence is not constant'
}

// round-robin cycles the defined order and resumes its position (the persisted cursor) after reload.
test("TreatmentManager roundRobin cycles and resumes its cursor across a reload") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-rr').toFile()
  try {
    def define = { tm ->
      tm.treatment('A', new SampleParams(1, 'a'), 1000)
      tm.treatment('B', new SampleParams(2, 'b'), 1000)
      tm.treatment('C', new SampleParams(3, 'c'), 1000)
    }
    def tm1 = new TreatmentManager(key: 'rr', dir: tmp.absolutePath, distribution: roundRobin())
    define(tm1)
    def seq = []
    4.times { def t = tm1.next(); seq << t.name; tm1.release(t) }
    assert seq == ['A', 'B', 'C', 'A'] : "round-robin order ($seq)"

    def tm2 = new TreatmentManager(key: 'rr', dir: tmp.absolutePath, distribution: roundRobin())
    define(tm2)
    assert tm2.next().name == 'B' : 'cursor resumed mid-cycle (4 % 3 == 1 -> B)'
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// random-block offers a full permutation of the treatments per block, and a reload continues the
// in-progress block (it does not restart it) -- the two halves form one complete permutation.
test("TreatmentManager randomBlock completes its block across a reload") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-blk').toFile()
  try {
    def define = { tm -> ['A', 'B', 'C', 'D'].each { tm.treatment(it, new SampleParams(1, it), 1000) } }
    def tm1 = new TreatmentManager(key: 'blk', dir: tmp.absolutePath, distribution: randomBlock(), seed: 7L)
    define(tm1)
    def first2 = []
    2.times { def t = tm1.next(); first2 << t.name; tm1.release(t) }    // cursor -> 2, mid-block

    def tm2 = new TreatmentManager(key: 'blk', dir: tmp.absolutePath, distribution: randomBlock(), seed: 7L)
    define(tm2)
    def rest = []
    2.times { def t = tm2.next(); rest << t.name; tm2.release(t) }      // cursor 2,3 -> completes block 0

    assert ((first2 + rest) as Set) == (['A', 'B', 'C', 'D'] as Set) :
      "block 0 completes across the reload without restarting ($first2 + $rest)"
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// weighted assignment favors higher-weight treatments (weight passed via the named-map form).
test("TreatmentManager weighted favors higher-weight treatments") {
  def tm = new TreatmentManager(distribution: weighted(), seed: 123L)
  tm.treatment('light', new SampleParams(1, 'l'), 100000)
  tm.treatment(name: 'heavy', parameters: new SampleParams(2, 'h'), target: 100000, weight: 9.0)
  def counts = [light: 0, heavy: 0]
  2000.times { def t = tm.next(); counts[t.name] = counts[t.name] + 1; tm.release(t) }
  assert counts.heavy > counts.light * 4 : "heavy (weight 9) should dominate: $counts"
}

// The code definition is authoritative: when the script diverges from the file, the CODE target/weight
// win and only the persisted progress (completed) is adopted.
test("TreatmentManager uses the code target/weight when code diverges from the file") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-div').toFile()
  try {
    def tm1 = new TreatmentManager(key: 'div', dir: tmp.absolutePath)
    tm1.treatment(name: 'A', parameters: new SampleParams(1, 'a'), target: 5, weight: 1.0)
    3.times { tm1.complete(tm1.get('A')) }

    def tm2 = new TreatmentManager(key: 'div', dir: tmp.absolutePath)
    tm2.treatment(name: 'A', parameters: new SampleParams(1, 'a'), target: 10, weight: 3.0)   // code wins
    assert tm2.get('A').target == 10 : 'code target wins'
    assert tm2.get('A').weight == 3.0 : 'code weight wins'
    assert tm2.get('A').completed == 3 : 'persisted progress is still adopted'
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// Lowering the code target below the already-collected completed count clamps completed down to the
// new target (the arm is simply treated as met) rather than leaving negative remaining slots.
test("TreatmentManager clamps persisted progress down to a lowered code target") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-lower').toFile()
  try {
    def tm1 = new TreatmentManager(key: 'lower', dir: tmp.absolutePath)
    tm1.treatment('A', new SampleParams(1, 'a'), 10)
    6.times { tm1.complete(tm1.get('A')) }                // 6 of 10 collected

    def tm2 = new TreatmentManager(key: 'lower', dir: tmp.absolutePath)
    tm2.treatment('A', new SampleParams(1, 'a'), 4)       // script lowers target to 4 (below the 6 done)
    assert tm2.get('A').target == 4 : 'code target wins'
    assert tm2.get('A').completed == 4 : 'completed clamped down to the new target'
    assert tm2.get('A').isMet()
    assert tm2.next() == null : 'the now-met arm is not assigned'
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// Defensive: a persisted completed count above its target (e.g. a hand-edited file) is clamped on
// load so the arm is simply treated as met rather than producing negative remaining slots.
test("TreatmentManager clamps a persisted completed count above its target") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-clamp').toFile()
  try {
    def json = new groovy.json.JsonBuilder([
      version:      1,
      key:          'clamp',
      distribution: [type: 'random', seed: 1, cursor: 0],
      treatments:   [A: [target: 2, weight: 1.0, completed: 5]]
    ]).toPrettyString()
    org.apache.commons.io.FileUtils.writeStringToFile(new File(tmp, 'clamp.json'), json)

    def tm = new TreatmentManager(key: 'clamp', dir: tmp.absolutePath)
    tm.treatment('A', new SampleParams(1, 'a'), 2)
    assert tm.get('A').completed == 2 : 'completed clamped to the target'
    assert tm.get('A').isMet()
    assert tm.next() == null : 'a met treatment is not assigned'
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// An arm present in the file but not re-defined this run is dormant: not live/assignable, but its
// history is preserved in the file across writes.
test("TreatmentManager keeps a dropped arm dormant in the file") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-dorm').toFile()
  try {
    def tm1 = new TreatmentManager(key: 'dorm', dir: tmp.absolutePath)
    tm1.treatment('A', new SampleParams(1, 'a'), 5)
    tm1.treatment('B', new SampleParams(2, 'b'), 5)
    2.times { tm1.complete(tm1.get('B')) }

    def tm2 = new TreatmentManager(key: 'dorm', dir: tmp.absolutePath)
    tm2.treatment('A', new SampleParams(1, 'a'), 5)      // B omitted this run
    assert tm2.get('B') == null : 'B is not live this run'
    assert tm2.all().collect { it.name } == ['A']
    tm2.complete(tm2.get('A'))                           // force a write

    def data = new groovy.json.JsonSlurper().parseText(new File(tmp, 'dorm.json').text)
    assert data.treatments.containsKey('B') : 'dormant B is preserved in the file'
    assert (data.treatments.B.completed as int) == 2
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// The persisted file is valid JSON written to the override dir, with no leftover temp file.
test("TreatmentManager writes valid JSON and leaves no temp file") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-atom').toFile()
  try {
    def tm = new TreatmentManager(key: 'atom', dir: tmp.absolutePath, distribution: roundRobin())
    tm.treatment('A', new SampleParams(1, 'a'), 3)
    tm.complete(tm.next())

    def f = new File(tmp, 'atom.json')
    assert f.exists() : 'file written in the override dir'
    def data = new groovy.json.JsonSlurper().parseText(f.text)
    assert (data.version as int) == 1
    assert data.distribution.type == 'round-robin'
    assert data.treatments.A != null
    def leftovers = tmp.listFiles().findAll { it.name.endsWith('.tmp') }
    assert leftovers.isEmpty() : "no temp files left behind: $leftovers"
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// A custom sampling function (a bare closure with the pick signature) is accepted and persisted as
// type 'custom', with the manager still owning seed/cursor.
test("TreatmentManager accepts a custom sampling function") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-custom').toFile()
  try {
    def firstEligible = { long s, long c, List ts -> ts.find { !it.isFull() } }
    def tm = new TreatmentManager(key: 'custom', dir: tmp.absolutePath, distribution: firstEligible)
    tm.treatment('A', new SampleParams(1, 'a'), 2)
    tm.treatment('B', new SampleParams(2, 'b'), 2)
    def t1 = tm.next()
    assert t1.name == 'A' : 'custom function picked the first eligible'
    tm.release(t1)
    assert tm.next().name == 'A'

    def data = new groovy.json.JsonSlurper().parseText(new File(tmp, 'custom.json').text)
    assert data.distribution.type == 'custom'
    assert (data.distribution.cursor as int) >= 1
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// A reload that does NOT re-supply distribution() must restore the persisted built-in strategy from
// the file, not silently fall back to uniform random while replaying the round-robin cursor.
test("TreatmentManager restores a built-in distribution on reload without re-specifying it") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-restore').toFile()
  try {
    def define = { tm ->
      tm.treatment('A', new SampleParams(1, 'a'), 1000)
      tm.treatment('B', new SampleParams(2, 'b'), 1000)
      tm.treatment('C', new SampleParams(3, 'c'), 1000)
    }
    def tm1 = new TreatmentManager(key: 'restore', dir: tmp.absolutePath, distribution: roundRobin())
    define(tm1)
    def seq = []
    4.times { def t = tm1.next(); seq << t.name; tm1.release(t) }
    assert seq == ['A', 'B', 'C', 'A']                 // cursor advanced to 4

    def tm2 = new TreatmentManager(key: 'restore', dir: tmp.absolutePath)   // NO distribution: arg
    define(tm2)
    def t = tm2.next()
    assert t.name == 'B' : 'round-robin resumed at cursor 4 (4 % 3 -> B), not random'

    def data = new groovy.json.JsonSlurper().parseText(new File(tmp, 'restore.json').text)
    assert data.distribution.type == 'round-robin' : 'strategy restored as round-robin, not reset to random'
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// The random-block multiplier is part of the strategy's identity, so it must round-trip through the
// file and be restored on reload -- otherwise a block of the wrong size resumes.
test("TreatmentManager persists and restores the random-block multiplier") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-blkmult').toFile()
  try {
    def tm1 = new TreatmentManager(key: 'blkmult', dir: tmp.absolutePath, distribution: randomBlock(2), seed: 7L)
    ['A', 'B'].each { tm1.treatment(it, new SampleParams(1, it), 1000) }
    tm1.complete(tm1.next())                           // force a persist
    def data = new groovy.json.JsonSlurper().parseText(new File(tmp, 'blkmult.json').text)
    assert data.distribution.type == 'random-block'
    assert (data.distribution.mult as int) == 2 : 'block multiplier persisted'

    def tm2 = new TreatmentManager(key: 'blkmult', dir: tmp.absolutePath)   // NO distribution: arg
    ['A', 'B'].each { tm2.treatment(it, new SampleParams(1, it), 1000) }
    tm2.complete(tm2.next())                           // assign + persist under the restored strategy
    def data2 = new groovy.json.JsonSlurper().parseText(new File(tmp, 'blkmult.json').text)
    assert data2.distribution.type == 'random-block' : 'random-block restored on reload (not reset to random)'
    assert (data2.distribution.mult as int) == 2 : 'multiplier restored on reload'
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// A custom distribution that returns an already-full treatment must NOT be assigned: next() re-verifies
// eligibility, so the over-target arm is skipped rather than over-recruited (the built-in samplers
// guaranteed this; a bare closure does not).
test("TreatmentManager skips an ineligible treatment returned by a custom distribution") {
  def alwaysFirst = { long s, long c, List ts -> ts[0] }   // buggy: always returns the first arm
  def tm = new TreatmentManager(distribution: alwaysFirst)
  tm.treatment('A', new SampleParams(1, 'a'), 1)
  tm.treatment('B', new SampleParams(2, 'b'), 1)

  def t1 = tm.next()
  assert t1 != null && t1.name == 'A'
  tm.complete(t1)                                      // A now met (1 == target) -> full
  assert tm.get('A').isFull()

  def t2 = tm.next()                                   // strategy still returns the full A
  assert t2 == null : 'an ineligible (full) treatment from the strategy is skipped, not assigned'
  assert tm.get('A').completed == 1 : 'A was not over-recruited beyond its target'
  assert tm.get('A').inFlight == 0 : 'no slot reserved on the rejected treatment'
}

// A persisted target below 1 (a corrupt or partially-written file, or a missing target defaulting to 0)
// must NOT create a permanently-full arm: the already-validated code target is used instead.
test("TreatmentManager ignores an invalid persisted target and uses the code target") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-badtarget').toFile()
  try {
    def json = new groovy.json.JsonBuilder([
      version:      1,
      key:          'badtarget',
      distribution: [type: 'random', seed: 1, cursor: 0],
      treatments:   [A: [target: 0, weight: 1.0, completed: 0]]   // target 0 -> would be full forever
    ]).toPrettyString()
    org.apache.commons.io.FileUtils.writeStringToFile(new File(tmp, 'badtarget.json'), json)

    def tm = new TreatmentManager(key: 'badtarget', dir: tmp.absolutePath)
    tm.treatment('A', new SampleParams(1, 'a'), 3)     // code target 3 is valid
    assert tm.get('A').target == 3 : 'invalid file target (0) ignored in favor of the code target'
    assert !tm.get('A').isFull()
    def t = tm.next()
    assert t != null && t.name == 'A' : 'the arm assigns instead of being permanently full'
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// weight must be > 0 (mirrors the target >= 1 rule): a zero/negative weight is a definition error.
test("TreatmentManager rejects a non-positive weight") {
  def tm = new TreatmentManager()
  def threw = false
  try { tm.treatment(name: 'bad', parameters: new SampleParams(1, 'x'), target: 1, weight: 0.0) }
  catch (IllegalArgumentException e) { threw = true }
  assert threw : 'weight must be > 0'
}
