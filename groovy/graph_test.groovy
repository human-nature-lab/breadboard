// Tests for BreadboardGraph.addTimer's player-selection contract (graph.groovy). Registered through
// the `test` DSL bound by ScriptTestHarness; ScriptLoader skips the `_test.groovy` suffix in
// production. Run via:
//   sbt -java-home "$JAVA8_HOME" "testOnly GroovyScriptTests"
//
// The contract under test: the SharedTimer constructor already attaches opts.player / opts.players,
// so addTimer must only fall back to "everyone" (g.V) when NEITHER was supplied. In particular a
// passed-in `players:` subset must not be clobbered with all vertices, and the positional
// addTimer(time, ...) helper -- which always builds a map with a `player: null` key -- must still
// mean "add everyone".
//
// `g` is a fresh, empty BreadboardGraph per case (graph.groovy's top-level `g = new BreadboardGraph`
// is re-eval'd on every harness reset), so g.V holds exactly the players a test adds. Each timer is
// created with a long (3600s) duration so nothing fires mid-test, and cancelled in a finally so the
// shared-timer registry returns to baseline.
//
// Groovy 1.8.6 target: no closure->functional-interface coercion, no lambdas / `::` refs.

// --- a passed-in subset must be respected -------------------------------------------------------

test("g.addTimer(players: subset) attaches only the passed players, not all of g.V") {
  def a = g.addPlayer('at-sub-a')
  def b = g.addPlayer('at-sub-b')
  def c = g.addPlayer('at-sub-c')
  def t = g.addTimer([time: 3600, name: 'at-sub', players: [a, b]])
  try {
    assert t.players*.id as Set == ['at-sub-a', 'at-sub-b'] as Set :
      "only the passed players should belong to the timer; got ${t.players*.id}"
    assert !t.hasPlayer(c) : "a player left out of the players list must not be added"
    assert g.V.count() == 3 : "sanity: the graph really does hold a third, unattached player"
  } finally {
    t.cancel()
  }
}

// --- a single player must be respected ----------------------------------------------------------

test("g.addTimer(player: one) attaches only that player") {
  def a = g.addPlayer('at-one-a')
  def b = g.addPlayer('at-one-b')
  def t = g.addTimer([time: 3600, name: 'at-one', player: a])
  try {
    assert t.players*.id == ['at-one-a'] : "only the single passed player should be attached; got ${t.players*.id}"
    assert !t.hasPlayer(b)
  } finally {
    t.cancel()
  }
}

// --- neither supplied => everyone ---------------------------------------------------------------

test("g.addTimer with no player/players falls back to every vertex in g.V") {
  def a = g.addPlayer('at-all-a')
  def b = g.addPlayer('at-all-b')
  def c = g.addPlayer('at-all-c')
  def t = g.addTimer([time: 3600, name: 'at-all'])
  try {
    assert t.players*.id as Set == ['at-all-a', 'at-all-b', 'at-all-c'] as Set :
      "with neither player nor players supplied, the timer should cover all of g.V; got ${t.players*.id}"
  } finally {
    t.cancel()
  }
}

// --- a null player key still means "everyone" (positional-helper contract) ----------------------

test("g.addTimer(player: null) still falls back to all of g.V") {
  // The positional addTimer(time, ...) helper always builds a map that carries a `player: null` key;
  // a null player must mean "everyone", not "no one" -- checking the value, not just key presence.
  def a = g.addPlayer('at-null-a')
  def b = g.addPlayer('at-null-b')
  def t = g.addTimer([time: 3600, name: 'at-null', player: null])
  try {
    assert t.players*.id as Set == ['at-null-a', 'at-null-b'] as Set :
      "a null player key must fall back to all of g.V; got ${t.players*.id}"
  } finally {
    t.cancel()
  }
}

test("the positional g.addTimer(time) helper attaches all of g.V") {
  def a = g.addPlayer('at-pos-a')
  def b = g.addPlayer('at-pos-b')
  def t = g.addTimer(3600)   // positional overload -> builds a map with player: null internally
  try {
    assert t.players*.id as Set == ['at-pos-a', 'at-pos-b'] as Set :
      "the positional helper (no player arg) should cover all of g.V; got ${t.players*.id}"
  } finally {
    t.cancel()
  }
}

// --- models.ConcurrentObservableMap: the private/inProps/outProps maps ---------------------------
//
// Vertex `private` vars and edge inProps/outProps are backed by models.ConcurrentObservableMap
// (a ConcurrentHashMap-backed groovy.util.ObservableMap) instead of the default LinkedHashMap. Two
// behaviours are pinned here:
//   1. Concurrent iterate-while-mutate must not throw ConcurrentModificationException. This is the
//      v2.5.0 field bug: a mutation (e.g. a timer callback on its own thread) fires a change event
//      whose listener chain (Admin.vertexPropertyChanged) walks the SAME map's keySet to serialize
//      it; with a LinkedHashMap that race threw CME in LinkedHashMap$LinkedHashIterator.
//   2. Writing null (`v.private.foo = null`) must not blow up: a raw ConcurrentHashMap forbids null
//      values, so the map treats a null write as a remove, preserving the old read-back-as-null.
// Reverting graph.groovy's `new models.ConcurrentObservableMap()` sites to `[:] as ObservableMap`
// makes the wiring assert fail and the concurrency case throw CME -- i.e. these are real regression
// guards, not just smoke tests.

test("vertex 'private' and edge inProps/outProps are the ConcurrentHashMap-backed ObservableMap") {
  def a = g.addPlayer('wire-a')
  def b = g.addPlayer('wire-b')
  assert a.private instanceof models.ConcurrentObservableMap :
    "vertex 'private' should be a models.ConcurrentObservableMap; got ${a.private?.getClass()?.name}"

  def e = g.addEdge(a, b, 'connected')
  assert e.getProperty('inProps') instanceof models.ConcurrentObservableMap :
    "edge inProps should be a models.ConcurrentObservableMap; got ${e.getProperty('inProps')?.getClass()?.name}"
  assert e.getProperty('outProps') instanceof models.ConcurrentObservableMap :
    "edge outProps should be a models.ConcurrentObservableMap; got ${e.getProperty('outProps')?.getClass()?.name}"
}

test("a vertex 'private' map survives concurrent mutation while it is being iterated (CME regression)") {
  def player = g.addPlayer('cme-iter')
  def priv = player.private
  (0..<25).each { priv["seed" + it] = it }   // give the iterator entries to walk

  def errors = new java.util.concurrent.CopyOnWriteArrayList()
  def stop = new java.util.concurrent.atomic.AtomicBoolean(false)

  // A second thread structurally modifies the same map -- standing in for a timer callback touching
  // private vars off the actor thread while the serialization loop below walks it.
  def writer = Thread.start {
    int n = 0
    try {
      while (!stop.get()) {
        priv["w" + (n % 40)] = n
        priv.remove("w" + ((n + 20) % 40))
        n++
      }
    } catch (Throwable t) {
      errors << t
    }
  }

  // Main thread reproduces Admin.vertexPropertyChanged: iterate keySet() and read each value. On a
  // LinkedHashMap backing this throws ConcurrentModificationException within milliseconds; on the
  // ConcurrentHashMap backing the weakly-consistent iterator never throws.
  try {
    long deadline = System.currentTimeMillis() + 250
    while (System.currentTimeMillis() < deadline && errors.isEmpty()) {
      for (Object k : priv.keySet()) {
        priv.get(k)
      }
    }
  } catch (Throwable t) {
    errors << t
  } finally {
    stop.set(true)
    writer.join(5000)
  }

  assert errors.isEmpty() :
    "concurrent mutate-while-iterate on a vertex 'private' map must not throw; got: " +
    errors.collect { it.getClass().name + ": " + it.message }
}

test("setting a vertex 'private' var to null removes it instead of throwing (CHM forbids null values)") {
  def player = g.addPlayer('null-guard')
  player.private.keepMe = 'stays'
  player.private.dropMe = 'goes'
  player.private.dropMe = null    // a raw ConcurrentHashMap would NPE here; the guard makes it a remove

  assert player.private.dropMe == null : "a private var set to null reads back as null"
  assert !player.private.containsKey('dropMe') : "a private var set to null is removed from the map"
  assert player.private.keepMe == 'stays' : "nulling one var must not disturb the others"
}

test("putAll into a vertex 'private' map routes null values through the write guard") {
  def player = g.addPlayer('null-putall')
  player.private.putAll([a: 1, b: null, c: 3])   // the null must not reach the backing ConcurrentHashMap

  assert player.private.a == 1 : "non-null entries in a putAll batch are stored"
  assert player.private.c == 3 : "non-null entries in a putAll batch are stored"
  assert player.private.b == null : "a null-valued entry reads back as null"
  assert !player.private.containsKey('b') : "a null value in a putAll batch is dropped, not stored"
}
