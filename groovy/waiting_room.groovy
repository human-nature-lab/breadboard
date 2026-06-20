import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap

// Overlapping loop executions are prevented by the isLoopRunning AtomicBoolean guard: if a tick
// is still running when the next fires, the new tick is skipped. Under sustained load this can
// drop a tick, but the guard itself cannot deadlock.

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
            // Clear the public ready-up state; selected players are overridden when
            // their Game's begin step runs, returned players go back to waiting.
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
  private SharedTimer _foundGroupTimer
  private SharedTimer _startGroupTimer
  private Closure _readyUpFailureCb
  private AtomicInteger groupId
  private AtomicBoolean isLoopRunning = new AtomicBoolean(false)
  private boolean _started = false
  public RecruitmentController _recruitmentController

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
    this._recruitmentController = new RecruitmentController()
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
        this._recruitmentController.clientWaiting(player.id)
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
    this._recruitmentController.enableLogging()
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
      this._recruitmentController.start()
    }
  }

  public stop() {
    this.withLock {
      this._loopTimer.cancel()
      this._recruitmentController.stop()
      if (this._foundGroupTimer) {
        this._foundGroupTimer.cancel()
      }
      if (this._startGroupTimer) {
        this._startGroupTimer.cancel()
      }
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
    this._recruitmentController.clientPending(clientId)
  }

  public clientCompleted(String clientId) {
    this._recruitmentController.clientCompleted(clientId)
  }

  public groupCompleted(String groupId) {
    this._recruitmentController.gameCompleted(groupId)
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
    def selectedPlayers = null
    def groupId = null

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
      } else {
        selectedPlayers = readyPlayers
        if (readyPlayers.size() > this.maxPlayers) {
          this.log("too many players for group, using a random sample")
          Collections.shuffle(readyPlayers)
          // Participants who readied up but weren't selected are prioritized for the next group.
          // This sort is stable, so players are randomized within priority groups.
          readyPlayers.sort { -it._system.waitingRoom.priority }
          selectedPlayers = readyPlayers[0..this.maxPlayers-1]
          def notSelectedPlayers = readyPlayers[this.maxPlayers..-1]
          this.log("not selected players", notSelectedPlayers)
          for (def player in notSelectedPlayers) {
            player._system.waitingRoom.state = "waiting-room"
            player._system.waitingRoom.notChosenForGroup = true
            player._system.waitingRoom.priority++
          }
        }
        for (def player in selectedPlayers) {
          player._system.waitingRoom.state = "group-start"
        }
        groupId = this.groupId.incrementAndGet()
        this.log("group $groupId starting in ", this.groupStartDelaySeconds, " seconds")
        this.waitingPlayers.removeAll(selectedPlayers)
      }
    }

    // External callbacks and timer construction run OUTSIDE the lock: don't execute user code or
    // build/start a timer while holding the mutex, and avoid re-entrancy surprises.
    for (def player in droppedPlayers) {
      this._readyUpFailureCb(player)
    }
    if (selectedPlayers != null) {
      def cb = this._groupReadyCb
      def selected = selectedPlayers
      def gid = groupId.toString()   // expose the id as a String to match groupCompleted()
      this._startGroupTimer = new SharedTimer([
        time: this.groupStartDelaySeconds,
        timerText: "Experiment starting in: ",
        name: "waiting-room-group-start",
        players: selected,
        result: {
          this.log("group $gid starting")
          this._recruitmentController.gameStarted(gid, selected.collect { it.id })
          this.removePlayers(*selected)
          cb(selected, gid)
        }
      ])
    }
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
            // Natural expiry: just clear the reference (end() already cancelled the timer). Take
            // the lock so this off-thread write doesn't race checkForReadyGroups / addPlayers.
            this.withLock {
              this._foundGroupTimer = null
            }
          }
        ])
      }
    }
  }

}


class RecruitmentClient {
  String id
  Date joinedAt
  Date startedGameAt
  Date waitingAt
  Date completedGameAt
  String state
  String gameId

  public RecruitmentClient(String id) {
    this.id = id
    this.joinedAt = new Date()
    this.startedGameAt = null
    this.waitingAt = null
    this.completedGameAt = null
    this.state = "pending"
    this.gameId = null
  }

  public setState(String state) {
    this.state = state
  }
}


/**
 * This class will keep track of counts and timing information for all clients. It's goal is to maximize the completion
 * rate of games while minimizing the time spent on the task before the game starts.
 */
class RecruitmentController extends BreadboardBase {
  int maxSimultaneousGames = 5   // max number of games that can be running at once
  int desiredCompletedGames = 10 // number of games to run before stopping recruitment
  int minPlayersPerGame = 15     // min number of players per game
  int maxPlayersPerGame = 25     // max number of players per game

  List<RecruitmentClient> clients 
  Map<String, Boolean> activeGames
  int completedGames = 0
  int completedPlayers = 0
  private BBTimer _loopTimer
  private boolean _showLogs = false
  private AtomicBoolean isRecruitmentLoopRunning = new AtomicBoolean(false)

  public RecruitmentController() {
    this.clients = new CopyOnWriteArrayList<>()
    this.activeGames = new ConcurrentHashMap<>()
    this._loopTimer = new BBTimer()
  }

  private log(...args) {
    if (this._showLogs) {
      println "[RecruitmentController] " + args.join(" ")
    }
  }

  public enableLogging() {
    this._showLogs = true
  }

  public start() {
    this._loopTimer.scheduleAtFixedRate(this._loop as GroovyTimerTask, 0, 10000)
  }

  public stop() {
    this._loopTimer.cancel()
  }

  public clientPending(String clientId) {
    this._setClientState(clientId, "pending")
  }
  
  private _setClientState(String clientId, String state) {
    def index = this.clients.findIndexOf { it.id == clientId }
    if (index == -1) {
      // New client: set its state and add it. Returning here avoids indexing clients[-1] (Groovy
      // negative indexing = last element), which is wrong under concurrent adds.
      def client = new RecruitmentClient(clientId)
      client.setState(state)
      this.clients.add(client)
      return
    }
    this.clients[index].setState(state)
  }

  public clientWaiting(String clientId) {
    this._setClientState(clientId, "waiting")
  }

  public clientCompleted(String clientId) {
    this._setClientState(clientId, "completed")
  }

  public removeClient(String clientId) {
    this._setClientState(clientId, "removed")
  }

  public gameStarted(String gameId, ArrayList<String> clientIds) {
    for (def client in this.clients) {
      if (clientIds.contains(client.id)) {
        client.gameId = gameId
        this._setClientState(client.id, "active")
      }
    }
    this.activeGames.put(gameId, true)
  }

  public gameCompleted(String gameId) {
    def clients = this.clients.findAll { it.gameId == gameId }
    if (clients) {
      for (def client in clients) {
        client.gameId = null
        this._setClientState(client.id, "completed")
      }
      this.activeGames.remove(gameId)
      this.completedGames++
    }
  }

  public canStartGame() {
    return this.activeGames.size() < this.maxSimultaneousGames
  }

  private _loop = {
    // Prevent overlapping executions using atomic boolean
    if (!isRecruitmentLoopRunning.compareAndSet(false, true)) {
      // Previous execution still running, skip this iteration
      return
    }
    
    try {
      def categories = new HashMap<String, Integer>()
      for (def client : this.clients) {
        if (!categories.containsKey(client.state)) {
          categories[client.state] = 0
        }
        categories[client.state]++
      }
      def s = ""
      for (def category : categories) {
        s += "$category.key: $category.value, "
      }
      if (s.length() > 0) {
        s = s.substring(0, s.length() - 2)
        s = "clients: $s"
      }
      def completedGames = this.completedGames
      def activeGames = this.activeGames.size()
      def desiredGames = this.desiredCompletedGames
      this.log("games: $activeGames active, $completedGames completed, $desiredGames desired; $s")
    } catch (Exception e) {
      println "Error in RecruitmentController._loop: ${e.message}"
      e.printStackTrace()
    } finally {
      // Always reset the flag, even if an exception occurred
      isRecruitmentLoopRunning.set(false)
    }
  }

}