public class ReadyUpSequence extends FormBase {
  def readyUpTimer
  def isStarted = false
  def key = "ready-up"
  def time = 30
  def pendingContent = [
    content: "The game will begin shortly. When a “Ready” button appears, press it to begin the task."
  ]
  def readyContent = [
    content: "Press the \"Ready\" button."
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
    players.each{
      this.addPlayer(it)
    }
  }

  /**
   * Add a single player to the ready up sequence.
   */
  public addPlayer (Vertex player) {
    if (this.players.contains(player)) return
    this.players << player
    player.text = this.fetchContent(this.pendingContent)
  }

  /**
   * Remove a player from the ready up sequence
   */
  public removePlayer (Vertex player) {
    this.players.removeElement(player)
  }

  /**
   * Begin the ready up sequence by showing all players a timer and a "ready" button
   */
  public start () {
    if (this.isStarted) return
    this.isStarted = true
    this.readyUpTimer = new Timer()
    this.readyUpTimer.runAfter(this.time * 1000) {
      this.end()
    }
    def timer = [
      type: "time",
      direction: "down",
      elapsed: 0,
      timerText: this.fetchContent(this.timerContent),
      duration: this.time * 1000,
      order: 0,
      name: this.key
    ]
    def updateRate = 1000
    this.readyUpTimer.scheduleAtFixedRate({
      timer.elapsed += updateRate
    } as GroovyTimerTask, 0, updateRate)
    def readyText = this.fetchContent(this.readyContent)
    def buttonText = this.fetchContent(this.readyButtonContent)
    this.players.each{ player -> 
      if (player.timers == null) {
        player.timers = [:]
      }
      player.timers[this.key] = timer
      player.text = readyText
      this.addAction(player, [
        name: buttonText,
        result: {
          this.onPlayerReady(player)
        }
      ])
    }
  }

  /**
   * The result closure called when the player presses the ready button
   */
  private onPlayerReady (Vertex player) {
    try {
      player.text = this.fetchContent(this.waitingContent)
      player._system.isReady = true

      // Check if all players are already ready
      for (def p: this.players) {
        if (!p._system.isReady) return
      }
      this.end() // only get here if all players are ready
    } catch (err) {
      println err.toString()
    }
    
  }

  /**
   * End the ready up sequence by cleaning up timers and actions
   */
  public end () {
    if (!this.isStarted) return
    this.isStarted = false
    if (this.readyUpTimer) {
      this.readyUpTimer.cancel()
    }
    def readyPlayers = []
    def notReadyPlayers = []
    this.players.each{
      if (it.timers != null && this.key in it.timers) {
        it.timers.remove(this.key)
      }
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