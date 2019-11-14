import com.tinkerpop.blueprints.Vertex

CHAT_EVENT = "chat"
chatHandler = { player, data ->
  // Stop listening to this event if the chat has been disabled
  if (!player.private.textInput) {
    player.off(CHAT_EVENT, chatHandler)
    return
  }

  def ctrl = player.private.textInput
  def text = data["text"]
  def recipientIds = data["recipients"]
  if (text.length() == 0 || recipientIds.size() == 0) {
    return
  }
  // Check if we've exceeded text max length
  if (text.length() > ctrl.maxLength) {
    text = text.substring(0, ctrl.maxLength)
  }
  def msg = [
    sender: player.id,
    recipients: recipientIds,
    text: text
  ]

  println msg.toString()

  if (ctrl.recordEvents) {
    a.addEvent(CHAT_EVENT, msg)
  }
  
  pushMessage(player, msg)    
  
  recipientIds.each{ id ->
    // Make sure the player is allowed to talk with this player before sending the message to them
    if (id && id in ctrl.allowedRecipients) {
      def v = g.getVertex(id)
      if (v && v.private.textInput) {
        pushMessage(v, msg)
      }
    }
  } 
}

def enableTextChat (Vertex v, Map opts = [:]) {
  println "enable text chat for " + v.id
  try {
    def defaultOpts = [maxLength: 255, messageBufferSize: 10, recordEvents: true]
    opts = defaultOpts + opts

    if (opts.maxLength > 255) {
      println "Messages longer than 255 characters will get cutoff by the database"
    }

    // Automatically allow all neighbors to be recipients if none are specified
    def recipients = opts.recipients
    if (!recipients) {
      recipients = v.neighbors.toList().collect{ it.id }
    }
    println "allowed recipients: " + recipients.toString()
    
    v.private.textInput = [
      isEnabled: true,
      recordEvents: opts.recordEvents,
      allowedRecipients: recipients,
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
  if (removeExisting && "private" in v && "textInput" in v.private) {
    v.private.textInput = null
  }
  v.off(CHAT_EVENT, chatHandler)
}

pushMessage = { recipient, msg ->
  def textInput = recipient.private.textInput
  def messages = textInput.messages
  messages << msg
  // Check if we've exceeded message buffer size
  if (messages.size() > textInput.messageBufferSize) {
    messages.remove(0)
  }
}
