import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

// Overlapping loop executions are prevented by the isLoopRunning AtomicBoolean guard: if a tick
// is still running when the next fires, the new tick is skipped. Under sustained load this can
// drop a tick, but the guard itself cannot deadlock.

// TODO: Add an auto-ready mode

// This is here to make the compiler happy
def doNothing = {}

class WaitingRoomReadyUp {
  ArrayList<Vertex> players
  long createdAt
  int readyUpSeconds = 20
  boolean isRunning = false
  SharedTimer _timer

  WaitingRoomReadyUp(ArrayList<Vertex> players, int readyUpSeconds) {
    this.players = players
    this.createdAt = System.currentTimeMillis()
    this.readyUpSeconds = readyUpSeconds
  }

  public run(Closure onDone) {
    this._timer = new SharedTimer([
      time: this.readyUpSeconds,
      name: "waiting-room-ready-up",
      timerText: "Remaining time: ",
      players: this.players,
      result: {
        def readyPlayers
        def notReadyPlayers
        synchronized (this) {
          this.isRunning = false
          readyPlayers = this.players.findAll { it._system.waitingRoom.isReady }
          notReadyPlayers = this.players.findAll { !it._system.waitingRoom.isReady }
          this.players.each {
            it._system.waitingRoom.isReady = false
            it.off("waiting-room:ready")
            // Clear the public ready-up state; selected players are overridden when their
            // Game's begin step runs, returned players go back to waiting.
            it.step = "waiting-room"
          }
          this._timer = null
        }
        // Invoke the callback OUTSIDE this monitor. handleReadyUpResult takes WaitingRoom.mutex,
        // and addPlayer() takes this monitor while holding that mutex; calling onDone here (still
        // holding the monitor) would invert the lock order and could deadlock (ABBA).
        onDone(readyPlayers, notReadyPlayers)
      }
    ])
    synchronized (this) {
      this.isRunning = true
      for (def player in this.players) {
        this.startPlayer(player)
      }
    }
  }

  private startPlayer(player) {
    def self = this
    player._system.waitingRoom.state = "ready-up"
    player._system.waitingRoom.notChosenForGroup = false
    player._system.waitingRoom.notEnoughReadyForGroup = false
    player._system.waitingRoom.isReady = false
    // Public mirror so the client can show the "Ready" button (see client-html.html).
    player.step = "ready-up"
    player.text = "<p><strong>Press 'Ready' before the timer expires to begin the game.</strong></p>"
    player.once("waiting-room:ready", { v, data ->
      // Write isReady under the ready-up monitor so it is visibly ordered against the timer
      // thread's partition read (the _system.waitingRoom map is not itself thread-safe).
      synchronized (self) {
        player._system.waitingRoom.isReady = true
      }
      // Hide the button and show a waiting message once they've readied up.
      player.step = "waiting-room"
      player.text = "<p>You are ready. Waiting for the other players...</p>"
    })
  }

  // A player will only be added if the timer hasn't started or at least 5 seconds remain on the timer
  public addPlayer(player) {
    synchronized (this) {
      if (this.isRunning) {
        def remainingTime = this._timer.endTime - System.currentTimeMillis()
        if (remainingTime < 5000) {
          return
        }
        this._timer.addPlayer(player)
      }
      this.players.add(player)
      this.startPlayer(player)
    }
  }
}

class WaitingRoom extends BreadboardBase {
  Lock mutex = new ReentrantLock(true)
  ArrayList<Vertex> waitingPlayers
  Set<String> addedPlayers
  int minPlayers
  int maxPlayers
  int readyStartDelaySeconds = 60
  int groupStartDelaySeconds = 8
  int readyUpSeconds = 20
  int readyUpFailureLimit = 3
  private boolean showLogs = false
  private boolean foundGroup = false
  private long foundGroupAt = 0
  private boolean readyUpRunning = false
  private Closure _groupReadyCb
  private Closure _setStageCb
  private WaitingRoomReadyUp readyUp
  BBTimer _loopTimer
  SharedTimer _foundGroupTimer
  // One countdown per pending group: a single ready-up round can now produce several groups, so
  // we keep them all so stop() can cancel every outstanding countdown (not just the last one).
  private List<SharedTimer> _startGroupTimers = new CopyOnWriteArrayList<>()
  private Closure _readyUpFailureCb
  private AtomicInteger groupId
  private AtomicBoolean isLoopRunning = new AtomicBoolean(false)
  private boolean _started = false
  // The single recruitment controller (recruitment.groovy), injected via setRecruitment(). Left
  // null when the lobby is used without recruitment (e.g. unit tests); every call below is guarded.
  // Untyped on purpose: RecruitmentController is defined in recruitment.groovy, which loads AFTER
  // this script, so a typed field would be a forward compile reference. Calls dispatch dynamically.
  def _recruitment = null

  public WaitingRoom(int minPlayers, int maxPlayers) {
    this.minPlayers = minPlayers
    this.maxPlayers = maxPlayers
    this.waitingPlayers = []
    this.mutex = new ReentrantLock(true)
    this._loopTimer = new BBTimer()
    this.addedPlayers = new HashSet<>()
    this._groupReadyCb = null
    this._setStageCb = this.defaultSetStageCb
    this.foundGroupAt = 0
    this.groupId = new AtomicInteger(0)
  }

  // Wire the lobby to the experiment's recruitment controller. Once set, the lobby reports
  // waiting / game-start / game-complete transitions to it (see the guarded calls below).
  public setRecruitment(rc) {
    this.withLock {
      this._recruitment = rc
    }
  }

  public setGroupId(int groupId) {
    // groupId is an AtomicInteger; no extra lock needed.
    this.groupId.set(groupId)
  }

  private withLock(Closure c) {
    mutex.lock()
    try {
      return c()
    } finally {
      mutex.unlock()
    }
  }

  public addPlayer(player) {
    this.addPlayers(player)
  }

  public addPlayers(...players) {
    this.log("adding players", players)
    this.withLock {
      for (def player in players) {
        if (this.addedPlayers.contains(player.id)) {
          continue
        }
        if (this._recruitment != null) this._recruitment.clientWaiting(player.id)
        this.addedPlayers.add(player.id)
        this.waitingPlayers.add(player)
        this._setStageCb(player)
        player._system.waitingRoom = [
          state: "waiting-room",
          priority: 0,
          readyUpFailures: 0,
          progress: 0.0,
          notChosenForGroup: false,
          notEnoughReadyForGroup: false,
        ]
        if (this.readyUp) {
          this.readyUp.addPlayer(player)
        } else if (this._foundGroupTimer) {
          this._foundGroupTimer.addPlayer(player)
        }
      }
    }
  }

  public enableLogging() {
    this.showLogs = true
    if (this._recruitment != null) this._recruitment.enableLogging()
  }

  public removePlayers(...players) {
    this.withLock {
      for (def player in players) {
        this._removePlayer(player)
      }
    }
  }

  private _removePlayer(player) {
    this.waitingPlayers.remove(player)
    this.addedPlayers.remove(player.id)
    player._system.remove("waitingRoom")
  }

  public removePlayer(player) {
    this.withLock {
      this._removePlayer(player)
    }
  }

  public onGroupReady(Closure c) {
    this.withLock {
      this._groupReadyCb = c
    }
  }

  public onReadyUpFailure(Closure c) {
    this.withLock {
      this._readyUpFailureCb = c
    }
  }

  public onSetStage(Closure c) {
    this.withLock {
      this._setStageCb = c
    }
  }

  def defaultSetStageCb = { Vertex player ->
    // Top-level vertex property: this is the field the client reads to pick its view (see the
    // ready-up code, which also writes player.step). Writing player.private.step instead would
    // be invisible to the client.
    player.step = "waiting-room"
  }

  public start() {
    this.withLock {
      if (this._groupReadyCb == null) {
        throw new Exception("Must supply a callback to WaitingRoom.onGroupReady")
      }
      if (this._readyUpFailureCb == null) {
        throw new Exception("Must supply a callback to WaitingRoom.onReadyUpFailure")
      }
      if (this._started) {
        return  // already running; don't schedule the loop twice
      }
      this._started = true
      this._loopTimer.scheduleAtFixedRate(this._loop as GroovyTimerTask, 0, 1000)
      if (this._recruitment != null) this._recruitment.start()
    }
  }

  public stop() {
    this.withLock {
      this._loopTimer.cancel()
      if (this._recruitment != null) this._recruitment.stop()
      if (this._foundGroupTimer) {
        this._foundGroupTimer.cancel()
      }
      for (def timer in this._startGroupTimers) {
        timer.cancel()
      }
      this._startGroupTimers.clear()
    }
  }

  private _loop = {
    // Prevent overlapping executions using atomic boolean
    if (!isLoopRunning.compareAndSet(false, true)) {
      // Previous execution still running, skip this iteration
      return
    }

    try {
      this.checkForReadyGroups()
      this.updatePlayerInfo()
      this.startPendingGroups()
    } catch (Exception e) {
      println "Error in WaitingRoom._loop: ${e.message}"
      e.printStackTrace()
    } finally {
      // Always reset the flag, even if an exception occurred
      isLoopRunning.set(false)
    }
  }

  private updatePlayerInfo() {
    // Update counters for players
    this.withLock {
      float progress = this.waitingPlayers.size() / this.minPlayers
      if (progress > 1) {
        progress = 1.0
      }
      for (def player in this.waitingPlayers) {
        player._system.waitingRoom.progress = progress
      }
    }
  }

  private log (...args) {
    if (this.showLogs) {
      println "[WaitingRoom] " + args.join(" ")
    }
  }

  public clientPending(String clientId) {
    if (this._recruitment != null) this._recruitment.clientPending(clientId)
  }

  public clientCompleted(String clientId) {
    if (this._recruitment != null) this._recruitment.clientCompleted(clientId)
  }

  public groupCompleted(String groupId) {
    if (this._recruitment != null) this._recruitment.gameCompleted(groupId)
  }

  private startPendingGroups() {
    // Update pending groups
    this.withLock {
      if (!this.foundGroup || this.readyUpRunning) {
        return
      }
      def now = System.currentTimeMillis()
      def isTimeToStart = now - this.foundGroupAt > (this.readyStartDelaySeconds * 1000)
      def hasEnoughPlayers = this.waitingPlayers.size() >= this.minPlayers
      if (this.foundGroup && isTimeToStart && hasEnoughPlayers) {
         // Start the ready step
        this.log("starting ready step")
        this._cancelFoundGroupTimer()   // countdown is over; ready-up shows its own timer
        this.readyUpRunning = true
        this.readyUp = new WaitingRoomReadyUp(new ArrayList(this.waitingPlayers), this.readyUpSeconds)
        this.readyUp.run(this.handleReadyUpResult)
      } else if (isTimeToStart) {
        this.log("failed to start group")
        // Reset the group and try again later
        this._cancelFoundGroupTimer()
        this.foundGroup = false
        this.foundGroupAt = 0
        this.readyUpRunning = false
      }
    }
  }

  private handleReadyUpResult = { List<Vertex> readyPlayers, List<Vertex> notReadyPlayers ->
    def droppedPlayers = []
    def groups = new ArrayList()

    // All shared-state mutation happens under the lock; waitingPlayers is a plain ArrayList, so
    // racing the per-second loop here would risk ConcurrentModificationException / lost updates.
    this.withLock {
      this.readyUp = null
      this.foundGroup = false
      this.foundGroupAt = 0
      this.readyUpRunning = false
      this._cancelFoundGroupTimer()

      for (def player in notReadyPlayers) {
        player._system.waitingRoom.state = "waiting-room"
        player._system.waitingRoom.readyUpFailures++
        if (player._system.waitingRoom.readyUpFailures >= this.readyUpFailureLimit) {
          this.log("player $player.id has failed to ready up too many times, removing from waiting room")
          this._removePlayer(player)   // already holding the lock; use the unlocked variant
          droppedPlayers.add(player)
        }
      }

      if (readyPlayers.size() < this.minPlayers) {
        def readyPlayerIds = readyPlayers.collect { it.id }
        def notReadyPlayerIds = notReadyPlayers.collect { it.id }
        this.log("not enough players", readyPlayerIds, notReadyPlayerIds)
        this.addEvent("waiting_room.not_enough_players", [
          readyPlayers: readyPlayerIds,
          notReadyPlayers: notReadyPlayerIds,
        ])
        for (def player in readyPlayers) {
          player._system.waitingRoom.state = "waiting-room"
          player._system.waitingRoom.notEnoughReadyForGroup = true
          player._system.waitingRoom.priority++
        }
      } else if (readyPlayers.size() > this.maxPlayers) {
        // Carve as many full groups as possible out of the ready pool, then keep the remainder
        // only if it itself clears minPlayers; otherwise those players go back to the lobby.
        def pending = new ArrayList(readyPlayers)
        Collections.shuffle(pending)
        this.log("too many players for group, using random samples")
        // Participants who ready up but aren't selected are prioritized for the next group.
        // This sort is stable, so players are randomized within priority groups.
        pending.sort { -it._system.waitingRoom.priority }
        while (pending.size() > this.maxPlayers) {
          def group = new ArrayList(pending.subList(0, this.maxPlayers))
          groups.add(group)
          pending.removeAll(group)
        }
        if (pending.size() >= this.minPlayers && pending.size() > 0) {
          groups.add(new ArrayList(pending))
        } else if (pending.size() > 0) {
          this.log("pending players", pending.collect { it.id })
          for (def player in pending) {
            player._system.waitingRoom.state = "waiting-room"
            player._system.waitingRoom.notChosenForGroup = true
            player._system.waitingRoom.priority++
          }
        }
      } else {
        groups.add(new ArrayList(readyPlayers))
      }

      // Reserve the selected players under the lock so the loop can't re-group them; the
      // group-start countdown timers and onGroupReady callbacks are built afterwards, outside it.
      for (def group in groups) {
        for (def player in group) {
          player._system.waitingRoom.state = "group-start"
        }
        this.waitingPlayers.removeAll(group)
      }
    }

    // External callbacks and timer construction run OUTSIDE the lock: don't execute user code or
    // build/start a timer while holding the mutex (avoids lock-order inversion / re-entrancy).
    for (def player in droppedPlayers) {
      this._readyUpFailureCb(player)
    }
    for (def group in groups) {
      this.startGroup(group, this.groupId.incrementAndGet())
    }
  }

  // Build and start the group-start countdown for an already-reserved group (its players are
  // marked "group-start" and removed from waitingPlayers by the caller). Called outside the lock.
  private startGroup(List<Vertex> group, int groupId) {
    def gid = groupId.toString()   // expose the id as a String to match groupCompleted()
    this.log("group $gid starting in ", this.groupStartDelaySeconds, " seconds")
    def cb = this.withLock {
      return this._groupReadyCb
    }
    def timer
    timer = new SharedTimer([
      time: this.groupStartDelaySeconds,
      timerText: "Experiment starting in: ",
      name: "waiting-room-group-start",
      players: group,
      result: {
        this.log("group $gid starting", group.collect { it.id })
        if (this._recruitment != null) this._recruitment.gameStarted(gid, group.collect { it.id })
        this.removePlayers(*group)
        this._startGroupTimers.remove(timer)
        cb(group, gid)
      }
    ])
    this._startGroupTimers.add(timer)
  }

  // Cancel and clear the "found group" countdown timer if one is running. Callers must hold mutex.
  private _cancelFoundGroupTimer() {
    if (this._foundGroupTimer) {
      this._foundGroupTimer.cancel()
      this._foundGroupTimer = null
    }
  }

  private checkForReadyGroups() {
    this.withLock {
      // Create groups of players
      if (this.waitingPlayers.size() < this.minPlayers) {
        return
      }
      if (!this.foundGroup && !this.readyUpRunning) {
        this.foundGroup = true
        this.foundGroupAt = System.currentTimeMillis()
        this.log("found group at " + this.foundGroupAt, "waiting ", this.readyStartDelaySeconds, " seconds before starting")
        this._foundGroupTimer = new SharedTimer([
          time: this.readyStartDelaySeconds,
          timerText: "Starting in: ",
          name: "waiting-room-found-group",
          players: new ArrayList(this.waitingPlayers),
          result: {
            // Natural expiry: just clear the reference (the timer already fired). Take the lock so
            // this off-thread write doesn't race checkForReadyGroups / addPlayers.
            this.withLock {
              this._foundGroupTimer = null
            }
          }
        ])
      }
    }
  }

}
