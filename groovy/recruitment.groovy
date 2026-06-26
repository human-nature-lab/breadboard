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

// ---------------------------------------------------------------------------
// Completion option holders -- parsed from the opts map passed to recruitment.end
// ---------------------------------------------------------------------------
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
// RecruitmentProvider -- the per-panel knowledge (Prolific, MTurk, ...)
// ---------------------------------------------------------------------------
// A provider is the only place that knows how one recruitment panel differs: its `source` label (the
// wire contract the Finish*.vue screens compare against), what to stamp at start, and how to validate
// + record a completion. The shared machinery (client states, game accounting, the gate) lives on the
// controller, which calls these hooks after running _ensureSystem(v, 'recruitment') -- so providers
// only ever touch _system.recruitment.*. Abstract (not an interface) because Groovy 1.8.6 has no
// default methods and onStart needs a no-op default.
abstract class RecruitmentProvider {
  abstract String getSource()
  void onStart(Vertex v, Map opts) {}
  // Validate opts and stamp the completion fields; throw if opts are insufficient.
  abstract void onEnd(Vertex v, Map opts)
  // Consulted only with more than one provider registered (see addProvider), to auto-route start().
  boolean matches(Vertex v) { return false }
}

class ProlificProvider extends RecruitmentProvider {
  String getSource() { return RecruitmentController.SOURCE_PROLIFIC }

  void onEnd(Vertex v, Map opts) {
    ProlificCompleteOpts o = new ProlificCompleteOpts(opts)
    if (!o.completionCode) {
      // A Prolific completion is meaningless without a code -- the client builds its submit URL as
      // ?cc=<completionCode>, so a null here would redirect the participant to ?cc=null.
      throw new IllegalArgumentException("Completion code is required")
    }
    v._system.recruitment.completionCode = o.completionCode
    v._system.recruitment.bonus = o.bonus
    v._system.recruitment.message = o.message
    v._system.recruitment.noFeedback = o.noFeedback
  }
}

class MturkProvider extends RecruitmentProvider {
  // Study-wide, so it's provider config stamped at start() rather than a per-call opt.
  Boolean sandbox = false

  String getSource() { return RecruitmentController.SOURCE_MTURK }

  void onStart(Vertex v, Map opts) {
    v._system.recruitment.sandbox = this.sandbox
  }

  void onEnd(Vertex v, Map opts) {
    MTurkCompleteOpts o = new MTurkCompleteOpts(opts)
    if (!o.bonus) {
      throw new IllegalArgumentException("Bonus is required")
    }
    v._system.recruitment.bonus = o.bonus
    v._system.recruitment.noFeedback = o.noFeedback
    v._system.recruitment.reason = o.reason
  }
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
// in-progress games finish: start() no-ops once it is off, and stopRecruiting() flips it off and
// ends everyone who hasn't finished yet.
class RecruitmentController extends BreadboardBase {

  // Source identifiers. Defined on the class (not read from the RecruitmentSource binding global)
  // because Groovy classes can't see script bindings. The global map is derived from these.
  static final String SOURCE_PROLIFIC = 'prolific'
  static final String SOURCE_MTURK = 'mturk'

  // When false, registerProlific/registerMturk stop admitting new participants (in-flight games
  // continue). volatile: flipped/read from socket + timer + experiment threads.
  private volatile boolean recruitmentActive = true

  // The engine graph (`g`, a BreadboardGraph) used to resolve a player vertex from its id when
  // bulk-completing in stopRecruiting. Untyped on purpose: typing it to the concrete graph class
  // would couple this script to that class at compile time for no benefit.
  private final Object graph

  // Registered recruitment providers, keyed by source. setProvider installs one (the common single-
  // panel case); addProvider adds more for a deployment that serves several panels and auto-routes
  // start() via RecruitmentProvider.matches(v). Declared as Map per the 1.8.6 note above.
  private final Map<String, RecruitmentProvider> providers = new ConcurrentHashMap<String, RecruitmentProvider>()
  // Provider used by start() when no registered provider claims the participant. volatile: set from
  // the experiment script, read from socket/game threads.
  private volatile RecruitmentProvider defaultProvider

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

  // --- providers -------------------------------------------------------------------------------

  // Install THE provider for this experiment (the common single-panel case): clears any others and
  // makes it the default. Use addProvider instead to serve several panels from one deployment.
  public setProvider(RecruitmentProvider provider) {
    this.providers.clear()
    this.providers[provider.getSource()] = provider
    this.defaultProvider = provider
  }

  // Register an additional provider. With more than one installed, start() auto-routes each
  // participant to the first provider whose matches(v) is true, falling back to the default.
  public addProvider(RecruitmentProvider provider) {
    this.providers[provider.getSource()] = provider
    if (this.defaultProvider == null) this.defaultProvider = provider
  }

  private RecruitmentProvider _resolveProviderForStart(Vertex v) {
    for (def p in this.providers.values()) {
      if (p.matches(v)) return p
    }
    if (this.defaultProvider == null) {
      throw new IllegalStateException("No recruitment provider configured; call recruitment.setProvider(...) first")
    }
    return this.defaultProvider
  }

  // --- start (gated) ---------------------------------------------------------------------------

  // No-ops once the gate is closed (see stopRecruiting).
  public start(Vertex v, Map opts = [:]) {
    if (!this.recruitmentActive) return
    RecruitmentProvider provider = _resolveProviderForStart(v)
    _ensureSystem(v, 'recruitment')
    v._system.recruitment.source = provider.getSource()
    provider.onStart(v, opts)
    this.clientPending(v.id)
  }

  // --- end -------------------------------------------------------------------------------------

  // Dispatch to the provider recorded at start() to complete the participant. Throws if start() was
  // never called (no recorded source to dispatch on).
  public end(Vertex v, Map opts = [:]) {
    _ensureSystem(v, 'recruitment')
    def source = v._system.recruitment.source
    RecruitmentProvider provider = (source != null) ? this.providers[source] : null
    if (provider == null) {
      throw new IllegalArgumentException("No recruitment provider for source '${source}'; was start() called for this participant?")
    }
    // Validate + stamp the provider-specific fields FIRST, so a bad-opts failure (e.g. a missing
    // completion code) leaves no half-completed state behind.
    provider.onEnd(v, opts)
    v._system.recruitment.completed = true
    v._system.recruitment.completedAt = DateTime.now()
    setVertexStatus(v, 'completed')
    this.clientCompleted(v.id)
  }

  // --- stop recruiting -------------------------------------------------------------------------

  // Close the gate (no new participants admitted; in-flight games continue) and end everyone who
  // hasn't finished yet, dispatching each to the provider they started under. Iterates a SNAPSHOT
  // (_completableSnapshot copies the list) so end() mutating client state mid-loop can't disturb the
  // iteration. `opts` must satisfy whatever provider(s) are in play -- e.g. a Prolific completionCode
  // or an MTurk bonus -- otherwise end() throws on the first participant that needs the missing opt.
  public stopRecruiting(Map opts = [:]) {
    this.recruitmentActive = false
    for (def client in this._completableSnapshot()) {
      def v = (this.graph != null) ? this.graph.getVertex(client.id) : null
      if (v != null) this.end(v, opts)
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
