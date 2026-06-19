import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

// Groovy 1.8.9 note: this file (and groups_test.groovy) must stay 1.8.9-compatible.
// No closure->functional-interface coercion (Map.computeIfAbsent / Collection.removeIf),
// no lambdas or `::` method refs. The JDK is 8, so Java 8 *library* methods are callable.

// ---------------------------------------------------------------------------
// GroupContext
// ---------------------------------------------------------------------------
// Static holder for the engine handles the framework classes need: the graph
// `g`, the global PlayerActions `a`, and the EventBus `events`. These are bound
// ONCE, at groups.groovy load time, from the script binding -- by then the core
// scripts have run (ScriptLoader.CORE_ORDER loads graph/actions/events before
// groups), so all three are present. This lets Game/Games reach the engine
// without any experiment-side wiring. (Not `c` -- it is unbound in the test
// harness and the closure-based steps see experiment globals via the binding
// anyway.)
class GroupContext {
  static Object g
  static Object a
  static Object events

  static void bind(Object gIn, Object aIn, Object eventsIn) {
    if (gIn != null) g = gIn
    if (aIn != null) a = aIn
    if (eventsIn != null) events = eventsIn
  }
}

// ---------------------------------------------------------------------------
// Games -- registry + factory
// ---------------------------------------------------------------------------
// A "game" is a running instance played by a cohort of players. `define`
// records a default builder closure (the experiment's game definition);
// `create` instantiates a Game, registers it, adds players, and runs the
// builder (the per-call builder wins over the defined one).
class Games {
  // Event the client sends when a player picks a group-scoped choice. The
  // frontend routes `_route: 'group'` clicks here via sendGroupChoice(uid).
  static final String SUBMIT_EVENT = "group-action-submit"

  private static final Map<String, Object> byId = new ConcurrentHashMap<>()
  private static Closure defaultBuilder = null

  // Record the default game definition. The builder is run against each Game
  // created without an explicit builder.
  static void define(Closure builder) {
    defaultBuilder = builder
  }

  // Instantiate, register, populate, and build a Game.
  static Game create(Object id, List players, Object parameters = null, Closure builder = null) {
    def game = new Game(id as String, parameters)
    byId[id as String] = game
    players?.each { game.addPlayer(it) }
    def b = (builder != null) ? builder : defaultBuilder
    if (b != null) {
      b.delegate = game
      b.resolveStrategy = Closure.DELEGATE_FIRST
      b(game)
    }
    return game
  }

  static Game get(Object id) { byId[id as String] }

  // Resolve the game a player belongs to via `_system.groupId`.
  static Game forPlayer(Object v) {
    def gid = v?._system?.groupId
    gid == null ? null : byId[gid as String]
  }

  static Collection<Object> all() { byId.values() }

  static void remove(Object id) { byId.remove(id as String) }
}

// ---------------------------------------------------------------------------
// Game -- one running instance for a cohort
// ---------------------------------------------------------------------------
class Game {
  final String id
  private final Object _parameters       // opaque, read-only after construction
  Map state = new LinkedHashMap()        // mutable scratch space for the experiment

  // Steps are closures keyed by name: steps[name] == [run: Closure, done: Closure].
  private final Map<String, Map> steps = new LinkedHashMap<>()
  private String currentStepName = null

  // All players ever added (active and dropped). `players` filters to active.
  private final List members = new ArrayList()

  // Per-step pending asks. pending[stepName] == list of entry maps:
  //   [player, uid, stepName, handler, listener, warnTimer, dropTimer, aiTimer]
  private final Map<String, List> pending = new LinkedHashMap<>()

  private final Lock lock = new ReentrantLock(true)
  private final Random rng = new Random()

  // Lifecycle
  private boolean finished = false
  private Closure onFinishClosure = null
  private Closure onAbandonClosure = null

  // Idle/drop config (mirrors PlayerActions in actions.groovy; times in seconds).
  private Object idleTime = null
  private Object warnTime = null
  private Object dropTime = null
  private boolean dropPlayersEnabled = false
  private Closure dropPlayerClosure = null

  // AI submit delay (ms). Small by default so test AIs resolve quickly;
  // experiments can raise it for human-like pacing.
  Number aiDelay = 50

  // Group-aware facade over the global PlayerActions `a` (see GameActions). Created up front so
  // `game.a` is always live, and so step closures (which delegate to the game) resolve a bare
  // `a.addEvent(...)` to the tagging version.
  private final GameActions _actions

  Game(String id, Object parameters) {
    this.id = id
    this._parameters = parameters
    this._actions = new GameActions(this)
  }

  // --- read-only parameters / state ---

  Object getParameters() { _parameters }

  // Group-aware action queue: `game.a.addEvent(...)` tags events with this game's id/step; every
  // other call forwards to the global `a`.
  GameActions getA() { _actions }

  // Name of the step `go` last switched to (null before the first go), thread-safe.
  String getCurrentStep() {
    lock.lock()
    try { return currentStepName } finally { lock.unlock() }
  }

  // --- membership ---

  // Add a player to the cohort: tag it with this game's id and mark active.
  void addPlayer(Object v) {
    if (v == null) return
    if (v._system == null) v._system = [:]
    v._system.groupId = this.id
    v._system.active = true
    lock.lock()
    try { if (!members.contains(v)) members.add(v) } finally { lock.unlock() }
  }

  // Fill the cohort up to `target` players with AI. AIs are added with a no-op
  // global behavior so the platform's PlayerAI driver does NOT fire against the
  // (empty) global action map -- group AIs are driven by `ask` instead.
  List addAI(int target) {
    int need = target - getPlayers().size()
    if (need <= 0) return []
    def noop = { p -> }
    def added = GroupContext.g.addAI(GroupContext.a, need, noop)
    added?.each { addPlayer(it) }
    return added
  }

  // Active members only -- dropped players (active == false) vanish immediately.
  List getPlayers() {
    lock.lock()
    try { return members.findAll { it?._system?.active } } finally { lock.unlock() }
  }

  // --- steps ---

  // Register a step's run/done closures. Delegating to the game lets step
  // bodies call ask/go/finish/players unqualified while still seeing experiment
  // globals via their owner (the binding) on fall-through.
  void step(String name, Map handlers) {
    def runC = handlers?.run
    def doneC = handlers?.done
    if (runC instanceof Closure) { runC.delegate = this; runC.resolveStrategy = Closure.DELEGATE_FIRST }
    if (doneC instanceof Closure) { doneC.delegate = this; doneC.resolveStrategy = Closure.DELEGATE_FIRST }
    lock.lock()
    try {
      if (steps.containsKey(name)) {
        throw new IllegalStateException("Step '$name' already defined in game $id")
      }
      steps[name] = [run: runC, done: doneC]
    } finally { lock.unlock() }
  }

  // Make `name` the current step and run its `run` closure.
  void go(String name) {
    Closure runC
    lock.lock()
    try {
      def step = steps[name]
      if (step == null) throw new IllegalArgumentException("No step '$name' in game $id")
      currentStepName = name
      runC = step.run
    } finally { lock.unlock() }
    if (runC != null) runC()   // outside the lock: run may re-enter ask/go
  }

  // --- asking players ---

  // Push a group-scoped choice to the player, register a resolver for their
  // submit, arm idle/drop (if enabled), and auto-drive AI. When the current
  // step's pending set drains (via submit or drop), the step's `done` fires.
  void ask(Object player, Map choice, Closure handler) {
    if (player == null) throw new IllegalArgumentException("ask() requires a player")
    String stepName
    lock.lock()
    try { stepName = currentStepName } finally { lock.unlock() }
    if (stepName == null) {
      throw new IllegalStateException("ask() called before any go() in game $id")
    }
    if (choice == null) choice = [:]
    if (choice.uid == null) choice.uid = UUID.randomUUID().toString()
    if (choice.name == null) choice.name = choice.uid

    def entry = [
      player:    player,
      uid:       choice.uid,
      stepName:  stepName,
      handler:   handler,
      listener:  null,
      warnTimer: null,
      dropTimer: null,
      aiTimer:   null,
    ]

    lock.lock()
    try {
      def list = pending[stepName]
      if (list == null) { list = new ArrayList(); pending[stepName] = list }
      list << entry
    } finally { lock.unlock() }

    assignChoice(player, choice)

    // One listener per ask, filtering on its own uid; resolved (and detached) on submit.
    def listener = { v, data ->
      if (data?.uid != entry.uid) return
      resolve(entry, v, data)
    }
    entry.listener = listener
    player.on(Games.SUBMIT_EVENT, listener)

    armIdleTimer(entry)

    if (player.getProperty("ai") == 1) driveAI(entry)
  }

  // Convenience: a named choice with a handler.
  void ask(Object player, String name, Closure handler) {
    ask(player, [name: name], handler)
  }

  // --- dropping ---

  // In-game drop: mark inactive, drain pending asks, cancel timers, disconnect
  // edges. If that empties the cohort and the game isn't finished, abandon it;
  // otherwise let the current step complete if its queue drained.
  void drop(Object player) {
    if (player == null) return
    if (player._system != null) player._system.active = false

    List affected = []
    lock.lock()
    try {
      def pid = player.id
      pending.each { stepName, list ->
        def matches = list.findAll { it.player?.id == pid }
        list.removeAll(matches)
        affected.addAll(matches)
      }
      def emptyKeys = pending.findAll { k, v -> v.isEmpty() }.keySet().toList()
      emptyKeys.each { pending.remove(it) }
    } finally { lock.unlock() }

    affected.each { e ->
      cancelEntryTimers(e)
      if (e.listener != null) {
        try { player.off(Games.SUBMIT_EVENT, e.listener) } catch (Exception ex) { /* ignore */ }
      }
    }
    unassignAllGroupChoices(player)
    try { GroupContext.g.removeEdges(player) } catch (Exception ex) { /* ignore */ }

    if (!finished && getPlayers().isEmpty()) {
      abandon()
    } else {
      maybeFireDone()
    }
  }

  // --- lifecycle ---

  void onFinish(Closure c) { onFinishClosure = c }
  void onAbandon(Closure c) { onAbandonClosure = c }

  // Normal completion.
  void finish() {
    boolean already
    lock.lock()
    try { already = finished; finished = true } finally { lock.unlock() }
    if (already) return
    if (onFinishClosure != null) { try { onFinishClosure() } catch (Exception e) { logErr("onFinish", e) } }
    dispose()
  }

  // Cohort emptied before finishing.
  private void abandon() {
    boolean already
    lock.lock()
    try { already = finished; finished = true } finally { lock.unlock() }
    if (already) return
    if (onAbandonClosure != null) { try { onAbandonClosure() } catch (Exception e) { logErr("onAbandon", e) } }
    dispose()
  }

  // Release AI resources, untag players, drop all pending, unregister.
  private void dispose() {
    List membersCopy
    List pendingEntries = []
    lock.lock()
    try {
      membersCopy = new ArrayList(members)
      pending.each { stepName, list -> pendingEntries.addAll(list) }
    } finally { lock.unlock() }

    pendingEntries.each { e -> cancelEntryTimers(e) }

    membersCopy.each { v ->
      if (v?.getProperty("ai") == 1) {
        try { GroupContext.a.ai.remove(v) } catch (Exception e) { /* ignore */ }
        try { GroupContext.g.removePlayer(v.id) } catch (Exception e) { /* ignore */ }
      }
      if (v?._system?.groupId == this.id) {
        v._system.remove('groupId')
        v._system.remove('active')
      }
    }

    lock.lock()
    try {
      members.clear()
      pending.clear()
      steps.clear()
      currentStepName = null
    } finally { lock.unlock() }

    Games.remove(this.id)
  }

  // --- idle / drop configuration (mirrors PlayerActions) ---

  void setIdleTime(Object t) { this.idleTime = t }
  void setWarnTime(Object t) { this.warnTime = t }
  void setDropTime(Object t) { this.dropTime = t }
  void setDropPlayers(boolean enabled) { this.dropPlayersEnabled = enabled }
  void setDropPlayerClosure(Closure c) { this.dropPlayerClosure = c }
  // Alias matching the SharedTimer/onDone style used elsewhere.
  void onDrop(Closure c) { this.dropPlayerClosure = c }

  // For tests/debugging: count outstanding asks for a step (current if null).
  int pendingCount(String stepName = null) {
    lock.lock()
    try {
      String key = (stepName != null) ? stepName : currentStepName
      return (pending[key] ?: []).size()
    } finally { lock.unlock() }
  }

  // --- internals ---

  private void assignChoice(Object player, Map choice) {
    if (player.choices == null) player.choices = []
    // Tag the choice group-scoped so the client routes the click to this game's
    // submit handler instead of the platform's global a.choose(uid). Push a copy
    // so the caller's map isn't mutated.
    player.choices << (choice + [_route: 'group'])
  }

  private void unassignChoice(Object player, Object uid) {
    if (player?.choices instanceof List) {
      player.choices = player.choices.findAll { it?.uid != uid }
    }
  }

  private void unassignAllGroupChoices(Object player) {
    if (player?.choices instanceof List) {
      player.choices = player.choices.findAll { it?._route != 'group' }
    }
  }

  // A player submitted their choice: detach, cancel timers, drop from pending,
  // run the caller's handler, then maybe fire the step's done.
  private void resolve(Map entry, Object v, Object data) {
    cancelEntryTimers(entry)
    if (entry.listener != null) {
      try { v.off(Games.SUBMIT_EVENT, entry.listener) } catch (Exception ex) { /* ignore */ }
    }
    unassignChoice(v, entry.uid)

    String stepName = entry.stepName
    lock.lock()
    try {
      def list = pending[stepName]
      if (list != null) {
        list.remove(entry)
        if (list.isEmpty()) pending.remove(stepName)
      }
    } finally { lock.unlock() }

    if (entry.handler != null) {
      try { entry.handler(v, data) } catch (Exception e) { logErr("ask handler", e) }
    }

    maybeFireDone()
  }

  // Fire the current step's done closure iff its pending queue has drained.
  private void maybeFireDone() {
    boolean empty
    Closure doneC = null
    lock.lock()
    try {
      String stepName = currentStepName
      empty = stepName != null && !pending.containsKey(stepName)
      if (empty) {
        def step = steps[stepName]
        doneC = (step != null) ? step.done : null
      }
    } finally { lock.unlock() }
    if (empty && doneC != null) doneC()   // outside the lock: done may re-enter go/ask/finish
  }

  // Auto-submit for an AI player after a short delay. The option is chosen on
  // THIS (eval) thread to avoid racing the choice write; the timer thread only
  // emits the submit CustomEvent, exactly like a real client.
  private void driveAI(Map entry) {
    def player = entry.player
    def groupChoices = (player.choices ?: []).findAll { it?._route == 'group' }
    def chosenUid = groupChoices ? groupChoices[rng.nextInt(groupChoices.size())].uid : entry.uid
    def timer = new BBTimer()
    entry.aiTimer = timer
    // GDK Timer.runAfter has no error trap of its own; guard the body so a late fire during
    // teardown can't throw uncaught on the daemon timer thread.
    timer.runAfter(aiDelay as int) {
      try {
        GroupContext.events.emit("CustomEvent",
          [playerId: player.id, eventName: Games.SUBMIT_EVENT, data: [uid: chosenUid]],
          [clientId: player.id])
      } catch (Throwable t) {
        logErr("AI auto-submit", t)
      }
    }
  }

  // Arm a warn->drop sequence for this ask, mirroring PlayerActions.setIdleTimer:
  // a BBTimer for the warn delay; on fire, push a "You will be dropped in:"
  // SharedTimer countdown (appearance 'warning') into the player's timers and,
  // when it expires, invoke the drop handler (default: drop(player)). Both are
  // cancelled when the player chooses (cancelEntryTimers).
  private void armIdleTimer(Map entry) {
    boolean armed = (idleTime != null || (warnTime != null && dropTime != null)) && dropPlayersEnabled
    if (!armed) return
    def player = entry.player
    def time1 = (warnTime != null) ? warnTime : idleTime
    def time2 = (dropTime != null) ? dropTime : idleTime

    def warnTimer = new BBTimer()
    entry.warnTimer = warnTimer
    // Both timer bodies are guarded: a late fire during teardown must not throw uncaught on the
    // daemon timer thread (GDK Timer.runAfter / SharedTimer have no trap of their own).
    warnTimer.runAfter((time1 * 1000) as int) {
      try {
        def shared = new SharedTimer([
          time:       time2,
          name:       "dropTimer",
          timerText:  "You will be dropped in: ",
          appearance: "warning",
          direction:  "down",
          player:     player,
          result:     {
            try {
              def handler = (dropPlayerClosure != null) ? dropPlayerClosure : { p -> drop(p) }
              handler(player)
            } catch (Throwable t) {
              logErr("idle/drop handler", t)
            }
          }
        ])
        entry.dropTimer = shared
      } catch (Throwable t) {
        logErr("idle/warn timer", t)
      }
    }
  }

  private void cancelEntryTimers(Map entry) {
    if (entry.warnTimer != null) { try { entry.warnTimer.cancel() } catch (Exception e) { /* ignore */ } ; entry.warnTimer = null }
    if (entry.dropTimer != null) { try { entry.dropTimer.cancel() } catch (Exception e) { /* ignore */ } ; entry.dropTimer = null }
    if (entry.aiTimer != null)   { try { entry.aiTimer.cancel() }   catch (Exception e) { /* ignore */ } ; entry.aiTimer = null }
  }

  private void logErr(String where, Throwable e) {
    println "[Game ${id}] $where threw: $e"
    e.printStackTrace()
  }
}

// ---------------------------------------------------------------------------
// GameActions -- group-aware facade over the global PlayerActions `a`
// ---------------------------------------------------------------------------
// The only behavior it adds is event tagging: addEvent merges the game's id
// (and current step) into the data before delegating to the global tracker, so
// every group event is attributable to its cohort. EventTracker.track flattens
// the map into name/value rows, so `groupId` lands as saved data. Everything
// else (choose, ai, size, ...) forwards to the global `a` unchanged.
class GameActions {
  private final Game game

  GameActions(Game game) { this.game = game }

  // Track an event, tagging it with the game's id and current step (without
  // clobbering caller-supplied keys), then delegate to the global `a`.
  void addEvent(String name, Map data = [:]) {
    def enriched = new LinkedHashMap()
    if (data != null) enriched.putAll(data)
    if (!enriched.containsKey('groupId')) enriched.put('groupId', game.id)
    def step = game.getCurrentStep()
    if (step != null && !enriched.containsKey('step')) enriched.put('step', step)
    GroupContext.a.addEvent(name, enriched)
  }

  // Transparent proxy: anything not defined above falls through to the global `a`.
  def methodMissing(String name, args) {
    return GroupContext.a.invokeMethod(name, args)
  }

  def propertyMissing(String name) {
    return GroupContext.a.getProperty(name)
  }

  void propertyMissing(String name, value) {
    GroupContext.a.setProperty(name, value)
  }
}

// ---------------------------------------------------------------------------
// Treatment / TreatmentManager -- assign cohorts to experimental conditions
// ---------------------------------------------------------------------------
// A treatment is one experimental condition: a name + an opaque `parameters`
// object (the same kind handed to Games.create, which becomes Game.parameters)
// + a completion `target`. The manager hands out treatments to new games and
// tracks the lifecycle so each condition reaches its target number of COMPLETED
// games. Wiring (next()-only):
//
//   def t = treatments.next()                 // null once every treatment is full
//   if (t != null) {
//     def game = Games.create(id, players, t.parameters)
//     game.state.treatment = t.name
//     game.onFinish  { treatments.complete(t) }
//     game.onAbandon { treatments.release(t) }
//   }
class Treatment {
  final String name
  final Object parameters
  final int target
  // Owned by the TreatmentManager: only read/written inside its lock.
  int completed = 0
  int inFlight = 0

  Treatment(String name, Object parameters, int target) {
    this.name = name
    this.parameters = parameters
    this.target = target
  }

  Object getParameters() { parameters }

  // Enough games are done or in progress that the target can be met -> not
  // eligible for a new assignment right now.
  boolean isFull() { (completed + inFlight) >= target }

  // The target number of completions has actually been reached.
  boolean isMet() { completed >= target }
}

class TreatmentManager {
  private final Map<String, Treatment> byName = new LinkedHashMap<>()
  private final List<Treatment> order = new ArrayList<>()
  private final Random rng = new Random()
  private final Lock lock = new ReentrantLock(true)

  // Define a treatment. `target` is the number of COMPLETED games desired (>= 1).
  Treatment treatment(String name, Object parameters, int target) {
    if (name == null) throw new IllegalArgumentException("treatment requires a name")
    if (target < 1) throw new IllegalArgumentException("treatment '$name' target must be >= 1 (got $target)")
    lock.lock()
    try {
      if (byName.containsKey(name)) throw new IllegalStateException("treatment '$name' already defined")
      def t = new Treatment(name, parameters, target)
      byName[name] = t
      order << t
      return t
    } finally { lock.unlock() }
  }

  // Full named-map form: treatment(name: 'A', parameters: paramsA, target: 20)
  Treatment treatment(Map opts) {
    return treatment(opts?.name as String, opts?.parameters, asTarget(opts?.target))
  }

  // Mixed positional + trailing named args (Groovy gathers the named args into a
  // leading map): treatment('A', paramsA, target: 20)
  Treatment treatment(Map opts, String name, Object parameters) {
    return treatment(name, parameters, asTarget(opts?.target))
  }

  // Define one treatment per combination in the cartesian product of the given variables.
  // `vars` maps each variable name to its list of values, e.g.
  //   factorial([a: [1, 2, 3], b: [5, 50, 500], c: [4, 3.5, 3]], 10)   // -> 27 treatments
  // Each treatment's name is the combination rendered as "k=v,..." (deterministic and unique) and
  // its target is `target` completed games. By default `parameters` is a read-only map of the
  // combination; pass a `paramsBuilder` to turn each combo map into your own params object:
  //   factorial([a: [1, 2], b: [5, 50]], 10) { combo -> new Params(combo.a, combo.b) }
  // Returns the list of treatments created, in product order (last variable varies fastest).
  List factorial(Map vars, int target, Closure paramsBuilder = null) {
    if (vars == null || vars.isEmpty()) {
      throw new IllegalArgumentException("factorial requires at least one variable")
    }
    if (target < 1) {
      throw new IllegalArgumentException("factorial target must be >= 1 (got $target)")
    }
    vars.each { k, values ->
      if (!(values instanceof Collection) || values.isEmpty()) {
        throw new IllegalArgumentException("variable '$k' must have a non-empty list of values")
      }
    }

    // Cartesian product, preserving variable order.
    List<Map> combos = [new LinkedHashMap()]
    vars.each { k, values ->
      def expanded = []
      combos.each { base ->
        values.each { v ->
          def m = new LinkedHashMap(base)
          m[k] = v
          expanded << m
        }
      }
      combos = expanded
    }

    def created = []
    combos.each { combo ->
      def name = combo.collect { k, v -> "$k=$v" }.join(',')
      def params = (paramsBuilder != null) ? paramsBuilder(combo) : Collections.unmodifiableMap(new LinkedHashMap(combo))
      created << treatment(name, params, target)
    }
    return created
  }

  // Reserve a slot for a new game and return a uniformly-random eligible treatment,
  // or null when every treatment is full (target met or enough games in flight).
  Treatment next() {
    lock.lock()
    try {
      def eligible = order.findAll { !it.isFull() }
      if (eligible.isEmpty()) return null
      def t = eligible[rng.nextInt(eligible.size())]
      t.inFlight = t.inFlight + 1
      return t
    } finally { lock.unlock() }
  }

  // A game for this treatment finished: consume its reserved slot permanently.
  void complete(Treatment t) {
    if (t == null) return
    lock.lock()
    try {
      def m = byName[t.name]
      if (m == null) return
      if (m.inFlight > 0) m.inFlight = m.inFlight - 1
      m.completed = m.completed + 1
    } finally { lock.unlock() }
  }

  // A game for this treatment abandoned before finishing: free its slot so the
  // treatment can be assigned again.
  void release(Treatment t) {
    if (t == null) return
    lock.lock()
    try {
      def m = byName[t.name]
      if (m == null) return
      if (m.inFlight > 0) m.inFlight = m.inFlight - 1
    } finally { lock.unlock() }
  }

  Treatment get(String name) {
    lock.lock()
    try { return byName[name] } finally { lock.unlock() }
  }

  Collection<Treatment> all() {
    lock.lock()
    try { return new ArrayList(order) } finally { lock.unlock() }
  }

  // True once every treatment has reached its completion target.
  boolean isComplete() {
    lock.lock()
    try { return !order.isEmpty() && order.every { it.isMet() } } finally { lock.unlock() }
  }

  // Snapshot of per-treatment counts, for logging/debugging.
  Map stats() {
    lock.lock()
    try {
      def s = new LinkedHashMap()
      order.each { s[it.name] = [target: it.target, completed: it.completed, inFlight: it.inFlight] }
      return s
    } finally { lock.unlock() }
  }

  private static int asTarget(Object raw) {
    if (raw == null) throw new IllegalArgumentException("treatment requires a 'target' (number of completed games)")
    return raw as int
  }
}

// Bind the engine handles from the script binding at load time (see GroupContext).
GroupContext.bind(g, a, events)
