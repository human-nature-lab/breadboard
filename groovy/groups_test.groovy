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
import java.util.concurrent.atomic.AtomicBoolean

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

// drop() leaves the player's graph edges intact by default; disconnect() -- or the drop(player, true)
// flag -- is what tears them out. Split so experiments decide when the graph is torn down.
test("drop keeps graph edges by default; disconnect / drop(player, true) remove them") {
  def doneCount = new AtomicInteger(0)
  defineCounterGame(doneCount)
  def p1 = g.addPlayer('edrp1')
  def p2 = g.addPlayer('edrp2')
  def p3 = g.addPlayer('edrp3')
  g.addEdge(p1, p2)
  g.addEdge(p1, p3)
  def game = Games.create('grpDropEdges', [p1, p2, p3])
  game.go('trial')

  assert g.hasEdge(p1, p2)
  game.drop(p2)                            // default: dropped, but NOT disconnected
  assert p2._system.status == 'dropped'
  assert g.hasEdge(p1, p2)                 // ...the edge survives the drop

  game.disconnect(p2)                      // explicit teardown removes it
  assert !g.hasEdge(p1, p2)

  assert g.hasEdge(p1, p3)
  game.drop(p3, true)                      // flag form drops AND disconnects in one call
  assert p3._system.status == 'dropped'
  assert !g.hasEdge(p1, p3)
  assert game.players.contains(p1)         // p1 still active -> game not abandoned
}

// A player completed BEFORE an in-game drop (recruitment.complete ran, then the experiment drops them
// to free their seat for a replacement) must KEEP 'completed'. drop() must not downgrade a terminal
// status: doing so strips the participant's finish/redirect screen and leaves them on a blank page.
// Regression for the drop-out-blank-screen bug.
test("drop does not downgrade an already-completed player to dropped") {
  def doneCount = new AtomicInteger(0)
  defineCounterGame(doneCount)
  def p1 = g.addPlayer('cdrp1')
  def p2 = g.addPlayer('cdrp2')
  g.addEdge(p1, p2)
  def game = Games.create('grpDropCompleted', [p1, p2])
  game.go('trial')

  setVertexStatus(p2, 'completed')         // simulate recruitment.complete having run for p2
  assert !game.players.contains(p2)        // completed -> no longer an active member

  game.drop(p2, true)                      // drop the completed seat + tear edges out for the bot
  assert p2._system.status == 'completed'  // NOT clobbered to 'dropped' -> finish screen survives
  assert !g.hasEdge(p1, p2)                // disconnect side of drop still runs
  assert game.players.contains(p1)         // p1 still active -> game not abandoned
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

// --- TreatmentManager.next(filter) -------------------------------------------------------------
// next(closure) restricts the eligible set to treatments the filter accepts, then samples among
// those exactly as next() samples among all. The experiment supplies the predicate (e.g. to match
// a value assigned to players at join time); the manager never needs to understand parameters.

// Only accepted treatments are ever handed out, even when excluded ones have open slots.
test("TreatmentManager.next(filter): only treatments the filter accepts are eligible") {
  def tm = new TreatmentManager()
  tm.treatment('A', new SampleParams(1, 'keep'), 5)
  tm.treatment('B', new SampleParams(2, 'skip'), 5)

  def picks = 0
  def t
  while ((t = tm.next { it.parameters.mode == 'keep' }) != null) {
    assert t.name == 'A' : 'filter must exclude B'
    tm.complete(t)
    picks++
    assert picks <= 10 : 'loop must terminate'
  }
  assert picks == 5                          // drained A's quota; B never touched
  assert tm.get('A').completed == 5
  assert tm.get('B').completed == 0
  assert tm.next { it.parameters.mode == 'keep' } == null   // A is met -> no accepted arm eligible
  assert tm.next { it.parameters.mode == 'skip' } != null   // ...but B is still assignable
}

// A filter that matches only full treatments yields null (the accepted set is at capacity).
test("TreatmentManager.next(filter): a filter matching only full treatments yields null") {
  def tm = new TreatmentManager()
  tm.treatment('A', new SampleParams(1, 'a'), 1)
  tm.treatment('B', new SampleParams(1, 'b'), 1)

  def a = tm.next { it.name == 'A' }
  assert a != null && a.name == 'A'
  assert tm.next { it.name == 'A' } == null               // A's only slot is in-flight
  assert (tm.next { it.name == 'B' })?.name == 'B'        // B still open
}

// No match -> null and nothing is reserved (a later unfiltered next still gets the arm).
test("TreatmentManager.next(filter): no match yields null and reserves nothing") {
  def tm = new TreatmentManager()
  tm.treatment('A', new SampleParams(1, 'a'), 1)

  assert tm.next { it.parameters.mode == 'nope' } == null
  def t = tm.next()
  assert t != null && t.name == 'A'          // the no-match call reserved no slot
}

// An accept-all filter behaves exactly like the zero-arg next().
test("TreatmentManager.next(filter): an accept-all filter drains the same as next()") {
  def tm = new TreatmentManager()
  tm.treatment('A', new SampleParams(1, 'a'), 2)
  tm.treatment('B', new SampleParams(1, 'b'), 1)

  def assigned = 0
  def t
  while ((t = tm.next { true }) != null) {
    assigned++
    assert assigned <= 10
    tm.complete(t)
  }
  assert assigned == 3
  assert tm.get('A').completed == 2 && tm.get('B').completed == 1
  assert tm.isComplete()
}

// The reserved treatment settles via complete()/release() just like next()'s result.
test("TreatmentManager.next(filter): result settles via complete() and release()") {
  def tm = new TreatmentManager()
  tm.treatment('A', new SampleParams(1, 'a'), 1)

  def t = tm.next { it.name == 'A' }
  assert t != null
  tm.release(t)                              // abandoned -> slot reopens
  def t2 = tm.next { it.name == 'A' }
  assert t2 != null && t2.name == 'A'
  tm.complete(t2)
  assert tm.get('A').completed == 1
  assert tm.isComplete()
}

// A filter that throws excludes that treatment (defensively) rather than aborting the assignment.
test("TreatmentManager.next(filter): a throwing filter excludes that treatment, not the draw") {
  def tm = new TreatmentManager()
  tm.treatment('A', new SampleParams(1, 'a'), 1)
  tm.treatment('B', new SampleParams(2, 'b'), 1)

  def t = tm.next { if (it.name == 'A') throw new RuntimeException('boom'); return it.name == 'B' }
  assert t != null && t.name == 'B'          // A excluded by the throw, B selected
}

// The first next(filter) seals the manager, same as the first next().
test("TreatmentManager.next(filter): the first call seals the manager") {
  def tm = new TreatmentManager()
  tm.treatment('A', new SampleParams(1, 'a'), 1)

  tm.next { it.name == 'A' }
  def threw = false
  try { tm.treatment('B', new SampleParams(1, 'b'), 1) } catch (IllegalStateException e) { threw = true }
  assert threw : 'defining a treatment after the first next(filter) must throw'
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

// --- TreatmentManager: log persistence + pluggable sampling ------------------
// Progress persists as an append-only JSON-lines log (<key>.jsonl): 'started' / 'completed' /
// 'released' event records plus 'config' records (seed, distribution, arm order). State is rebuilt
// by replaying the log, so a fresh manager instance (an engine reload / server restart) resumes,
// and the record can be corrected by hand -- every line stands alone. inFlight is ephemeral and
// never persisted; the cursor is the count of 'started' records. Each test uses its own temp dir
// (the harness has no temp fixture, and cwd is the repo root) and cleans up.

// Parse every non-blank line of a treatment log into records. Throws on an invalid line, so using
// it doubles as a validity check on the whole file.
tmReadLog = { File f ->
  def slurper = new groovy.json.JsonSlurper()
  f.readLines('UTF-8').findAll { it.trim() != '' }.collect { slurper.parseText(it) }
}

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
    assert tm2.get('A').started == 1 : 'the assignment itself IS on record'
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

// Defensive: a replayed completed count above its target (e.g. a hand-edited log) is clamped on
// load so the arm is simply treated as met rather than producing negative remaining slots. The log
// is hand-written here -- records need no 'at' field and no config record to count.
test("TreatmentManager clamps a replayed completed count above its target") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-clamp').toFile()
  try {
    def lines = ['{"type":"config","version":2,"key":"clamp","seed":1,"distribution":"random","order":["A"]}']
    5.times { lines << '{"type":"completed","treatment":"A"}' }
    org.apache.commons.io.FileUtils.writeStringToFile(new File(tmp, 'clamp.jsonl'), lines.join('\n') + '\n')

    def tm = new TreatmentManager(key: 'clamp', dir: tmp.absolutePath)
    tm.treatment('A', new SampleParams(1, 'a'), 2)
    assert tm.get('A').completed == 2 : 'completed clamped to the target'
    assert tm.get('A').isMet()
    assert tm.next() == null : 'a met treatment is not assigned'
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// An arm present in the log but not re-defined this run is dormant: not live/assignable, but the
// log is append-only, so its history stays in the file untouched.
test("TreatmentManager keeps a dropped arm dormant in the log") {
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

    def recs = tmReadLog(new File(tmp, 'dorm.jsonl'))
    assert recs.findAll { it.type == 'completed' && it.treatment == 'B' }.size() == 2 :
      'dormant B keeps its history in the log'
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// Every log line is one standalone valid JSON record, and no temp files are left behind.
test("TreatmentManager writes one valid JSON record per line and leaves no temp file") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-atom').toFile()
  try {
    def tm = new TreatmentManager(key: 'atom', dir: tmp.absolutePath, distribution: roundRobin())
    tm.treatment('A', new SampleParams(1, 'a'), 3)
    tm.complete(tm.next())

    def f = new File(tmp, 'atom.jsonl')
    assert f.exists() : 'log written in the override dir'
    def recs = tmReadLog(f)                              // throws if any line is invalid
    def cfg = recs.findAll { it.type == 'config' }.last()
    assert (cfg.version as int) == 2
    assert cfg.distribution == 'round-robin'
    assert cfg.order == ['A']
    assert recs.find { it.type == 'started' && it.treatment == 'A' } != null
    assert recs.find { it.type == 'completed' && it.treatment == 'A' } != null
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

    def recs = tmReadLog(new File(tmp, 'custom.jsonl'))
    assert recs.findAll { it.type == 'config' }.last().distribution == 'custom'
    assert recs.count { it.type == 'started' } == 2 : 'each assignment appended a started record'
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

    def cfgs = tmReadLog(new File(tmp, 'restore.jsonl')).findAll { it.type == 'config' }
    assert cfgs.last().distribution == 'round-robin' : 'strategy restored as round-robin, not reset to random'
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
    tm1.complete(tm1.next())                           // force a write
    def f = new File(tmp, 'blkmult.jsonl')
    def cfg = tmReadLog(f).findAll { it.type == 'config' }.last()
    assert cfg.distribution == 'random-block'
    assert (cfg.mult as int) == 2 : 'block multiplier persisted'

    def tm2 = new TreatmentManager(key: 'blkmult', dir: tmp.absolutePath)   // NO distribution: arg
    ['A', 'B'].each { tm2.treatment(it, new SampleParams(1, it), 1000) }
    tm2.complete(tm2.next())                           // assign + append under the restored strategy
    def cfg2 = tmReadLog(f).findAll { it.type == 'config' }.last()
    assert cfg2.distribution == 'random-block' : 'random-block restored on reload (not reset to random)'
    assert (cfg2.mult as int) == 2 : 'multiplier restored on reload'
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

// Hand-edits happen: a corrupt line (a typo, a crash-truncated tail) or an unknown record type is
// warned about and skipped, and every other record still replays -- the log never loads
// all-or-nothing.
test("TreatmentManager skips corrupt or unknown log lines and replays the rest") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-badline').toFile()
  try {
    def lines = [
      '{"type":"config","version":2,"key":"badline","seed":1,"distribution":"random","order":["A"]}',
      '{"type":"completed","treatment":"A"}',
      'this is not json at all',
      '{"type":"frobnicate","treatment":"A"}',
      '{"type":"completed","treatment":"A"}',
      '{"type":"comp'                                    // crash-truncated tail
    ]
    org.apache.commons.io.FileUtils.writeStringToFile(new File(tmp, 'badline.jsonl'), lines.join('\n') + '\n')

    def tm = new TreatmentManager(key: 'badline', dir: tmp.absolutePath)
    tm.treatment('A', new SampleParams(1, 'a'), 5)
    assert tm.get('A').completed == 2 : 'both valid completed records replayed'
    assert tm.next() != null : 'the manager still assigns'
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// The log records the full assignment lifecycle in order: a config record first, then one 'started'
// per assignment, 'completed' per finish, 'released' per abandonment. Treatment.started counts every
// assignment (completed AND released) and survives a reload.
test("TreatmentManager logs started, completed, and released records") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-log').toFile()
  try {
    def tm = new TreatmentManager(key: 'log', dir: tmp.absolutePath)
    tm.treatment('A', new SampleParams(1, 'a'), 2)
    def t1 = tm.next()
    tm.complete(t1)
    def t2 = tm.next()
    tm.release(t2)
    assert tm.get('A').started == 2

    def recs = tmReadLog(new File(tmp, 'log.jsonl'))
    assert recs[0].type == 'config' : 'the config record precedes the first event'
    assert recs.findAll { it.treatment == 'A' }.collect { it.type } ==
      ['started', 'completed', 'started', 'released']
    assert recs.every { it.at != null } : 'every record is timestamped'

    def tm2 = new TreatmentManager(key: 'log', dir: tmp.absolutePath)
    tm2.treatment('A', new SampleParams(1, 'a'), 2)
    assert tm2.get('A').started == 2 : 'started adopted from the log'
    assert tm2.get('A').completed == 1
    assert tm2.get('A').inFlight == 0
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// THE point of the log format: a researcher can void a bad game by deleting its 'completed' line;
// the arm reopens on the next reload.
test("TreatmentManager reopens an arm when a completed record is deleted from the log") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-void').toFile()
  try {
    def tm1 = new TreatmentManager(key: 'void', dir: tmp.absolutePath)
    tm1.treatment('A', new SampleParams(1, 'a'), 2)
    2.times { tm1.complete(tm1.get('A')) }
    assert tm1.get('A').isMet()

    def f = new File(tmp, 'void.jsonl')
    def lines = f.readLines('UTF-8').findAll { it.trim() != '' }
    int drop = lines.findLastIndexOf { it.contains('"type":"completed"') }
    assert drop >= 0
    lines.remove(drop)                                   // void one game by hand
    f.setText(lines.join('\n') + '\n', 'UTF-8')

    def tm2 = new TreatmentManager(key: 'void', dir: tmp.absolutePath)
    tm2.treatment('A', new SampleParams(1, 'a'), 2)
    assert tm2.get('A').completed == 1 : 'the voided game no longer counts'
    assert !tm2.get('A').isMet()
    assert tm2.next() != null : 'the arm is assignable again'
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// ...and the inverse: hand-appending a 'completed' line manually credits a game.
test("TreatmentManager honors a hand-appended completed record") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-credit').toFile()
  try {
    def tm1 = new TreatmentManager(key: 'credit', dir: tmp.absolutePath)
    tm1.treatment('A', new SampleParams(1, 'a'), 3)
    tm1.complete(tm1.get('A'))

    new File(tmp, 'credit.jsonl').append('{"type":"completed","treatment":"A"}\n', 'UTF-8')

    def tm2 = new TreatmentManager(key: 'credit', dir: tmp.absolutePath)
    tm2.treatment('A', new SampleParams(1, 'a'), 3)
    assert tm2.get('A').completed == 2 : 'the hand-appended record counts'
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// A schema-1 snapshot (<key>.json) migrates into the log on first load: completed counts become
// 'completed' records, the old cursor rides in as cursorBase (so positional strategies resume), the
// distribution is restored, and the legacy file is left in place but never read again (no double
// count on the next reload).
test("TreatmentManager migrates a schema-1 snapshot into the log") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-mig').toFile()
  try {
    def json = new groovy.json.JsonBuilder([
      version:      1,
      key:          'mig',
      distribution: [type: 'round-robin', seed: 9, cursor: 4],
      treatments:   [A: [target: 100, weight: 1.0, completed: 2],
                     B: [target: 100, weight: 1.0, completed: 1],
                     C: [target: 100, weight: 1.0, completed: 1]]
    ]).toPrettyString()
    org.apache.commons.io.FileUtils.writeStringToFile(new File(tmp, 'mig.json'), json)

    def define = { tm -> ['A', 'B', 'C'].each { tm.treatment(it, new SampleParams(1, it), 100) } }
    def tm1 = new TreatmentManager(key: 'mig', dir: tmp.absolutePath)   // NO distribution: arg
    define(tm1)
    assert tm1.get('A').completed == 2 && tm1.get('B').completed == 1 && tm1.get('C').completed == 1
    assert tm1.next().name == 'B' : 'round-robin resumed at the migrated cursor (4 % 3 -> B)'

    def recs = tmReadLog(new File(tmp, 'mig.jsonl'))
    assert (recs[0].cursorBase as int) == 4 : 'old cursor migrated as cursorBase'
    assert recs.findAll { it.type == 'completed' && it.migrated == true }.size() == 4
    assert new File(tmp, 'mig.json').exists() : 'the legacy snapshot is left as a backup'

    def tm2 = new TreatmentManager(key: 'mig', dir: tmp.absolutePath)   // log exists -> legacy ignored
    define(tm2)
    assert tm2.get('A').completed == 2 : 'no double count from re-migrating'
    assert tm2.next().name == 'C' : 'cursor advanced past the tm1 assignment (5 % 3 -> C)'
  } finally {
    org.apache.commons.io.FileUtils.deleteDirectory(tmp)
  }
}

// Config records are appended only when the effective config changes: steady-state reloads add
// nothing, while a definition change (an added arm) is recorded once.
test("TreatmentManager appends a config record only when the configuration changes") {
  def tmp = java.nio.file.Files.createTempDirectory('tm-cfg').toFile()
  try {
    def f = new File(tmp, 'cfg.jsonl')
    def tm1 = new TreatmentManager(key: 'cfg', dir: tmp.absolutePath, distribution: roundRobin())
    tm1.treatment('A', new SampleParams(1, 'a'), 100)
    tm1.treatment('B', new SampleParams(2, 'b'), 100)
    tm1.release(tm1.next())
    assert tmReadLog(f).count { it.type == 'config' } == 1

    def tm2 = new TreatmentManager(key: 'cfg', dir: tmp.absolutePath)   // same defs, restored strategy
    tm2.treatment('A', new SampleParams(1, 'a'), 100)
    tm2.treatment('B', new SampleParams(2, 'b'), 100)
    tm2.release(tm2.next())
    assert tmReadLog(f).count { it.type == 'config' } == 1 : 'an unchanged config appends nothing'

    def tm3 = new TreatmentManager(key: 'cfg', dir: tmp.absolutePath)
    tm3.treatment('A', new SampleParams(1, 'a'), 100)
    tm3.treatment('B', new SampleParams(2, 'b'), 100)
    tm3.treatment('C', new SampleParams(3, 'c'), 100)                   // definition changed
    tm3.release(tm3.next())
    def cfgs = tmReadLog(f).findAll { it.type == 'config' }
    assert cfgs.size() == 2 : 'the changed config is recorded once'
    assert cfgs.last().order == ['A', 'B', 'C']
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

// ---------------------------------------------------------------------------
// game.after(ms) { ... } -- non-blocking deferred work
// ---------------------------------------------------------------------------

// after() must return immediately (the body has NOT run synchronously) and then fire the body later
// on a timer thread -- i.e. it defers without blocking the caller's thread.
test.async("game.after fires the body after the delay, off-thread, without blocking the caller", 4000) { done ->
  Games.define { game -> }                       // a bare after() needs no steps
  def game = Games.create('grpAfter', [])
  def callingThreadId = Thread.currentThread().getId()
  def fired = new AtomicBoolean(false)
  game.after(50) {
    fired.set(true)
    done {
      // ran on a timer thread, not the caller's -> the caller was never blocked
      assert Thread.currentThread().getId() != callingThreadId
    }
  }
  check { assert !fired.get() }                   // after() returned before the deferred body ran
}

// The returned handle cancels a still-pending call: after the original delay has well elapsed, a
// cancelled call's body must never have run.
test.async("cancelling the returned handle aborts a pending after()", 4000) { done ->
  Games.define { game -> }
  def game = Games.create('grpAfterCancel', [])
  def fired = new AtomicBoolean(false)
  def handle = game.after(50) { fired.set(true) }
  handle.cancel()                                 // abort before it fires
  timers.newTimer().runAfter(250) {               // check past the original 50ms delay
    done { assert !fired.get() }
  }
}

// Finishing (disposing) the game cancels any pending call, and the finished-guard means a late fire
// is a no-op: the deferred body never runs against the disposed game.
test.async("finishing the game cancels a pending after() so its body never fires", 4000) { done ->
  def finishCount = new AtomicInteger(0)
  Games.define { game -> game.onFinish { finishCount.incrementAndGet() } }
  def game = Games.create('grpAfterFinish', [])
  def fired = new AtomicBoolean(false)
  game.after(50) { fired.set(true) }
  game.finish()                                   // disposes the game -> pending call cancelled
  check { assert finishCount.get() == 1 }
  timers.newTimer().runAfter(250) {
    done {
      assert !fired.get()                         // the deferred body never ran
      assert Games.get('grpAfterFinish') == null  // ...and finish() still disposed the game
    }
  }
}

// The canonical use: a step's `done` schedules the next step after a pause. The unqualified `go` in
// the after() body proves the body runs with the game as its delegate (like a step's run/done).
test.async("game.after paces a step transition: done defers go() to the next step", 4000) { done ->
  Games.define { game ->
    game.step('reveal', [
      run:  { game.players.each { p -> game.ask(p, [name: 'go', result: { v, data -> }]) } },
      done: { game.after(50) { go('results') } }, // unqualified go -> resolves via the game delegate
    ])
    game.step('results', [
      run:  { done { assert game.currentStep == 'results' } },   // only reached if the deferred go fired
      done: { },
    ])
  }
  def p1 = g.addPlayer('paceP1')
  def game = Games.create('grpPace', [p1])
  game.go('reveal')
  submit(p1, p1.choices[0].uid)                   // resolve reveal -> done waits 50ms -> go('results')
}
