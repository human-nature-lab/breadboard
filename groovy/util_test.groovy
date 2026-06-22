// Tests for the frontend-config helpers in util.groovy (configureFrontend, kickPlayers), written as
// Groovy and registered through the `test` DSL bound by ScriptTestHarness. Production's ScriptLoader
// skips any file ending in `_test.groovy`, so this never loads into a real experiment. Run via:
//   sbt -java-home "$JAVA8_HOME" "testOnly GroovyScriptTests"
//
//   test("name") { ... }                            -> synchronous
//   test.async("name", timeoutMs) { done -> ... }   -> async; signal completion with done()
//   test.skip("name") { ... }                       -> reported as ignored, not run
//
// `g`, `a`, `events` etc. are the same engine bindings the platform scripts see; configureFrontend,
// kickPlayers and _ensureSystem come from util.groovy (a core script, already loaded). `_system`
// reads (`v._system...`) and `v.id` are safe; only `v.<gremlin-pipe-method>` reads (e.g. `v.step`)
// throw.
//
// Groovy 1.8.6 target: no closure->functional-interface coercion, no lambdas / `::` refs.

// --- configureFrontend ---------------------------------------------------------------------------

test("configureFrontend writes the prolific flag into _system.frontend") {
  // Guards the variable-shadowing fix: the opts must actually be read from the parameter. (The old
  // `FrontendConfigOpts opts = opts as FrontendConfigOpts` self-reference produced a null config.)
  def v = g.addPlayer('cf-prolific-1')
  configureFrontend(v, [prolific: true])
  assert v._system.frontend.prolific == true
  assert v._system.frontend.trackScreen != true   // not requested -> not enabled
}

test("configureFrontend enables screen tracking when trackScreen is set") {
  def v = g.addPlayer('cf-track-1')
  // trackScreen:true makes configureFrontend call trackPlayerScreen(), which registers on the global
  // `player`; bind it to this vertex so the registration target exists in the test engine.
  player = v
  configureFrontend(v, [trackScreen: true])
  assert v._system.frontend.trackScreen == true
  assert v._system.frontend.prolific != true
}

// --- kickPlayers ---------------------------------------------------------------------------------

test("kickPlayers marks _system.frontend.kicked so the client redirect fires") {
  def v = g.addPlayer('kick-1')
  kickPlayers(v.id)
  assert v._system.frontend.kicked == true
}

test("kickPlayers handles several players in one call") {
  def a1 = g.addPlayer('kick-multi-a')
  def b1 = g.addPlayer('kick-multi-b')
  kickPlayers(a1.id, b1.id)
  assert a1._system.frontend.kicked == true
  assert b1._system.frontend.kicked == true
}
