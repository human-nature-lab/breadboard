import java.util.Collections

/**
 * A custom timer class which will be correctly removed when the script engine reloads
 */
class BBTimer extends Timer {
  BBTimer () {
    super()
    this.register()
  }

  void end () {
    super.cancel()
  }

  void cancel () {
    super.cancel()
    this.unregister()
  }
}

BBTimer.metaClass.register = {
  timers.register(delegate)
}

BBTimer.metaClass.unregister = {
  timers.unregister(delegate)
}

/**
 * Timers registry. Handles cleaning them up property when necessary
 */
class BBTimers {
  ArrayList<BBTimer> timers = Collections.synchronizedList(new ArrayList())
  ArrayList<SharedTimer> sharedTimers = Collections.synchronizedList(new ArrayList())

  public void cancel () {
    synchronized (this.timers) {
      for (def timer : this.timers) {
        timer.end()
      }
      this.timers.clear()
    }
    // We don't need to cancel shared timers because they depend on the BBTimers which are cancelled above
    synchronized (this.sharedTimers) {
      this.sharedTimers.clear()
    }
  }

  public void register (BBTimer timer) {
    synchronized (this.timers) {
      this.timers << timer
    }
  }

  public void unregister (BBTimer timer) {
    synchronized (this.timers) {
      this.timers.remove(timer)
    }
  }

  public void registerShared (SharedTimer timer) {
    synchronized (this.sharedTimers) {
      this.sharedTimers << timer
    }
  }

  public void unregisterShared (SharedTimer timer) {
    synchronized (this.sharedTimers) {
      this.sharedTimers.remove(timer)
    }
  }

  public BBTimer newTimer () {
    return new BBTimer()
  }

}

// Global timers registry. Gets cleaned up when the ScriptBoard resets
timers = new BBTimers()

class GroovyTimerTask extends TimerTask {
  Closure closure

  void run() {
    closure()
  }
}

class TimerMethods {
  static TimerTask runEvery(Timer timer, long delay, long period, Closure codeToRun) {
    TimerTask task = new GroovyTimerTask(closure: codeToRun)
    timer.schedule task, delay, period
    task
  }
}

class SharedTimer extends BreadboardBase {
  def players = []
  def doneClosures = []
  Number updateRate = 1000
  Number startTime
  Number endTime
  Number elapsed = 0
  Number duration // millis
  Number order
  String name
  String type = "time"
  String direction = "down"
  String currencyAmount = "0"
  String appearance = ""
  Boolean hasEnded = false
  Map content
  private BBTimer timer

  SharedTimer (int seconds) {
    this([
      time: seconds
    ])
  }
  /**
   * Create a shared timer via a Map
   * @param {Map} opts
   * @param {Number} [opts.time] - Timer time in seconds
   * @param {Number} [opts.duration] - Timer time in milliseconds
   * @param {Vertex} [opts.player] - A player to add this timer to
   * @param {Vertex[]} [opts.players] - A list of players to add this timer to
   * @param {Boolean} [opts.lazy] - Start the timer when the first player is added
   * @param {Closure} [opts.result] - Call this closure when the timer expires
   * @param {String} [opts.timerText] - The timer label to display
   * @param {String} [opts.name] - The unique key to use for this timer
   * @param {Number} [opts.updateRate] - How often this timer should update
   * @param {String} [opts.type="time"] - Valid options are "time" and "currency"
   * @param {String} [opts.direction = "down"] - Valid options are "up" or "down"
   * @param {Number} [opts.currencyAmount] - Initial currency for the timer
   * @param {String} [opts.appearance=""] - The color to use to display the timer
   * @param {Map} [opts.content] - A content map to use for fetching the timer label content
   */
  SharedTimer (Map opts) {
    if ("time" in opts) {
      this.duration = opts.time * 1000
    } else if ("duration" in opts) {
      this.duration = opts.duration
    } else {
      throw new Exception("'time' or 'duration' properties must be present to start a timer")
    }
    
    if ("player" in opts) {
      this.addPlayer(opts.player)
    }
    if ("players" in opts) {
      this.addPlayers(opts.players)
    }
    if ("result" in opts) {
      this.onDone(opts.result)
    }
    if ("timerText" in opts) {
      this.content = [
        content: opts.timerText
      ]
    }
    def props = ["updateRate", "type", "direction", "currencyAmount", "appearance", "content"]
    props.each{ prop ->
      if (prop in opts) {
        this."${prop}" = opts[prop]
      }
    }
    this.name = "name" in opts ? opts.name : UUID.randomUUID().toString()
    this.register()
    if (!opts.lazy) {
      this.startTimer()
    }

  }

  /**
   * Add multiple players at once
   * @param {Vertex[]} players - An iterable list of players to add to this timer
   */
  public addPlayers (players) {
    players.each{
      this.addPlayer(it)
    }
  }

  /**
   * Add a single player to this timer
   * @param {Vertex} player - The player to add to this timer
   */
  public addPlayer (Vertex player) {
    if (player == null) {
      println "null player"
      return
    }
    if (this.players.contains(player)) return
    this.players << player
    if (player.timers == null) {
      player.timers = [:]
    }
    player.timers[this.name] = [
      type: this.type,
      direction: this.direction,
      duration: this.duration,
      elapsed: 0,
      currencyAmount: this.currencyAmount,
      appearance: this.appearance,
      timerText: this.fetchContent(this.content),
      order: this.order ?: player.timers.size()
    ]
    this.startTimer()
  }

  /**
   * Register a closure to be called when the timer has ended
   * @param {Closure} cb - Closure without arguments
   */
  public onDone (Closure cb) {
    this.doneClosures << cb
  }

  /**
   * Cancel the timer early and remove it from each player
   */
  public cancel () {
    if (!this.timer) return
    this.unregister()
    this.timer.purge()
    this.timer.cancel()
    this.timer = null
    this.players.each{player ->
      this.endPlayer(player)
    }
    this.players.clear()
  }

  /**
   * End the timer for all players. Should use cancel to end the timer early.
   */
  private end () {
    if (this.hasEnded) return
    this.hasEnded = true
    this.cancel()
    this.doneClosures.each{ cb ->
      cb()
    }
  }

  /**
   * Remove a single player from the timer.
   * @param {Vertex} player - The player to remove
   */
  public removePlayer (Vertex player) {
    if (this.players.contains(player)) {
      this.endPlayer(player)
      this.players.remove(player)
    }
  }

  /**
   * Stop displaying this timer for this player
   */
  private endPlayer (Vertex player) {
    player.timers.remove(this.name)
  }

  /**
   * Start the timer only if it hasn't been started yet
   */
  private startTimer () {
    if (this.timer) return
    this.elapsed = 0
    this.startTime = System.currentTimeMillis()
    this.endTime = this.startTime + this.duration * 1000
    this.timer = new BBTimer()
    this.registerTimerEvents()
  }

  private resetTimer () {
    this.timer.purge()
    this.timer.cancel()
    this.timer = new BBTimer()
  }

  private registerTimerEvents () {
    this.timer.runAfter(this.duration) {
      this.end()
    }
    this.timer.scheduleAtFixedRate({
      this.tick(this.updateRate)
    } as GroovyTimerTask, this.updateRate, this.updateRate)
  }

  /**
   * Set the duration for this timer
   * @param {int} duration - The new timer duration
   */ 
  public setDuration (int duration) {
    this.duration = duration
    if (this.timer) {
      this.resetTimer()
      this.endTime = this.startTime + this.duration * 1000
      // Check if we've already exceeded the duration and end if we have
      if (System.currentTimeMillis() > this.endTime) {
        return this.end()
      }
      this.registerTimerEvents()
      this.players.each{ player ->
        if (player.timers != null && this.name in player.timers) {
          player.timers[this.name].duration = this.duration
        }
      }
    }
  }

  /**
   * Add time to an existing timer
   * @param {int} delta - The number of milliseconds to add
   */
  public addTime (int delta) {
    this.setDuration(this.duration + delta)
  }

  /**
   * Restart the timer as though it just started
   */
  public restart () {
    this.resetTimer()
    this.elapsed = 0
    this.startTime = System.currentTimeMillis()
    this.endTime = this.startTime + this.duration * 1000
    this.registerTimerEvents()
  }

  /**
   * This updates the timer for each player attached to this timer.
   * @param {Int} delta - The number of milliseconds to increase the timer by.
   */
  public tick (int delta) {
    this.elapsed += delta
    this.players.each{ player ->
      if (player.timers != null && this.name in player.timers) {
        player.timers[this.name].elapsed = this.elapsed
      }
    }
  }
}


SharedTimer.metaClass.register = {
  timers.registerShared(delegate)
}

SharedTimer.metaClass.unregister = {
  timers.unregisterShared(delegate)
}