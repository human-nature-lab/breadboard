import com.tinkerpop.blueprints.Vertex
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class BaseChatManager extends BreadboardBase {}

BaseChatManager.metaClass.getVertex = { id ->
  return g.getVertex(id)
}

class ChatManager extends BaseChatManager {
  private mut = new ReentrantLock()
  private chatLocks = [:]
  public CHAT_KEY = "chatState"
  public CHAT_EVENT = "chat"

  private withMut(Closure cb) {
    this.mut.lock()
    try {
      return cb()
    } finally {
      this.mut.unlock()
    }
  }

  private withPlayerChatLock(String playerId, Closure cb) {
    def self = this
    def lock = this.withMut() {
      if (!(playerId in self.chatLocks)) {
        self.chatLocks[playerId] = new ReentrantLock()
      }
      return self.chatLocks[playerId]
    }
    lock.lock()
    try {
      return cb()
    } finally {
      lock.unlock()
    }
  }

  private chatHandler (player, data) {
    def self = this
    this.withPlayerChatLock(player.id){
      // Stop listening to this event if the chat has been disabled
      if (!player.private[self.CHAT_KEY]) {
        return disableTextChat(player)
      }

      def chatState = player.private[self.CHAT_KEY]
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
        self.addEvent(self.CHAT_EVENT, msg)
      }
      
      self._pushMessage(player, msg)    
      
      recipientIds.each{ id ->
        // Make sure the player is allowed to talk with this player before sending the message to them
        if (id && id in chatState.allowedRecipients) {
          def v = self.getVertex(id)
          if (v && v.private[self.CHAT_KEY]) {
            self.pushMessage(v, msg)
          }
        }
      } 
    }
  }

  public enableTextChat (Vertex v, Map opts = [:]) {
    def self = this
    println "start enable text chat for " + v.id
    return this.withPlayerChatLock(v.id) {
       println "enabling text chat for " + v.id
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
        
        self.addEvent("chat-enabled", opts)
        
        v.private[self.CHAT_KEY] = [
          isEnabled: true,
          recordEvents: opts.recordEvents,
          allowedRecipients: opts.recipients,
          maxLength: opts.maxLength,
          messageBufferSize: opts.messageBufferSize,
          messages: []
        ]
        
        v.on(self.CHAT_EVENT, self.&chatHandler)
      } catch (err) {
        println err.getMessage()
        throw err
      }
      println "end enable text chat for " + v.id
    }
  }

  public disableTextChat (Vertex v, removeExisting = true) {
    def self = this
    return this.withPlayerChatLock(v.id) {
      if (removeExisting && "private" in v && self.CHAT_KEY in v.private) {
        v.private.remove(self.CHAT_KEY)
      }
      v.off(self.CHAT_EVENT, self.&chatHandler)
      self.addEvent("chat-removed", [player: v.id])
    }
  }

  public pushMessage (Vertex recipient, Map msg) {
    def self = this
    return this.withPlayerChatLock(recipient.id) {
      return self._pushMessage(recipient, msg)
    }
  }

  private _pushMessage (Vertex recipient, Map msg) {
    def chatState = recipient.private[this.CHAT_KEY]
    def messages = chatState.messages
    messages << msg
    // Check if we've exceeded message buffer size
    if (messages.size() > chatState.messageBufferSize) {
      messages.remove(0)
    }
  }
  
}