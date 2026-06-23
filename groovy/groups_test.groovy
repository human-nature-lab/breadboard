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
// step -- exactly as if they had submitted. drop also flips _system.active so `players` excludes them.
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
  assert p2._system.active == false        // drop flipped the active flag
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
