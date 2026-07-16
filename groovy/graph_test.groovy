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
