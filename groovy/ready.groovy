import java.util.concurrent.locks.ReentrantLock

public class ReadyUpSequence extends FormBase {
  SharedTimer readyUpTimer
  def isStarted = false
  def hasFinished = false
  def key = "ready-up"
  def time = 30
  def mut = new ReentrantLock()
  def pendingContent = [
    content: "The game will begin shortly. When a “Ready” button appears, press it to begin the task."
  ]
  def readyContent = [
    content: "Press the \"Ready\" button before the timer expires to begin this task."
  ]
  def readyButtonContent = [
    content: "Ready"
  ]
  def timerContent = [
    content: "Game begins in "
  ]
  def waitingContent = [
    content: "The game will begin shortly. Please wait for other players."
  ]
  def players = []
  def doneCbs = []

  ReadyUpSequence () {}
  ReadyUpSequence (Map opts) {
    if (opts != null) return
    def keys = ["pendingContent", "readyContent", "readyButtonContent", "time", "timerContent", "waitingContent"]
    for (def key: keys) {
      if (key in opts) {
        this[key] = opts[key]
      }
    }
  }

  /**
   * Add a list of players at the same time
   */
  public addPlayers (players) {
    this.withLock(){
      players.each{
        this._addPlayer(it)
      }
    }
  }

  /**
   * Add a single player to the ready up sequence.
   */
  public addPlayer (Vertex player) {
    this.withLock(){
      this._addPlayer(player)
    }
  }

  private _addPlayer (Vertex player) {
    if (!this.mut.isLocked()) {
      throw new Exception("Must be called within a lock")
    }
    if (this.players.contains(player)) return
    this.players << player
    if (this.isStarted && !this.hasFinished) {
      this._startPlayer(player)
    } else {
      player.text = this.fetchContent(this.pendingContent)
    }
  }

  /**
   * Remove a player from the ready up sequence
   */
  public removePlayer (Vertex player) {
    this.withLock(){
      this.clearPlayer(player)
      this.players.removeElement(player)
    }
  }

  /**
   * Begin the ready up sequence by showing all players a timer and a "ready" button
   */
  public start () {
    this.withLock(){
      if (this.isStarted) return
      this.isStarted = true
      this.hasFinished = false
      this.readyUpTimer = new SharedTimer([
        content: this.timerContent,
        name: this.key,
        time: this.time,
        appearance: "error"
      ])
      this.readyUpTimer.onDone{
        this.end()
      }
      this.readyUpTimer.addPlayers(this.players)
      this.players.each{ player -> this._startPlayer(player)}
    }
  }

  
  private _startPlayer(Vertex player) {
    def buttonText = this.fetchContent(this.readyButtonContent)
    def readyText = this.fetchContent(this.readyContent)
    // LOCK MUST BE HELD WHILE CALLING
    if (!this.mut.isLocked()) {
      throw new Exception("Must be called within a lock")
    }
    player.text = readyText
    this.addAction(player, [
      name: buttonText,
      result: {
        this.onPlayerReady(player)
      }
    ])
  }

  /**
   * The result closure called when the player presses the ready button
   */
  private onPlayerReady (Vertex player) {
    this.withLock(){
      try {
        player.text = this.fetchContent(this.waitingContent)
        player._system.isReady = true

        // Check if all players are already ready
        for (def p: this.players) {
          if (!p._system.isReady) return
        }
        this._end() // only get here if all players are ready
      } catch (err) {
        println err.toString()
      }
    }
  }

  private withLock(Closure cb) {
    this.mut.lock()
    try {
      return cb()
    } finally {
      this.mut.unlock()
    }
  }

  /**
   * End the ready up sequence by cleaning up timers and actions
   */
  public end () {
    this.withLock() {
      this._end()
    }
  }

  private _end () {
    if (!this.mut.isLocked()) {
      throw new Exception("Must be called within a lock")
    }
    if (!this.isStarted) return
    this.isStarted = false
    this.hasFinished = true
    if (this.readyUpTimer) {
      this.readyUpTimer.cancel()
    }
    def readyPlayers = []
    def notReadyPlayers = []
    this.players.each{
      this.clearActions(it)

      if (it._system.isReady) {
        readyPlayers << it
      } else {
        notReadyPlayers << it
      }
    }

    for (def cb: this.doneCbs) {
      cb(readyPlayers, notReadyPlayers)
    }
  }

  /**
   * Pass a closure to be called when the ready up sequence is complete
   */
  def onDone (Closure cb) {
    this.doneCbs << cb
  }

}

ReadyUpSequence.metaClass.addAction = { Vertex player, HashMap... choices ->
  a.add(player, *choices)
}

ReadyUpSequence.metaClass.clearActions = { Vertex player -> 
  a.remove(player)
}