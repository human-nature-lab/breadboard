import com.tinkerpop.blueprints.Vertex
import groovy.transform.ToString
import org.joda.time.DateTime
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

// Groovy 1.8.6 target: no closure->functional-interface coercion (computeIfAbsent / removeIf /
// forEach / *.stream()), no lambdas / `::` refs. Concurrent fields are declared as the interface
// type (`List x = new CopyOnWriteArrayList()`); declaring them as the concrete class silently
// downgrades them to a plain ArrayList and loses thread-safety.

// Recruitment source identifiers, exposed as a global map for experiments/tests. The values are a
// wire contract: player._system.recruitment.source is exactly the string the frontend compares
// against (see BBMain.vue isProlific / isMTurk). Derived from the controller's static constants so
// there is one source of truth -- the class methods can't see this binding global (Groovy classes
// don't see script bindings), so they use RecruitmentController.SOURCE_* directly.
RecruitmentSource = [
  PROLIFIC: RecruitmentController.SOURCE_PROLIFIC,
  MTURK: RecruitmentController.SOURCE_MTURK,
].asImmutable()

@ToString(includeNames = true)
class ProlificRegisterOpts {
}

@ToString(includeNames = true)
class MTurkRegisterOpts {
  Boolean sandbox
}

@ToString(includeNames = true)
class ProlificCompleteOpts {
  String completionCode
  String message
  DateTime completedAt
  Double bonus
  Boolean noFeedback
}

@ToString(includeNames = true)
class MTurkCompleteOpts {
  Double bonus
  Boolean noFeedback
  String reason
}

// ---------------------------------------------------------------------------
// RecruitmentClient -- one tracked participant
// ---------------------------------------------------------------------------
// State machine: "pending" (registered, not yet placed) -> "waiting" (sitting in the lobby) ->
// "active" (in a running game) -> "completed". "removed" is a terminal off-ramp. This single
// per-client record subsumes the old pending/in-game/completed id sets: a "pending id" is just a
// client whose state == "pending".
class RecruitmentClient {
  String id
  Date joinedAt
  Date waitingAt
  Date startedGameAt
  Date completedGameAt
  String state
  String gameId

  RecruitmentClient(String id) {
    this.id = id
    this.joinedAt = new Date()
    this.state = "pending"
    this.gameId = null
  }

  public setState(String state) {
    this.state = state
  }
}

// ---------------------------------------------------------------------------
// RecruitmentController -- the single recruitment authority
// ---------------------------------------------------------------------------
// Owns BOTH halves of recruitment that used to live in two places:
//   * the panel lifecycle for each participant (register/complete for Prolific & MTurk, writing
//     _system.recruitment.* that the Finish*.vue screens read), and
//   * the cohort/game accounting the lobby relies on (client states, active/completed games),
//     which previously lived in a second class of the same name inside waiting_room.groovy.
// `WaitingRoom` no longer owns a controller; it is handed this one via setRecruitment(...) and
// reports lobby/game events to it.
//
// The "gate" (recruitmentActive) lets an experiment stop admitting NEW participants while letting
// in-progress games finish: registerProlific/registerMturk no-op once it is off, and
// stopRecruiting* flips it off and completes everyone who hasn't finished yet.
class RecruitmentController extends BreadboardBase {

  // Source identifiers. Defined on the class (not read from the RecruitmentSource binding global)
  // because Groovy classes can't see script bindings. The global map is derived from these.
  static final String SOURCE_PROLIFIC = 'prolific'
  static final String SOURCE_MTURK = 'mturk'

  // When false, registerProlific/registerMturk stop admitting new participants (in-flight games
  // continue). volatile: flipped/read from socket + timer + experiment threads.
  private volatile boolean recruitmentActive = true

  // The engine graph (`g`, a BreadboardGraph) used to resolve a player vertex from its id when
  // bulk-completing in stopRecruiting*. Untyped on purpose: typing it to the concrete graph class
  // would couple this script to that class at compile time for no benefit.
  private final Object graph

  // One RecruitmentClient per player id. CopyOnWriteArrayList: read from the logging loop while
  // socket threads register/complete. Declared as List (see the 1.8.6 note above).
  final List<RecruitmentClient> clients = new CopyOnWriteArrayList<RecruitmentClient>()
  // gameId -> running flag. Removed when the game completes.
  final Map<String, Boolean> activeGames = new ConcurrentHashMap<String, Boolean>()
  // Count of completed games (incremented under `lock`).
  int completedGames = 0

  // Recruitment goals / limits. canStartGame() gates on maxSimultaneousGames; the rest are read by
  // experiments deciding when to stopRecruiting*.
  int maxSimultaneousGames = 5
  int desiredCompletedGames = 10
  int minPlayersPerGame = 15
  int maxPlayersPerGame = 25

  private final Lock lock = new ReentrantLock(true)
  private boolean _showLogs = false
  private BBTimer _loopTimer
  private AtomicBoolean isLoopRunning = new AtomicBoolean(false)

  RecruitmentController(Object graph) {
    this.graph = graph
  }

  // --- logging loop (optional) -----------------------------------------------------------------

  public enableLogging() {
    this._showLogs = true
  }

  private log(...args) {
    if (this._showLogs) {
      println "[RecruitmentController] " + args.join(" ")
    }
  }

  // Start a periodic status log. Idempotent (a second call while running is a no-op). The lobby
  // does NOT call this -- it's an opt-in convenience for experiments that want visibility.
  public start() {
    this.lock.lock()
    try {
      if (this._loopTimer != null) return
      this._loopTimer = new BBTimer()
      this._loopTimer.scheduleAtFixedRate(this._loop as GroovyTimerTask, 0, 10000)
    } finally {
      this.lock.unlock()
    }
  }

  public stop() {
    this.lock.lock()
    try {
      if (this._loopTimer != null) {
        this._loopTimer.cancel()
        this._loopTimer = null
      }
    } finally {
      this.lock.unlock()
    }
  }

  // --- client lookup / state -------------------------------------------------------------------

  // Find the client for `clientId`, creating it (state "pending") if absent. The create path is
  // guarded by the lock so two threads registering the same id can't insert a duplicate.
  private RecruitmentClient _client(String clientId) {
    def existing = this.clients.find { it.id == clientId }
    if (existing != null) return existing
    this.lock.lock()
    try {
      existing = this.clients.find { it.id == clientId }
      if (existing != null) return existing
      def created = new RecruitmentClient(clientId)
      this.clients.add(created)
      return created
    } finally {
      this.lock.unlock()
    }
  }

  private _setState(String clientId, String state) {
    this._client(clientId).setState(state)
  }

  public clientPending(String clientId) {
    this._setState(clientId, "pending")
  }

  public clientWaiting(String clientId) {
    def client = this._client(clientId)
    client.waitingAt = new Date()
    client.setState("waiting")
  }

  public clientCompleted(String clientId) {
    this._setState(clientId, "completed")
  }

  public removeClient(String clientId) {
    this._setState(clientId, "removed")
    // Mirror the controller-side 'removed' onto the participant's study-level lifecycle so the rest of
    // breadboard (and the frontend) treats them as out of the study. The controller is keyed by client
    // id; resolve the vertex from the graph (as stopRecruiting* does) and mark it dropped (terminal).
    def v = (this.graph != null) ? this.graph.getVertex(clientId) : null
    if (v != null) setVertexStatus(v, 'dropped')
  }

  // --- games -----------------------------------------------------------------------------------

  // Mark a cohort active under one gameId. Only updates clients already known to the controller
  // (registered / waiting); ids it has never seen are ignored. Idempotent: a repeat call (e.g. the
  // lobby AND the Games registry both reporting the same game id) keeps startedGameAt from the first
  // call and leaves the active state unchanged.
  public gameStarted(String gameId, ArrayList<String> clientIds) {
    for (def client in this.clients) {
      if (clientIds.contains(client.id)) {
        client.gameId = gameId
        if (client.startedGameAt == null) client.startedGameAt = new Date()
        client.setState("active")
      }
    }
    this.activeGames.put(gameId, true)
  }

  // A game finished normally: free its slot, mark its clients completed, and count it -- but only
  // count if the game was actually active. Idempotent: a second call (e.g. the lobby's
  // groupCompleted AND Game.finish both reporting) finds the slot already gone and counts nothing.
  public gameCompleted(String gameId) {
    boolean wasActive = (this.activeGames.remove(gameId) != null)
    def gameClients = this.clients.findAll { it.gameId == gameId }
    for (def client in gameClients) {
      client.gameId = null
      client.completedGameAt = new Date()
      client.setState("completed")
    }
    if (wasActive) {
      this.lock.lock()
      try {
        this.completedGames++
      } finally {
        this.lock.unlock()
      }
    }
  }

  // A game ended WITHOUT completing (its cohort emptied / it was abandoned). Free the slot and
  // disassociate its clients, but do NOT count it toward completedGames. Idempotent: a second call
  // finds the slot already gone and does nothing.
  public gameAbandoned(String gameId) {
    if (this.activeGames.remove(gameId) == null) return
    for (def client in this.clients) {
      if (client.gameId == gameId) {
        client.gameId = null
      }
    }
  }

  // True while fewer than maxSimultaneousGames are running.
  public boolean canStartGame() {
    return this.activeGames.size() < this.maxSimultaneousGames
  }

  public boolean isRecruitmentActive() {
    return this.recruitmentActive
  }

  // --- register (gated) ------------------------------------------------------------------------

  public registerProlific(Vertex v, Map opts = [:]) {
    if (!this.recruitmentActive) return
    ProlificRegisterOpts registerOpts = new ProlificRegisterOpts(opts)
    _ensureSystem(v, 'recruitment')
    v._system.recruitment.source = SOURCE_PROLIFIC
    this.clientPending(v.id)
  }

  public registerMturk(Vertex v, Map opts = [:]) {
    if (!this.recruitmentActive) return
    MTurkRegisterOpts registerOpts = new MTurkRegisterOpts(opts)
    _ensureSystem(v, 'recruitment')
    v._system.recruitment.source = SOURCE_MTURK
    v._system.recruitment.sandbox = registerOpts.sandbox
    this.clientPending(v.id)
  }

  // --- complete --------------------------------------------------------------------------------

  public completeProlific(Vertex v, Map opts = [:]) {
    ProlificCompleteOpts completeOpts = new ProlificCompleteOpts(opts)
    _ensureSystem(v, 'recruitment')
    if (v._system.recruitment.source != SOURCE_PROLIFIC) {
      throw new IllegalArgumentException("Cannot complete prolific registration for non-prolific source")
    }
    if (!completeOpts.completionCode) {
      // A Prolific completion is meaningless without a code -- the client builds its submit URL as
      // ?cc=<completionCode>, so a null here would redirect the participant to ?cc=null.
      throw new IllegalArgumentException("Completion code is required")
    }
    v._system.recruitment.completed = true
    v._system.recruitment.completedAt = DateTime.now()
    v._system.recruitment.completionCode = completeOpts.completionCode
    v._system.recruitment.bonus = completeOpts.bonus
    v._system.recruitment.message = completeOpts.message
    v._system.recruitment.noFeedback = completeOpts.noFeedback
    setVertexStatus(v, 'completed')
    this.clientCompleted(v.id)
  }

  public completeMturk(Vertex v, Map opts = [:]) {
    MTurkCompleteOpts completeOpts = new MTurkCompleteOpts(opts)
    _ensureSystem(v, 'recruitment')
    if (v._system.recruitment.source != SOURCE_MTURK) {
      throw new IllegalArgumentException("Cannot complete mturk registration for non-mturk source")
    }
    if (!completeOpts.bonus) {
      throw new IllegalArgumentException("Bonus is required")
    }
    v._system.recruitment.completed = true
    v._system.recruitment.noFeedback = completeOpts.noFeedback
    v._system.recruitment.reason = completeOpts.reason
    v._system.recruitment.bonus = completeOpts.bonus
    setVertexStatus(v, 'completed')
    this.clientCompleted(v.id)
  }

  // --- stop recruiting -------------------------------------------------------------------------

  // Flip the gate off (no new participants admitted) and complete everyone who hasn't finished yet,
  // resolving each player's vertex from the graph. Iterates a SNAPSHOT (_completableSnapshot copies
  // the list), so completeProlific mutating client state mid-loop can't disturb the iteration.
  public stopRecruitingProlific(Map opts = [:]) {
    if (!opts.completionCode) {
      throw new IllegalArgumentException("Completion code is required to stop prolific recruitment")
    }
    this.recruitmentActive = false
    for (def client in this._completableSnapshot()) {
      def v = (this.graph != null) ? this.graph.getVertex(client.id) : null
      if (v != null) this.completeProlific(v, opts)
    }
  }

  public stopRecruitingMturk(Map opts = [:]) {
    if (!opts.bonus) {
      throw new IllegalArgumentException("Bonus is required to stop mturk recruitment")
    }
    this.recruitmentActive = false
    for (def client in this._completableSnapshot()) {
      def v = (this.graph != null) ? this.graph.getVertex(client.id) : null
      if (v != null) this.completeMturk(v, opts)
    }
  }

  // Clients eligible for bulk completion: not already completed and not removed. findAll returns a
  // fresh list, so callers iterate a stable snapshot.
  private List<RecruitmentClient> _completableSnapshot() {
    return this.clients.findAll { it.state != "completed" && it.state != "removed" }
  }

  // --- logging loop body -----------------------------------------------------------------------

  private _loop = {
    // Skip if a previous tick is still running.
    if (!isLoopRunning.compareAndSet(false, true)) {
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
      isLoopRunning.set(false)
    }
  }

}

// The single recruitment authority for the experiment. `g` is the engine graph, created by
// graph.groovy (loaded before this script -- see ScriptLoader.CORE_ORDER).
recruitment = new RecruitmentController(g)

// Hand the controller to the Games registry so Game.create/finish/abandon report game lifecycle
// automatically (see groups.groovy). GroupContext is defined in groups.groovy, loaded before this
// script. These updates are idempotent with WaitingRoom's, so the lobby + Games can both drive them.
GroupContext.bindRecruitment(recruitment)
