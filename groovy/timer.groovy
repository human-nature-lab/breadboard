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
 * Global class which manages timers in the script engine
 */
class BBTimers {
  ArrayList<BBTimer> timers = Collections.synchronizedList(new ArrayList())

  public void cancel () {
    synchronized (this.timers) {
      for (def timer : this.timers) {
        timer.end()
      }
      this.timers.clear()
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

}

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

class GlobalTimer extends BreadboardBase {
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
  Map content
  private BBTimer timer

  GlobalTimer (int seconds) {
    this([
      time: seconds
    ])
  }
  GlobalTimer (Map opts) {
    if ("time" in opts) {
      this.duration = opts.time * 1000
    } else if ("duration" in opts) {
      this.duration = opts.duration
    } else {
      throw new Exception("'time' or 'duration' properties must be present to start a timer")
    }
    
    if ("updateRate" in opts) {
      this.updateRate = opts.updateRate
    }
    if ("timerText" in opts) {
      this.content = [
        content: opts.timerText
      ]
    }
    if ("immediate" in opts && opts.immediate) {
      this.startTimer()
    }
    this.name = "name" in opts ? opts.name : UUID.randomUUID().toString()

  }

  /**
   * Add multiple players at once
   */
  public addPlayers (players) {
    players.each{
      this.addPlayer(it)
    }
  }

  /**
   * Add a single player to this timer
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
   */
  public onDone (Closure cb) {
    this.doneClosures << cb
  }
  
  /**
   * End the timer for all players. Can be called before the time has expired to end it early.
   */
  public end () {
    if (!this.timer) return
    this.timer.purge()
    this.timer.cancel()
    this.timer = null
    println "end global timer " + this.name
    this.players.each{player ->
      this.endPlayer(player)
    }
    this.doneClosures.each{ cb ->
      cb()
    }
  }

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
    this.timer.runAfter(this.duration) {
      this.end()
    }
    this.timer.scheduleAtFixedRate({
      this.tick(this.updateRate)
    } as GroovyTimerTask, this.updateRate, this.updateRate)
  }

  /**
   * This updates the timer for each player attached to this timer.
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