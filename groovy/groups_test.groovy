// Tests for groups.groovy, written as Groovy and registered through the `test` DSL bound by
// ScriptTestHarness. Production's ScriptLoader skips any file ending in `_test.groovy`, so this
// never loads into a real experiment. Run via:  sbt "testOnly GroovyScriptTests"
//
//   test("name") { ... }                 -> synchronous
//   test.async("name", timeoutMs) { done -> ... }  -> async; signal completion with done()
//   test.skip("name") { ... }            -> reported as ignored, not run
//
// `g`, `a`, `events`, `timers`, `results` etc. are the same engine bindings the platform scripts
// see; classes like Group / BaseGroupStep / GroupActions come from groups.groovy, already loaded.

// A trivial group step: queues one choice per active player, and counts how many times its
// `done` fires. `done` is invoked automatically by GroupActions when the step's pending queue
// empties -- which is the behaviour under test.
class TrialStep extends BaseGroupStep {
  static int doneCount = 0
  void run(Object... args) {
    group.activePlayers.each { p -> a.add(p, [name: 'go'], { v, data -> }) }
  }
  void done(Object... args) { doneCount++ }
}

// Like TrialStep, but records the id of every group whose `done` fires. TrialStep.doneCount is a
// single shared counter and can't tell two groups apart; this can, so isolation tests can assert
// WHICH group completed.
class RecordingStep extends BaseGroupStep {
  static List doneGroups = []
  void run(Object... args) {
    group.activePlayers.each { p -> a.add(p, [name: 'go'], { v, data -> }) }
  }
  void done(Object... args) { doneGroups << group.id }
}

// Simulate a player picking their queued choice. This drives the real client path: a CustomEvent
// on the bus -> events.groovy routes it to the player-scoped listener GroupActions installed.
// The uid is passed in (not read from p.choices here) so callers can read it on the main thread --
// reading the choice map from a timer thread races with the main thread that wrote it.
def submit = { p, uid ->
  events.emit("CustomEvent",
    [playerId: p.id, eventName: GroupActions.SUBMIT_EVENT, data: [uid: uid]],
    [clientId: p.id])
}

test("done fires once when every queued choice is submitted") {
  GroupRegistry.bindContext([g: g, a: a])
  def p1 = g.addPlayer('p1')
  def p2 = g.addPlayer('p2')
  def group = new Group('grp1', [p1, p2])
  group.register('trial', TrialStep)
  group.start('trial')

  assert group.a.pendingCount() == 2        // both players have a pending choice
  submit(p1, p1.choices[0].uid)
  assert group.a.pendingCount() == 1         // one resolved, step not done yet
  submit(p2, p2.choices[0].uid)
  assert group.a.pendingCount() == 0         // queue empty -> done should have fired
  assert TrialStep.doneCount == 1
}

test("dispose clears the groupId tag from its players") {
  def p1 = g.addPlayer('d1')
  def group = new Group('grpD', [p1])
  assert p1._system.groupId == 'grpD'        // Group tags each player on construction
  group.dispose()
  assert p1._system.groupId == null          // ...and untags them on dispose
}

// The core design claim of GroupActions is that state lives per-group: two groups in the same
// engine must not share a pending queue, and resolving one must not fire the other's `done`.
test("two groups keep independent queues; resolving one fires done only for that group") {
  GroupRegistry.bindContext([g: g, a: a])
  def a1 = g.addPlayer('iso-a1')
  def a2 = g.addPlayer('iso-a2')
  def b1 = g.addPlayer('iso-b1')
  def gA = new Group('grpIsoA', [a1, a2])
  def gB = new Group('grpIsoB', [b1])
  gA.register('trial', RecordingStep); gA.start('trial')
  gB.register('trial', RecordingStep); gB.start('trial')

  assert gA.a.pendingCount() == 2
  assert gB.a.pendingCount() == 1

  submit(a1, a1.choices[0].uid)
  submit(a2, a2.choices[0].uid)              // group A fully resolved

  assert gA.a.pendingCount() == 0
  assert gB.a.pendingCount() == 1             // group B's queue is untouched by A's resolution
  assert RecordingStep.doneGroups == ['grpIsoA']   // ...and only A's step completed
}

test("register rejects a duplicate step name") {
  def group = new Group('grpDup', [g.addPlayer('dup1')])
  group.register('trial', TrialStep)
  def threw = false
  try { group.register('trial', TrialStep) } catch (IllegalStateException e) { threw = true }
  assert threw : 'registering the same step name twice must throw'
}

test("start rejects an unknown step name") {
  def group = new Group('grpUnknown', [g.addPlayer('unk1')])
  def threw = false
  try { group.start('nope') } catch (IllegalArgumentException e) { threw = true }
  assert threw : 'starting a step that was never registered must throw'
}

test("add before any step has started throws") {
  def p1 = g.addPlayer('pre1')
  def group = new Group('grpPre', [p1])
  // No step started yet -> _currentStepName is null -> add() must refuse to queue a choice.
  def threw = false
  try { group.a.add(p1, [name: 'x'], { v, data -> }) } catch (IllegalStateException e) { threw = true }
  assert threw : 'add() before a step starts must throw'
}

test("GroupRegistry resolves a player's group and forgets it on remove") {
  def p1 = g.addPlayer('reg1')
  def stray = g.addPlayer('stray')           // a vertex never added to any group
  def group = new Group('grpReg', [p1])
  GroupRegistry.put(group)

  assert GroupRegistry.get('grpReg').is(group)
  assert GroupRegistry.forPlayer(p1).is(group)   // resolved via p1._system.groupId
  assert GroupRegistry.forPlayer(stray) == null  // ungrouped player -> no group

  GroupRegistry.remove('grpReg')
  assert GroupRegistry.get('grpReg') == null
}

// Proves the runner's async plumbing: the body returns immediately, and the assertion only runs
// after the timer fires and calls done() (the harness blocks on done up to the timeout). The
// group choice->done flow itself is covered by the synchronous test above.
test.async("async case waits for done() fired from a timer callback", 4000) { done ->
  def ticked = new java.util.concurrent.atomic.AtomicBoolean(false)
  timers.newTimer().runAfter(50) {
    ticked.set(true)
    done { assert ticked.get() }   // assertion runs on completion, captured by the harness
  }
}

// Group choices submitted from a java.util.Timer thread must resolve the queue and fire done, exactly
// like the synchronous case. This exercises the real cross-thread path: a timer callback (on Timer-N)
// emits CustomEvent on the bus -> events.groovy routes it to the player-scoped listener that
// GroupActions installed on the eval thread. There is NO thread-binding hazard: traced with identity
// hashes, `events` and `g` are the SAME instances on the eval thread and the timer thread (gremlin-groovy
// 2.5.0 resolves a bare `events` against a FIXED ScriptContext captured at closure-creation, regardless
// of calling thread), g.getVertex works on the timer thread, and EventBus is thread-safe (ConcurrentHashMap
// of CopyOnWriteArrayList). The previous "different EventBus on the timer thread" was a test-harness
// artifact with NOTHING to do with threads: Vertex.metaClass.on (events.groovy) is a JVM-global metaclass
// mutation whose closure permanently captures the FIRST harness's `events`; with a per-harness `new
// EventBus()`, every harness after the first registered player.on(...) listeners on the first harness's
// dead bus while emitting on its own -> no delivery. Fixed by sharing one cleared EventBus across
// harnesses (ScriptTestHarness). Production is unaffected: one static EventBus for the engine's life.
test.async("group choices submitted from a timer thread resolve and fire done", 4000) { done ->
  GroupRegistry.bindContext([g: g, a: a])
  def p1 = g.addPlayer('tp1')
  def p2 = g.addPlayer('tp2')
  def group = new Group('grpTimer', [p1, p2])
  group.register('trial', TrialStep)
  def before = TrialStep.doneCount
  group.start('trial')

  // Read the uids on THIS (eval) thread -- the choice maps are written here; the timer callback only emits.
  def uid1 = p1.choices[0].uid
  def uid2 = p2.choices[0].uid
  assert group.a.pendingCount() == 2

  timers.newTimer().runAfter(50) {
    submit(p1, uid1)
    submit(p2, uid2)
    done {
      assert group.a.pendingCount() == 0          // queue drained from the timer thread
      assert TrialStep.doneCount == before + 1     // done fired exactly once
    }
  }
}

// A player who drops mid-step (a.remove) must have their pending choice cancelled, and once that
// empties the queue the step completes -- exactly as if they had submitted. This needs only
// GroupActions.remove(player), NOT the unwired `removePlayers` global (that's only used by
// Group.dropPlayers), so it's testable now.
test("removing a player mid-step drains their choice and lets the step complete") {
  GroupRegistry.bindContext([g: g, a: a])
  def p1 = g.addPlayer('rm1')
  def p2 = g.addPlayer('rm2')
  def group = new Group('grpRemove', [p1, p2])
  group.register('trial', TrialStep)
  def before = TrialStep.doneCount
  group.start('trial')

  assert group.a.pendingCount() == 2
  submit(p1, p1.choices[0].uid)              // p1 answers normally
  assert group.a.pendingCount() == 1          // p2 still outstanding...
  assert TrialStep.doneCount == before         // ...so done has NOT fired yet

  group.a.remove(p2)                          // p2 drops; their queued choice is cancelled
  assert group.a.pendingCount() == 0           // queue now empty
  assert TrialStep.doneCount == before + 1      // ...so the step completed exactly once
}
