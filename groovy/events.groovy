import com.tinkerpop.blueprints.Vertex
import java.util.concurrent.CopyOnWriteArrayList

final CUSTOM_EVENT = "CustomEvent"
final PLAYER_ACTION_PROP = "eventName"
final PLAYER_DATA_PROP = "data"
final PLAYER_ID_PROP = "playerId"
final SEND_EVENT = "__send-event"

// Player-event tracing is opt-in and off by default. Call enablePlayerEventLogging() from an
// experiment (or a test) to switch on the eventLog() traces emitted by the vertex event methods.
def _logPlayerEvents = false

enablePlayerEventLogging = {
  _logPlayerEvents = true
}

def makePlayerEventHash (String id, String eventName) {
  return "__player-" + id + "-" + eventName
}

eventLog = { Object ...vals ->
  if (_logPlayerEvents) {
    println vals.collect{ "${it}" }.join(" ")
  }
}

Vertex.metaClass.playerEvents = [].toSet()
Vertex.metaClass.on = { String eventName, Closure cb ->
  eventLog("vertex.on", delegate.id, eventName)
  try {
    def globalEventName = makePlayerEventHash(delegate.id, eventName)
    delegate.playerEvents.add(eventName)
    events.on(globalEventName, cb)
  } catch (Exception e) {
    e.printStackTrace()
  }
}
Vertex.metaClass.once = { String eventName, Closure cb ->
  eventLog("vertex.once", delegate.id, eventName)
  def internalClosure
  internalClosure = { Vertex v, Object ...data ->
    eventLog("vertex.once callback", v.id)
    v.off(eventName, internalClosure)
    cb(v, *data)
  }
  delegate.on(eventName, internalClosure)
}

// Method overloads
Vertex.metaClass.off = { String eventName, Closure cb ->
  wasRemoved = events.off(makePlayerEventHash(delegate.id, eventName), cb)
  if (wasRemoved) {
    delegate.playerEvents.remove(eventName)
  }
  eventLog("vertex.off 1", delegate.id, eventName, wasRemoved)
  return wasRemoved
}
Vertex.metaClass.off << { String eventName ->
  wasRemoved = events.off(makePlayerEventHash(delegate.id, eventName))
  if (wasRemoved) {
    delegate.playerEvents.remove(eventName)
  }
  eventLog("vertex.off 2", delegate.id, eventName, wasRemoved)
  return wasRemoved
}
Vertex.metaClass.send = { String eventName, Object ...data ->
  eventLog("vertex.send", delegate.id, eventName)
  events.emit(SEND_EVENT, delegate.id, eventName, data)
}
Vertex.metaClass.clearListeners = {
  eventLog("vertex.clearListeners", delegate.id)
  playerId = delegate.id
  delegate.playerEvents.toList().each{ String event ->
    delegate.off(event)
  }
}

clearAllPlayerListeners = {
  eventLog("clearing all player listeners")
  g.V.each{
    it.clearListeners()
  }
}


/**
 * All events in breadboard use the global event bus to pass events between the 
 * ScriptBoard side and the groovy scripting scope. Player scoped events are 
 * routed using a simple hash of the player id and the event name. Player scoped
 * events are also passed in the player vertex as an argument.
 */ 
events.on(CUSTOM_EVENT, { Map params, Map clientData ->
  try {
    if (PLAYER_ID_PROP in params && PLAYER_ACTION_PROP in params) {
      def playerId = params[PLAYER_ID_PROP]
      if (clientData.clientId != playerId) {
        println "Hacking attempt thwarted! Player " + clientData.clientId + " tried to impersonate " + playerId
        return
      }
      def player = g.getVertex(playerId)
      if (player != null) {
        def globalEventName = makePlayerEventHash(player.id, params[PLAYER_ACTION_PROP])
        events.emit(globalEventName, player, params[PLAYER_DATA_PROP])
      }
    }
  } catch (Exception e) {
    println "custom event exception"
    e.printStackTrace()
  }
})


// ---------------------------------------------------------------------------
// Engine-reload lifecycle hooks
// ---------------------------------------------------------------------------
// Closures registered via onBeforeEngineReload run just before the script engine is torn down and
// rebuilt: ScriptBoard.resetEngine calls _runBeforeEngineReloadHooks() as the first thing it does
// on a reload, while g / a / timers are still live. Use them to release resources the platform does
// NOT clean up automatically -- e.g. stop a WaitingRoom, dispose Games, close external connections.
// (Timers created through BBTimer / SharedTimer are already cancelled by timers.cancel() on reload,
// so they do not need a hook.)
//
// CopyOnWriteArrayList: hooks are registered from experiment / socket threads while the actor thread
// runs them on reload.
_beforeEngineReloadHooks = new CopyOnWriteArrayList()

// Register a closure to run before the next engine reload. Returns the closure so the caller can
// retain / identify it.
onBeforeEngineReload = { Closure c ->
  if (c != null) {
    _beforeEngineReloadHooks.add(c)
  }
  return c
}

// Run every registered before-reload hook in registration order, then clear the list. Each hook is
// guarded: a throwing hook is logged (with Groovy stack frames humanized back to real file names)
// and does NOT stop the remaining hooks. A snapshot is taken before running so a hook that registers
// another hook can't mutate the list mid-iteration (the new hook runs on the next reload instead).
_runBeforeEngineReloadHooks = {
  def hooks = new ArrayList(_beforeEngineReloadHooks)
  _beforeEngineReloadHooks.clear()
  for (def hook in hooks) {
    try {
      hook()
    } catch (Throwable t) {
      models.ScriptLoader.humanizeStackTrace(t)
      play.Logger.error("Error in onBeforeEngineReload hook", t)
    }
  }
}