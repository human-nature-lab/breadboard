import com.tinkerpop.blueprints.Vertex

final CUSTOM_EVENT = "CustomEvent"
final PLAYER_ACTION_PROP = "eventName"
final PLAYER_DATA_PROP = "data"
final PLAYER_ID_PROP = "playerId"
final SEND_EVENT = "__send-event"

def makePlayerEventHash (String id, String eventName) {
  return "__player-" + id + "-" + eventName
}

Vertex.metaClass.playerEvents = [].toSet()
Vertex.metaClass.on = { String eventName, Closure cb ->
  try {
    def globalEventName = makePlayerEventHash(delegate.id, eventName)
    delegate.playerEvents.add(eventName)
    events.on(globalEventName, cb)
  } catch (Exception e) {
    e.printStackTrace()
  }
}
// Method overloads
Vertex.metaClass.off = { String eventName, Closure cb ->
  delegate.playerEvents.remove(eventName)
  events.off(makePlayerEventHash(delegate.id, eventName), cb)  
}
Vertex.metaClass.off << { String eventName ->
  delegate.playerEvents.remove(eventName)
  events.off(makePlayerEventHash(delegate.id, eventName))
}
Vertex.metaClass.send = { String eventName, Object ...data -> 
  events.emit(SEND_EVENT, delegate.id, eventName, data)
}
Vertex.metaClass.clear = {
  playerId = delegate.id
  delegate.playerEvents.each{ String event ->
    events.off(makePlayerEventHash(playerId, event))
  }
}

/**
 * All events in breadboard use the global event bus to pass events between the 
 * ScriptBoard side and the groovy scripting scope. Player scoped events are 
 * routed using a simple hash of the player id and the event name. Player scoped
 * events are also passed in the player vertex as an argument.
 */ 
events.on(CUSTOM_EVENT, { params ->
  println "custom event to player event"
  try {
    if (PLAYER_ID_PROP in params && PLAYER_ACTION_PROP in params) {
      def player = g.getVertex(params[PLAYER_ID_PROP])
      if (player != null) {
        def globalEventName = makePlayerEventHash(player.id, params[PLAYER_ACTION_PROP])
        println "emit global event " + globalEventName
        events.emit(globalEventName, player, params[PLAYER_DATA_PROP])
      }
    }
  } catch (Exception e) {
    println "custom event exception"
    e.printStackTrace()
  }
})