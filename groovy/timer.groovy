import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit
import akka.dispatch.ExecutionContexts
import play.libs.Akka
import scala.concurrent.duration.Duration
/**
 * A custom timer class which will be correctly removed when the script engine reloads
 */
class BBTimer extends Timer {
  static def registry

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

  void register () {
    registry.register(this)
  }

  void unregister () {
    registry.unregister(this)
  }
}

/**
 * The timer operations SharedTimer needs, backed by Akka's shared scheduler
 * instead of creating one native thread per experiment timer.
 */
class BBScheduledTimer {
  static def registry
  // Akka Scheduler backing the timer tasks. Null in production, where schedulerOrDefault() falls
  // back to the running Play app's Akka.system(); tests inject a standalone ActorSystem's scheduler
  // so this real scheduling code runs without a started application.
  static def scheduler
  // Timer closures are experiment code and may block; like the original
  // per-timer java.util.Timer threads, they must not run on (and starve)
  // Akka's shared dispatcher. Threads in this pool die after 60s idle.
  static def callbackContext
  def tasks = new CopyOnWriteArrayList()
  private final def taskLock = new java.util.concurrent.locks.ReentrantLock()
  private volatile boolean cancelled = false

  BBScheduledTimer () {
    registry.registerScheduled(this)
  }

  // Prod leaves `scheduler` null and uses the app's shared Akka scheduler, exactly as before;
  // tests inject one so no started Play application is required.
  private schedulerOrDefault () {
    return scheduler ?: Akka.system().scheduler()
  }

  void runAfter (long delay, Closure closure) {
    if (cancelled) return
    tasks << schedulerOrDefault().scheduleOnce(
      Duration.create(Math.max(0L, delay), TimeUnit.MILLISECONDS),
      new GroovyTimerTask(closure: {
        taskLock.lock()
        try {
          if (!cancelled) closure()
        } finally {
          taskLock.unlock()
        }
      }),
      callbackContext
    )
  }

  void scheduleAtFixedRate (Runnable task, long delay, long period) {
    if (cancelled) return
    tasks << schedulerOrDefault().schedule(
      Duration.create(Math.max(0L, delay), TimeUnit.MILLISECONDS),
      Duration.create(period, TimeUnit.MILLISECONDS),
      new GroovyTimerTask(closure: {
        if (!taskLock.tryLock()) return
        try {
          if (!cancelled) task.run()
        } finally {
          taskLock.unlock()
        }
      }),
      callbackContext
    )
  }

  int purge () {
    return 0
  }

  void cancel () {
    cancelled = true
    tasks.each { it.cancel() }
    tasks.clear()
    registry.unregisterScheduled(this)
  }

  void end () {
    cancel()
  }
}

/**
 * Timers registry. Handles cleaning them up property when necessary
 */
class BBTimers {
  ArrayList<BBTimer> timers = new CopyOnWriteArrayList()
  ArrayList<BBScheduledTimer> scheduledTimers = new CopyOnWriteArrayList()
  ArrayList<SharedTimer> sharedTimers = new CopyOnWriteArrayList()

  public synchronized void cancel () {
    for (def timer : new ArrayList(this.timers)) {
      timer.end()
    }
    this.timers.clear()
    for (def timer : new ArrayList(this.scheduledTimers)) {
      timer.end()
    }
    this.scheduledTimers.clear()
    this.sharedTimers.clear()
  }

  public synchronized void register (BBTimer timer) {
    this.timers << timer
  }

  public synchronized void unregister (BBTimer timer) {
    this.timers.remove(timer)
  }

  public synchronized void registerScheduled (BBScheduledTimer timer) {
    this.scheduledTimers << timer
  }

  public synchronized void unregisterScheduled (BBScheduledTimer timer) {
    this.scheduledTimers.remove(timer)
  }

  public synchronized void registerShared (SharedTimer timer) {
    this.sharedTimers << timer
  }

  public synchronized void unregisterShared (SharedTimer timer) {
    this.sharedTimers.remove(timer)
  }

  /**
   * Remove a player from every shared timer. Called when a player leaves the
   * experiment so no timer keeps per-player state (or a reference to the player
   * vertex) around after they're gone. Only shared timers track players, so
   * BBTimer/BBScheduledTimer need nothing here.
   * @param {Vertex} player - The player to remove from all shared timers
   * @param {Boolean} [endIfEmpty=false] - End any shared timer left with no players after the removal
   */
  public void removePlayer (Vertex player, Boolean endIfEmpty = false) {
    for (def timer : new ArrayList(this.sharedTimers)) {
      timer.removePlayer(player, endIfEmpty)
    }
  }

  public BBTimer newTimer () {
    return new BBTimer()
  }

}

// Global timers registry. Gets cleaned up when the ScriptBoard resets
timers = new BBTimers()
BBTimer.registry = timers
BBScheduledTimer.registry = timers
BBScheduledTimer.callbackContext = ExecutionContexts.fromExecutorService(
  Executors.newCachedThreadPool({ r ->
    def t = new Thread(r, "shared-timer-callback")
    t.setDaemon(true)
    return t
  } as ThreadFactory))

class GroovyTimerTask extends TimerTask {
  Closure closure

  void run() {
    try {
      closure()
    } catch (Throwable t) {
      models.ScriptLoader.humanizeStackTrace(t)
      play.Logger.error("Uncaught error in timer task", t)
    }
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
  static def registry

  def players = new CopyOnWriteArrayList()
  def doneClosures = []
  Number updateRate = 1000
  Number startTime
  Number endTime
  Boolean hasEnded = false
  Map content
  Map playerTimer = new ConcurrentHashMap([
    type: "time",
    direction: "down",
    duration: 0,
    elapsed: 0,
    currencyAmount: "0",
    appearance: "",
    order: 0
  ])
  private BBScheduledTimer timer

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
      this.playerTimer.duration = opts.time * 1000
    } else if ("duration" in opts) {
      this.playerTimer.duration = opts.duration
    } else {
      throw new Exception("'time' or 'duration' properties must be present to start a timer")
    }
    if ("timerText" in opts) {
      this.playerTimer.timerText = opts.timerText
    } else if ("content" in opts) {
      this.playerTimer.timerText = this.fetchContent(opts.content)
    }
    if ("result" in opts) {
      this.onDone(opts.result)
    }
    def props = ["updateRate", "type", "direction", "currencyAmount", "appearance"]
    props.each{ prop ->
      if (prop in opts) {
        this.playerTimer[prop] = opts[prop]
      }
    }
    this.playerTimer.name = "name" in opts ? opts.name : UUID.randomUUID().toString()
    if ("player" in opts) {
      this.addPlayer(opts.player)
    } else if ("players" in opts) {
      this.addPlayers(opts.players)
    }
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
    player.timers[this.playerTimer.name] = this.playerTimer
    this.startTimer()
  }

  /**
   * Whether the given player is currently attached to this timer
   * @param {Vertex} player - The player to check for
   */
  public hasPlayer (Vertex player) {
    return this.players.contains(player)
  }

  /**
   * Whether a player with the given id is currently attached to this timer
   * @param {String} playerId - The id of the player to check for
   */
  public hasPlayer (String playerId) {
    return this.players.any { it.id == playerId }
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
    this.unregister()
    if (!this.timer) return
    this.timer.purge()
    this.timer.cancel()
    this.timer = null
    this.players.each{player ->
      this.endPlayer(player)
    }
    this.players.clear()
  }

  public pause () {
    if (this.timer) {
      this.timer.purge()
      this.timer.cancel()
      this.timer = null
      this.playerTimer.paused = true
    }
  }

  public resume () {
    if (this.timer) return // Don't start the timer twice
    this.endTime = System.currentTimeMillis() + this.playerTimer.duration - this.playerTimer.elapsed
    // this.startTime = now - this.playerTimer.elapsed
    this.registerTimerEvents()
    this.playerTimer.paused = false
  }

  /**
   * End the timer for all players. Should use cancel to end the timer early and continue as though it completed successfully
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
   * @param {Boolean} [endIfEmpty=false] - End the timer if no players remain after removing this one
   */
  public removePlayer (Vertex player, Boolean endIfEmpty = false) {
    if (this.players.contains(player)) {
      this.endPlayer(player)
      this.players.remove(player)
    }
    if (endIfEmpty && this.players.isEmpty()) {
      this.end()
    }
  }

  /**
   * Stop displaying this timer for this player
   */
  private endPlayer (Vertex player) {
    if (!player.timers) return
    player.timers.remove(this.playerTimer.name)
  }

  /**
   * Start the timer only if it hasn't been started yet
   */
  private startTimer () {
    if (this.timer || this.hasEnded) return
    this.playerTimer.elapsed = 0
    this.startTime = System.currentTimeMillis()
    this.endTime = this.startTime + this.playerTimer.duration
    this.registerTimerEvents()
  }

  private resetTimer () {
    this.timer.purge()
    this.timer.cancel()
    this.timer = null
    this.hasEnded = false
  }

  private registerTimerEvents () {
    if (this.timer) return
    this.timer = new BBScheduledTimer()
    long delay = this.endTime - System.currentTimeMillis()
    this.timer.runAfter(delay) {
      this.end()
    }
    this.timer.scheduleAtFixedRate(new GroovyTimerTask(closure: {
      this.tick(this.updateRate)
    }), this.updateRate, this.updateRate)
  }

  /**
   * Set the duration for this timer
   * @param {int} duration - The new timer duration in milliseconds
   */ 
  public setDuration (int duration) {
    // Floor the duration so a very short (or negative) value can't schedule a timer that fires
    // before the client can render/react to it.
    if (duration < 2000) {
      duration = 2000
    }
    this.playerTimer.duration = duration
    if (this.timer) {
      this.resetTimer()
      this.endTime = this.startTime + duration
      // Check if we've already exceeded the duration and end if we have
      if (System.currentTimeMillis() >= this.endTime) {
        return this.end()
      }
      this.registerTimerEvents()
    }
  }

  /**
   * Add time to an existing timer
   * @param {int} delta - The number of milliseconds to add to the timer
   */
  public addTime (int delta) {
    this.setDuration(this.playerTimer.duration + delta)
  }

  /**
   * Restart the timer as though it just started
   */
  public restart () {
    this.resetTimer()
    this.playerTimer.elapsed = 0
    this.startTime = System.currentTimeMillis()
    this.endTime = this.startTime + this.playerTimer.duration
    this.registerTimerEvents()
  }

  /**
   * This updates the timer for each player attached to this timer.
   * @param {Int} delta - The number of milliseconds to increase the timer by.
   */
  public tick (int delta) {
    this.playerTimer.elapsed += delta
  }
  
  public isRunning () {
    return this.timer != null
  }

  void register () {
    registry.registerShared(this)
  }

  void unregister () {
    registry.unregisterShared(this)
  }
}

SharedTimer.registry = timers
