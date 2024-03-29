import com.tinkerpop.blueprints.Vertex

CHAT_KEY = "chatState"
CHAT_EVENT = "chat"
chatHandler = { player, data ->
  // Stop listening to this event if the chat has been disabled
  if (!player.private[CHAT_KEY]) {
    return disableTextChat(player)
  }

  def chatState = player.private[CHAT_KEY]
  def text = data["text"]
  def recipientIds = data["recipients"]
  if (text.length() == 0 || recipientIds.size() == 0) {
    return
  }
  // Check if we've exceeded text max length
  if (text.length() > chatState.maxLength) {
    text = text.substring(0, chatState.maxLength)
  }
  def msg = [
    sender: player.id,
    recipients: recipientIds,
    text: text
  ]
  println "chat " + msg

  if (chatState.recordEvents) {
    a.addEvent(CHAT_EVENT, msg)
  }
  
  pushMessage(player, msg)    
  
  recipientIds.each{ id ->
    // Make sure the player is allowed to talk with this player before sending the message to them
    if (id && id in chatState.allowedRecipients) {
      def v = g.getVertex(id)
      if (v && v.private[CHAT_KEY]) {
        pushMessage(v, msg)
      }
    }
  } 
}

def enableTextChat (Vertex v, Map opts = [:]) {
  try {
    def defaultOpts = [maxLength: 255, messageBufferSize: 10, recordEvents: true]
    opts = defaultOpts + opts

    if (opts.maxLength > 255) {
      println "Messages longer than 255 characters will get cutoff by the database"
    }

    // Automatically allow all neighbors to be recipients if none are specified
    def recipients = opts.recipients
    if (!opts.recipients) {
      opts.recipients = v.neighbors.toList().collect{ it.id }
    }
    println "enable text chat for " + v.id + " to " + opts.recipients
    
    a.addEvent("chat-enabled", opts)
    
    v.private[CHAT_KEY] = [
      isEnabled: true,
      recordEvents: opts.recordEvents,
      allowedRecipients: opts.recipients,
      maxLength: opts.maxLength,
      messageBufferSize: opts.messageBufferSize,
      messages: []
    ]
    
    v.on(CHAT_EVENT, chatHandler)
  } catch (err) {
    println err.getMessage()
    throw err
  }
}

def disableTextChat (Vertex v, removeExisting = true) {
  if (removeExisting && "private" in v && CHAT_KEY in v.private) {
    v.private.remove(CHAT_KEY)
  }
  v.off(CHAT_EVENT, chatHandler)
  a.addEvent("chat-removed", [player: v.id])
}

pushMessage = { recipient, msg ->
  def chatState = recipient.private[CHAT_KEY]
  def messages = chatState.messages
  messages << msg
  // Check if we've exceeded message buffer size
  if (messages.size() > chatState.messageBufferSize) {
    messages.remove(0)
  }
}
