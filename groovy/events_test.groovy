// Tests for the engine-reload lifecycle hooks in events.groovy (onBeforeEngineReload /
// _runBeforeEngineReloadHooks). events.groovy is a core script, already loaded. In production
// ScriptBoard.resetEngine calls _runBeforeEngineReloadHooks() just before tearing the engine down;
// here we drive that call directly. The hook list (_beforeEngineReloadHooks) is a shared binding
// global, so each test clears it first to stay independent of ordering. Run via:
//   sbt -java-home <jdk8> "testOnly GroovyScriptTests"
//
// Binding closures are invoked with parens (onBeforeEngineReload({...}) / _runBeforeEngineReloadHooks()),
// matching how util_test drives configureFrontend / kickPlayers.
//
// Groovy 1.8.6 target: no closure->functional-interface coercion, no lambdas / `::` refs.

test("onBeforeEngineReload hooks fire in registration order, then the list is cleared") {
  _beforeEngineReloadHooks.clear()
  def order = []
  onBeforeEngineReload({ order << 'a' })
  onBeforeEngineReload({ order << 'b' })
  onBeforeEngineReload({ order << 'c' })

  _runBeforeEngineReloadHooks()
  assert order == ['a', 'b', 'c']
  assert _beforeEngineReloadHooks.isEmpty()    // cleared after running

  // A second reload with nothing newly registered fires nothing more.
  _runBeforeEngineReloadHooks()
  assert order == ['a', 'b', 'c']
}

test("a throwing onBeforeEngineReload hook does not stop the remaining hooks") {
  _beforeEngineReloadHooks.clear()
  def ran = []
  onBeforeEngineReload({ ran << 1 })
  onBeforeEngineReload({ throw new RuntimeException("boom") })   // logged + swallowed
  onBeforeEngineReload({ ran << 3 })

  _runBeforeEngineReloadHooks()
  assert ran == [1, 3]                          // the throwing hook didn't abort the others
  assert _beforeEngineReloadHooks.isEmpty()
}

test("onBeforeEngineReload returns the registered closure and ignores null") {
  _beforeEngineReloadHooks.clear()
  def c = { }
  def returned = onBeforeEngineReload(c)
  assert returned.is(c)
  assert _beforeEngineReloadHooks.size() == 1

  onBeforeEngineReload(null)                    // null is ignored, not added
  assert _beforeEngineReloadHooks.size() == 1

  _beforeEngineReloadHooks.clear()              // don't leave hooks registered for other tests
}
