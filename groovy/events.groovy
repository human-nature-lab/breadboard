def CUSTOM_PLAYER_EVENT = "CustomPlayerEvent"
def CUSTOM_EVENT = "CustomEvent"
events.register(CUSTOM_EVENT)
events.register(CUSTOM_PLAYER_EVENT)
events.on(CUSTOM_EVENT, { params ->
  if ("playerId" in params) {
    def player = g.getVertex(params["playerId"])
    if (player) {
      events.emit(CUSTOM_PLAYER_EVENT, player, params)
    }
  }
})