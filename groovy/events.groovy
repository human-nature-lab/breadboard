final CUSTOM_EVENT = "CustomEvent"
final PLAYER_ACTION_PROP = "eventName"
final PLAYER_DATA_PROP = "data"
final PLAYER_ID_PROP = "playerId"

/**
 * All events in breadboard use the global event bus to pass events between the 
 * ScriptBoard side and the groovy scripting scope. Player scoped events are 
 * routed using a simple hash of the player id and the event name. Player scoped
 * events are also passed in the player vertex as an argument.
 */ 
events.on(CUSTOM_EVENT, { params ->
  println "custom event to scoped event"
  if (PLAYER_ID_PROP in params && PLAYER_ACTION_PROP in params) {
    def player = g.getVertex(params[PLAYER_ID_PROP])
    if (player) {
      def globalEventName = makePlayerEventHash(params[PLAYER_ID_PROP], params[PLAYER_ACTION_PROP])
      events.emit(globalEventName, player, params[PLAYER_DATA_PROP])
    }
  }
})