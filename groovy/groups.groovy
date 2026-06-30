import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import groovy.json.JsonBuilder
import groovy.json.JsonSlurper

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
  // The recruitment controller (recruitment.groovy). Bound separately, AFTER groups.groovy loads,
  // because recruitment.groovy loads later (see ScriptLoader.CORE_ORDER) -- it can't exist yet at
  // groups.groovy load time. Stays null until then; Games/Game guard on it being non-null.
  static Object recruitment

  static void bind(Object gIn, Object aIn, Object eventsIn) {
    if (gIn != null) g = gIn
    if (aIn != null) a = aIn
    if (eventsIn != null) events = eventsIn
  }

  static void bindRecruitment(Object rec) {
    if (rec != null) recruitment = rec
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
    // Report the cohort start to the recruitment controller (if one is bound). Done before the
    // builder runs, so the reported cohort is the human players, not AI added during the build.
    _notifyRecruitmentGameStarted(game)
    def b = (builder != null) ? builder : defaultBuilder
    if (b != null) {
      b.delegate = game
      b.resolveStrategy = Closure.DELEGATE_FIRST
      b(game)
    }
    return game
  }

  // Best-effort: tell the bound recruitment controller a game started. Idempotent with
  // WaitingRoom.gameStarted (both key on the game id), so the canonical lobby flow
  // (onGroupReady -> Games.create with the same id) does NOT double-count. Player ids that aren't
  // recruitment clients (e.g. AI) are ignored by the controller. Guarded so a recruitment hiccup
  // can never break game creation.
  private static void _notifyRecruitmentGameStarted(Game game) {
    try {
      def rec = GroupContext.recruitment
      if (rec != null) rec.gameStarted(game.id, game.getPlayers()*.id)
    } catch (Exception e) {
      println "[Games] recruitment.gameStarted threw: $e"
    }
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

  // Per-step pending decisions. pending[stepName] == list of entry maps:
  //   [player, uids(Set), buttons(List), stepName, listener, warnTimer, dropTimer, aiTimer, cancelled]
  // Each `buttons` element is one option: [uid, name, result(Closure|null), event].
  private final Map<String, List> pending = new LinkedHashMap<>()

  private final Lock lock = new ReentrantLock(true)

  // Lifecycle. `finished` is read outside the lock in drop(), so keep it volatile.
  private volatile boolean finished = false
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

  // RNG for AI option selection (mirrors PlayerAI.defaultBehavior's random pick).
  private final Random _rng = new Random()

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
    def existing = v._system.groupId
    if (existing != null && existing != this.id) {
      println "[Game ${id}] addPlayer: player ${v.id} was already tagged for game ${existing}; reassigning to ${this.id}"
    }
    v._system.groupId = this.id
    // Membership is study-level: a player counts as a member while their _system.status is 'active'.
    // Vertices default to 'active' at creation, so only seed it if somehow unset -- and never override
    // a terminal status, so a dropped/kicked/completed player is not silently re-activated.
    if (v._system.status == null) v._system.status = 'active'
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

  // Active members only -- a player whose study-level _system.status has left 'active'
  // (dropped/kicked/completed) vanishes immediately.
  List getPlayers() {
    lock.lock()
    try { return members.findAll { it?._system?.status == 'active' } } finally { lock.unlock() }
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
  //
  // One ask == ONE decision presenting one or more mutually-exclusive options, mirroring the
  // platform's a.add. Every option becomes a button on the player; picking ANY option resolves the
  // single decision -- it clears ALL of that decision's buttons, runs the chosen option's `result`
  // closure, and removes the decision from the step's pending set. When the set drains (via submit
  // or drop), the step's `done` fires.
  //
  // Each option is a Map. Recognized keys (a.add parity):
  //   name            button label (defaults to the uid)
  //   result/results  Closure run when this option is chosen; invoked as result(player, data).
  //                   Declare fewer params if you don't need them -- { v -> } and { -> } also work.
  //   event           [name: ..., data: ...] tracked via game.a.addEvent when this option is chosen
  //   uid             optional stable id (auto-generated when absent)
  // Any other keys (e.g. `class`, `custom`) are forwarded verbatim to the client.
  //
  //   game.ask(player,
  //     [name: 'Cooperate', result: { v, data -> ... }],
  //     [name: 'Defect',    result: { v, data -> ... }])
  //
  // An optional leading init closure runs once, just before the buttons are shown:
  //   game.ask(player, { /* per-ask setup */ }, [name: 'A', result: {...}], [name: 'B', result: {...}])
  void ask(Object player, Map... options) {
    ask(player, (Closure) null, options)
  }

  void ask(Object player, Closure init, Map... options) {
    if (player == null) throw new IllegalArgumentException("ask() requires a player")
    if (options == null || options.length == 0) {
      throw new IllegalArgumentException("ask() requires at least one option")
    }
    String stepName
    lock.lock()
    try { stepName = currentStepName } finally { lock.unlock() }
    if (stepName == null) {
      throw new IllegalStateException("ask() called before any go() in game $id")
    }

    if (init != null) {
      init.delegate = this
      init.resolveStrategy = Closure.DELEGATE_FIRST
      try { init() } catch (Exception e) { logErr("ask init", e) }
    }

    // Normalize each option into a client-facing button map (sent to the player) plus an internal
    // record carrying its result closure. A copy is taken so the caller's option map isn't mutated.
    List buttons = []
    List choiceMaps = []
    for (Map opt : options) {
      if (opt == null) throw new IllegalArgumentException("ask() option must not be null")
      def result = (opt.result != null) ? opt.result : opt.results   // accept result or results
      if (result != null && !(result instanceof Closure)) {
        throw new IllegalArgumentException("ask() option 'result' must be a Closure")
      }
      String uid = (opt.uid != null) ? opt.uid.toString() : UUID.randomUUID().toString()
      String name = (opt.name != null) ? opt.name.toString() : uid

      def choiceMap = new LinkedHashMap(opt)
      choiceMap.remove('result')
      choiceMap.remove('results')
      choiceMap.remove('event')
      choiceMap.uid = uid
      choiceMap.name = name

      buttons << [uid: uid, name: name, result: result, event: opt.event]
      choiceMaps << choiceMap
    }

    def entry = [
      player:    player,
      uids:      buttons.collect { it.uid } as Set,
      buttons:   buttons,
      stepName:  stepName,
      listener:  null,
      warnTimer: null,
      dropTimer: null,
      aiTimer:   null,
      cancelled: false,
    ]

    lock.lock()
    try {
      def list = pending[stepName]
      if (list == null) { list = new ArrayList(); pending[stepName] = list }
      list << entry
    } finally { lock.unlock() }

    // Render every option as a button. assignChoice tags each _route:'group' so the client routes
    // the click to this game's submit handler instead of the platform's global a.choose(uid).
    choiceMaps.each { assignChoice(player, it) }

    // One listener per decision, resolved by ANY of its option uids; detached on resolve.
    def uids = entry.uids
    def listener = { v, data ->
      if (!uids.contains(data?.uid?.toString())) return
      resolve(entry, v, data)
    }
    entry.listener = listener
    player.on(Games.SUBMIT_EVENT, listener)

    armIdleTimer(entry)

    if (player.getProperty("ai") == 1) driveAI(entry)
  }

  // --- dropping ---

  // In-game drop: mark the player dropped (a terminal study-level status), drain pending asks, cancel
  // timers, disconnect edges. If that empties the cohort and the game isn't finished, abandon it;
  // otherwise let the current step complete if its queue drained.
  void drop(Object player) {
    if (player == null) return
    if (player._system != null) player._system.status = 'dropped'

    List affected = []
    boolean drained = false
    Closure doneC = null
    lock.lock()
    try {
      def pid = player.id
      // Whether the current step had outstanding asks before this drop; we only fire
      // done if this drop is what empties it (a genuine non-empty -> empty transition).
      boolean hadCurrent = (currentStepName != null) && pending.containsKey(currentStepName)
      pending.each { stepName, list ->
        def matches = list.findAll { it.player?.id == pid }
        list.removeAll(matches)
        affected.addAll(matches)
      }
      def emptyKeys = pending.findAll { k, v -> v.isEmpty() }.keySet().toList()
      emptyKeys.each { pending.remove(it) }
      if (hadCurrent && !pending.containsKey(currentStepName)) {
        def step = steps[currentStepName]
        doneC = (step != null) ? step.done : null
        drained = true
      }
    } finally { lock.unlock() }

    affected.each { e ->
      cancelEntryTimers(e)
      if (e.listener != null && e.player != null) {
        try { e.player.off(Games.SUBMIT_EVENT, e.listener) } catch (Exception ex) { /* ignore */ }
      }
    }
    unassignAllGroupChoices(player)
    try { GroupContext.g.removeEdges(player) } catch (Exception ex) { /* ignore */ }

    if (!finished && getPlayers().isEmpty()) {
      abandon()
    } else if (drained && doneC != null) {
      doneC()   // outside the lock: done may re-enter go/ask/finish
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
    notifyRecruitment(true)   // completed -> counts toward completedGames
    dispose()
  }

  // Cohort emptied before finishing.
  private void abandon() {
    boolean already
    lock.lock()
    try { already = finished; finished = true } finally { lock.unlock() }
    if (already) return
    if (onAbandonClosure != null) { try { onAbandonClosure() } catch (Exception e) { logErr("onAbandon", e) } }
    notifyRecruitment(false)  // abandoned -> frees the slot, NOT counted as completed
    dispose()
  }

  // Best-effort: report this game's end to the bound recruitment controller (if one is set).
  // `completed` true -> gameCompleted (counts); false -> gameAbandoned (frees the slot, no count).
  // Both are idempotent with WaitingRoom.groupCompleted, so wiring both can't double-count. Guarded
  // so a recruitment hiccup can never break finish/abandon.
  private void notifyRecruitment(boolean completed) {
    try {
      def rec = GroupContext.recruitment
      if (rec == null) return
      if (completed) rec.gameCompleted(this.id) else rec.gameAbandoned(this.id)
    } catch (Exception e) {
      logErr("recruitment notify", e)
    }
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

    pendingEntries.each { e ->
      cancelEntryTimers(e)
      if (e.listener != null && e.player != null) {
        try { e.player.off(Games.SUBMIT_EVENT, e.listener) } catch (Exception ex) { /* ignore */ }
      }
    }

    membersCopy.each { v ->
      if (v?.getProperty("ai") == 1) {
        try { GroupContext.a.ai.remove(v) } catch (Exception e) { /* ignore */ }
        try { GroupContext.g.removePlayer(v.id) } catch (Exception e) { /* ignore */ }
      }
      if (v?._system?.groupId == this.id) {
        // Detach from this (now-finished) game, but leave the study-level _system.status untouched:
        // ending a game does not end the participant's study lifecycle (drop/kick/complete do that).
        v._system.remove('groupId')
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

  // A player picked an option. The pending-list removal AND the "did this drain the current step?"
  // decision happen in ONE critical section, so the done closure is claimed atomically by whichever
  // call empties the step -- under concurrent submits of the last outstanding decisions, done fires
  // exactly once. The call is also idempotent: if this decision was already resolved or dropped (a
  // duplicate/stale submit, e.g. two bus threads racing the same click), `removed` is false and we
  // run nothing.
  private void resolve(Map entry, Object v, Object data) {
    String stepName = entry.stepName
    boolean removed = false
    boolean drained = false
    Closure doneC = null
    lock.lock()
    try {
      def list = pending[stepName]
      removed = (list != null) && list.remove(entry)
      if (removed && list.isEmpty()) {
        pending.remove(stepName)
        if (stepName == currentStepName) {
          def step = steps[stepName]
          doneC = (step != null) ? step.done : null
          drained = true
        }
      }
    } finally { lock.unlock() }

    if (!removed) return   // already resolved or dropped -- ignore the duplicate/stale submit

    cancelEntryTimers(entry)
    if (entry.listener != null) {
      try { v.off(Games.SUBMIT_EVENT, entry.listener) } catch (Exception ex) { /* ignore */ }
    }
    // Clear EVERY option button for this decision, not just the one that was chosen.
    entry.uids.each { unassignChoice(v, it) }

    // Run the chosen option's result (each option carries its own). The submit names the picked uid.
    def chosenUid = data?.uid?.toString()
    def button = entry.buttons.find { it.uid == chosenUid }
    if (button != null) {
      def ev = button.event
      if (ev != null) {
        try {
          def evName = (ev instanceof Map) ? ev.name : ev
          def evData = (ev instanceof Map && ev.data != null) ? ev.data : [:]
          if (evName != null) _actions.addEvent(evName.toString(), evData as Map)
        } catch (Exception e) { logErr("ask event", e) }
      }
      if (button.result instanceof Closure) {
        try { invokeResult(button.result, v, data) } catch (Exception e) { logErr("ask result", e) }
      }
    }

    if (drained && doneC != null) doneC()   // outside the lock: done may re-enter go/ask/finish
  }

  // Invoke an option's result closure, tolerating its declared arity: (), (player) or
  // (player, data). Lets experiments write { -> }, { v -> } or { v, data -> } as they prefer.
  private void invokeResult(Closure result, Object v, Object data) {
    switch (result.maximumNumberOfParameters) {
      case 0:  result(); break
      case 1:  result(v); break
      default: result(v, data); break
    }
  }

  // Auto-submit for an AI player after a short delay. The AI picks a uniformly-random option from
  // THIS decision (mirroring PlayerAI.defaultBehavior) and emits the same submit CustomEvent a real
  // client would. Each ask is one decision, so multiple asks to the same AI in a step each resolve
  // independently. The timer thread only emits the event.
  private void driveAI(Map entry) {
    def player = entry.player
    def buttons = entry.buttons
    if (buttons == null || buttons.isEmpty()) return
    def timer = new BBTimer()
    entry.aiTimer = timer
    // GDK Timer.runAfter has no error trap of its own; guard the body so a late fire during
    // teardown can't throw uncaught on the daemon timer thread.
    timer.runAfter(aiDelay as int) {
      try {
        def choice = buttons[_rng.nextInt(buttons.size())]
        GroupContext.events.emit("CustomEvent",
          [playerId: player.id, eventName: Games.SUBMIT_EVENT, data: [uid: choice.uid]],
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
        // The player may have resolved (cancelEntryTimers) while this SharedTimer was being
        // built. Decide keep-vs-cancel under the lock against the entry's cancelled flag, so a
        // late-armed drop timer can never escape cancellation and drop a player who already
        // responded.
        boolean keep
        lock.lock()
        try {
          if (entry.cancelled) { keep = false } else { entry.dropTimer = shared; keep = true }
        } finally { lock.unlock() }
        if (!keep) { try { shared.cancel() } catch (Exception e) { /* ignore */ } }
      } catch (Throwable t) {
        logErr("idle/warn timer", t)
      }
    }
  }

  // Mark the entry cancelled and tear down its timers. The drop SharedTimer is armed
  // asynchronously on the warn-timer thread (see armIdleTimer); flipping `cancelled` and
  // reading/clearing `dropTimer` under the lock closes the race where a drop timer armed
  // just after this call would otherwise survive and drop an already-resolved player.
  private void cancelEntryTimers(Map entry) {
    Object dropTimer
    lock.lock()
    try {
      entry.cancelled = true
      dropTimer = entry.dropTimer
      entry.dropTimer = null
    } finally { lock.unlock() }
    if (entry.warnTimer != null) { try { entry.warnTimer.cancel() } catch (Exception e) { /* ignore */ } ; entry.warnTimer = null }
    if (dropTimer != null)       { try { dropTimer.cancel() }       catch (Exception e) { /* ignore */ } }
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
  final double weight
  // Owned by the TreatmentManager: only read/written inside its lock.
  int completed = 0
  int inFlight = 0

  Treatment(String name, Object parameters, int target) {
    this(name, parameters, target, 1.0d)
  }

  Treatment(String name, Object parameters, int target, double weight) {
    this.name = name
    this.parameters = parameters
    this.target = target
    this.weight = weight
  }

  Object getParameters() { parameters }
  double getWeight() { weight }

  // Enough games are done or in progress that the target can be met -> not
  // eligible for a new assignment right now.
  boolean isFull() { (completed + inFlight) >= target }

  // The target number of completions has actually been reached.
  boolean isMet() { completed >= target }
}

// A named sampling strategy: a `type` label (persisted for debugging) plus the pure pick function
// (long seed, long cursor, List<Treatment> order) -> Treatment|null. Built-in factories
// (simpleRandom/roundRobin/weighted/randomBlock) return one of these; a custom strategy may instead
// be passed to TreatmentManager.distribution(...) as a bare closure (labelled 'custom').
class Sampler {
  final String type
  final Closure fn
  // Optional numeric parameter that defines the strategy (currently only the random-block multiplier).
  // Persisted alongside `type` so the strategy can be faithfully restored on reload; null otherwise.
  final Integer param
  Sampler(String type, Closure fn) { this(type, fn, null) }
  Sampler(String type, Closure fn, Integer param) { this.type = type; this.fn = fn; this.param = param }
  // Allows a Sampler to be invoked directly as a function too: sampler(seed, cursor, order).
  Treatment call(long s, long c, List ts) { return (Treatment) fn.call(s, c, ts) }
}

class TreatmentManager {
  static final int CURRENT_SCHEMA_VERSION = 1

  private final Map<String, Treatment> byName = new LinkedHashMap<>()
  private final List<Treatment> order = new ArrayList<>()
  private final Lock lock = new ReentrantLock(true)
  // Serializes file writes off the hot `lock`. Declared as the Lock interface per the Groovy 1.8.6
  // field-typing rule (a concrete concurrent type declared as its subclass is mishandled).
  private final Lock persistLock = new ReentrantLock()

  // Persistence target. null => persistence disabled => behaves exactly like the pre-persistence
  // TreatmentManager (no file is ever touched). See resolvePersistFile.
  private final File persistFile
  private final String key

  // Sampling state. `seed` + `cursor` are the ONLY persisted sampling state: every distribution is a
  // pure function pick(seed, cursor, order) -> Treatment, so resuming needs just these two numbers.
  // `cursor` is the number of assignments made and advances by one on each successful next().
  private long seed = 0L
  private boolean seedLoaded = false
  private long cursor = 0L

  // Monotonic snapshot sequence (guarded by `lock`): each snapshot taken in persist() is stamped with
  // the next value, and a write that loses the persistLock race to a higher-numbered snapshot is
  // dropped rather than clobbering newer state. lastWrittenSeq is guarded by persistLock.
  private long snapshotSeq = 0L
  private long lastWrittenSeq = 0L

  // Active sampling function (long seed, long cursor, List<Treatment> order) -> Treatment|null, plus
  // a human label persisted for debugging. Defaults to uniform-random (the historical behavior).
  // strategyParam carries the strategy's numeric parameter (random-block multiplier) so it round-trips.
  private Closure strategy
  private String strategyLabel = 'random'
  private Integer strategyParam = null

  // Set true on the first next(): no treatment may be defined and no distribution changed after
  // assignments begin (keeps the order / block math stable for round-robin and random-block).
  private boolean sealed = false

  // Accounting parsed from the file at construction, consumed by treatment(...) as it (re)defines each
  // name (only the persisted completed count is adopted; the code definition wins). Names still present
  // here at write time are dormant (in the file but not re-defined this run) and are preserved verbatim
  // so a temporarily-removed arm keeps its history.
  private final Map loadedTreatments = new LinkedHashMap()
  private String loadedDistType = null
  private Integer loadedDistParam = null
  // Treatment names in the order the persisted file listed them, captured at load so the first next()
  // can warn if the live definition order has since changed (which silently remaps a positional cursor).
  private final List loadedOrder = new ArrayList()

  // No key => no persistence => identical to the historical in-memory behavior.
  TreatmentManager() { this([:]) }

  TreatmentManager(String key) { this([key: key]) }

  // Recognized config keys: key (enables persistence + names the file), dir (storage dir override;
  // defaults to ./data/treatments), distribution (a sampler/closure), seed (explicit RNG seed).
  TreatmentManager(Map config) {
    if (config == null) config = [:]
    this.key = (config.key != null) ? config.key.toString() : null
    this.persistFile = resolvePersistFile(config)
    this.strategy = randomFn()
    this.strategyLabel = 'random'

    loadFromFile()

    if (config.seed != null) {
      this.seed = config.seed as long
    } else if (!seedLoaded) {
      this.seed = new Random().nextLong()
    }

    if (config.distribution != null) {
      distribution(config.distribution)
    } else if (loadedDistType != null) {
      // The caller did not re-supply a distribution, so restore the persisted one. Built-ins are
      // rebuilt from their label (+ param); a 'custom' closure is code and cannot be reconstructed, so
      // warn loudly and keep the random default rather than silently resuming a cursor under it.
      Closure restored = strategyForLabel(loadedDistType, loadedDistParam)
      if (restored != null) {
        this.strategy = restored
        this.strategyLabel = loadedDistType
        this.strategyParam = loadedDistParam
      } else {
        println "[TreatmentManager] persisted distribution '${loadedDistType}' cannot be auto-restored " +
          "(a custom strategy must be re-supplied via distribution:); falling back to random"
      }
    }
  }

  // Select the sampling method. Pass a built-in sampler (roundRobin(), randomBlock(2), weighted(),
  // simpleRandom()) or a custom closure (long seed, long cursor, List<Treatment> order) -> Treatment.
  // Configure once, before the first next().
  void distribution(Object arg) {
    Closure fn
    String label
    Integer param = null
    if (arg instanceof Sampler) {
      fn = ((Sampler) arg).fn
      label = ((Sampler) arg).type
      param = ((Sampler) arg).param
    } else if (arg instanceof Closure) {
      fn = (Closure) arg
      label = 'custom'
    } else {
      throw new IllegalArgumentException("distribution requires a sampler (e.g. roundRobin()) or a closure")
    }
    lock.lock()
    try {
      if (sealed) throw new IllegalStateException("cannot change distribution after the first assignment")
      if (loadedDistType != null && !loadedDistType.equals(label)) {
        println "[TreatmentManager] distribution changed (file='${loadedDistType}', now='${label}'); seed/cursor are method-agnostic and are reused"
      }
      this.strategy = fn
      this.strategyLabel = label
      this.strategyParam = param
    } finally { lock.unlock() }
  }

  // Define a treatment. `target` is the number of COMPLETED games desired (>= 1).
  Treatment treatment(String name, Object parameters, int target) {
    return treatment(name, parameters, target, 1.0d)
  }

  // Define a treatment with an explicit `weight` (used only by the weighted distribution; default
  // 1.0). The CODE definition is authoritative: on reload the script's target/weight/parameters always
  // win, and only the persisted PROGRESS (the completed count) is adopted so a run resumes where it
  // left off. Editing the script therefore changes the experiment as written -- e.g. lowering a target
  // stops recruitment sooner. Defining the same name twice in one run, or any treatment after the
  // first next(), throws.
  Treatment treatment(String name, Object parameters, int target, double weight) {
    if (name == null) throw new IllegalArgumentException("treatment requires a name")
    if (target < 1) throw new IllegalArgumentException("treatment '$name' target must be >= 1 (got $target)")
    if (weight <= 0.0d) throw new IllegalArgumentException("treatment '$name' weight must be > 0 (got $weight)")
    lock.lock()
    try {
      if (sealed) throw new IllegalStateException("cannot define treatment '$name' after the first assignment")
      if (byName.containsKey(name)) throw new IllegalStateException("treatment '$name' already defined")
      def prior = loadedTreatments.remove(name)
      // target/weight/parameters come from this (code) call -- the file never overrides them.
      Treatment t = new Treatment(name, parameters, target, weight)
      if (prior != null) {
        int pCompleted = prior.completed as int
        if (pCompleted < 0) pCompleted = 0
        if (pCompleted > target) {
          // The script lowered the target below the already-collected count; clamp so the arm is just
          // treated as met rather than yielding negative remaining slots.
          println "[TreatmentManager] treatment '$name' has $pCompleted completed in the file but the code " +
            "target is now $target; clamping completed to $target (the arm is already met)"
          pCompleted = target
        }
        t.completed = pCompleted
      }
      t.inFlight = 0
      byName[name] = t
      order << t
      return t
    } finally { lock.unlock() }
  }

  // Full named-map form: treatment(name: 'A', parameters: paramsA, target: 20, weight: 2.0)
  Treatment treatment(Map opts) {
    return treatment(opts?.name as String, opts?.parameters, asTarget(opts?.target), asWeight(opts?.weight))
  }

  // Mixed positional + trailing named args (Groovy gathers the named args into a
  // leading map): treatment('A', paramsA, target: 20, weight: 2.0)
  Treatment treatment(Map opts, String name, Object parameters) {
    return treatment(name, parameters, asTarget(opts?.target), asWeight(opts?.weight))
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

  // Reserve a slot and return the sampled eligible treatment, or null when every treatment is full.
  // The first call seals the manager (no more treatment/distribution changes). Sampling is delegated
  // to the active pure function; `cursor` advances by one per successful assignment.
  Treatment next() {
    Treatment chosen = null
    boolean firstSeal = false
    boolean advanced = false
    lock.lock()
    try {
      firstSeal = sealIfNeeded()
      if (firstSeal) warnIfOrderDriftsFromFile()
      boolean anyEligible = false
      for (Treatment t : order) { if (!t.isFull()) { anyEligible = true; break } }
      if (anyEligible) {
        chosen = (Treatment) strategy.call(seed, cursor, order)
        // A custom distribution closure can return a foreign or already-full treatment; the built-in
        // eligible-set pick used to make that impossible. Re-verify it is a live, eligible arm so we
        // never reserve a slot on (or over-recruit) something the strategy should not have returned.
        if (chosen != null && (byName[chosen.name] != chosen || chosen.isFull())) {
          println "[TreatmentManager] distribution returned an ineligible treatment '${chosen.name}'; skipping this assignment"
          chosen = null
        }
        if (chosen != null) {
          chosen.inFlight = chosen.inFlight + 1
          cursor = cursor + 1
          advanced = true
        }
      }
    } finally { lock.unlock() }
    if (advanced || firstSeal) persist()
    return chosen
  }

  // Like next(), but only treatments accepted by `filter` (a (Treatment) -> boolean closure) are
  // eligible; the active distribution then samples among those exactly as next() samples among all.
  // Lets the caller constrain the draw to treatments consistent with whatever it likes -- e.g. a
  // per-player value chosen at join time -- without the manager needing to understand parameters.
  // Returns null when no accepted treatment is still eligible. Seals on the first call, like next().
  // A filter that throws excludes that treatment (logged) rather than aborting the assignment.
  // Note: roundRobin/randomBlock balance over whatever list they receive, so a filtered draw will
  // not reproduce the unfiltered block/round-robin sequence -- prefer random/weighted when filtering.
  Treatment next(Closure filter) {
    if (filter == null) return next()
    Treatment chosen = null
    boolean firstSeal = false
    boolean advanced = false
    lock.lock()
    try {
      firstSeal = sealIfNeeded()
      if (firstSeal) warnIfOrderDriftsFromFile()
      // Restrict to the treatments the filter accepts, preserving definition order.
      List candidates = new ArrayList()
      for (Treatment t : order) {
        boolean ok = false
        try {
          ok = filter.call(t) ? true : false
        } catch (Exception e) {
          println "[TreatmentManager] next() filter threw for treatment '${t.name}'; excluding it: $e"
        }
        if (ok) candidates.add(t)
      }
      boolean anyEligible = false
      for (Treatment t : candidates) { if (!t.isFull()) { anyEligible = true; break } }
      if (anyEligible) {
        chosen = (Treatment) strategy.call(seed, cursor, candidates)
        // Re-verify the pick is a live, eligible, accepted arm before reserving a slot on it -- a
        // custom distribution could return a foreign/full treatment or one the filter excluded.
        if (chosen != null && (byName[chosen.name] != chosen || chosen.isFull() || !candidates.contains(chosen))) {
          println "[TreatmentManager] distribution returned an ineligible treatment '${chosen.name}'; skipping this assignment"
          chosen = null
        }
        if (chosen != null) {
          chosen.inFlight = chosen.inFlight + 1
          cursor = cursor + 1
          advanced = true
        }
      }
    } finally { lock.unlock() }
    if (advanced || firstSeal) persist()
    return chosen
  }

  // A game for this treatment finished: consume its reserved slot permanently, then persist.
  void complete(Treatment t) {
    if (t == null) return
    boolean changed = false
    lock.lock()
    try {
      def m = byName[t.name]
      if (m != null) {
        if (m.inFlight > 0) m.inFlight = m.inFlight - 1
        m.completed = m.completed + 1
        changed = true
      }
    } finally { lock.unlock() }
    if (changed) persist()
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
      order.each { s[it.name] = [target: it.target, completed: it.completed, inFlight: it.inFlight, weight: it.weight] }
      return s
    } finally { lock.unlock() }
  }

  private static int asTarget(Object raw) {
    if (raw == null) throw new IllegalArgumentException("treatment requires a 'target' (number of completed games)")
    return raw as int
  }

  private static double asWeight(Object raw) {
    if (raw == null) return 1.0d
    return raw as double
  }

  // --- internals: sealing + persistence ---

  // Must hold `lock`. Returns true iff it transitioned to sealed on THIS call.
  private boolean sealIfNeeded() {
    if (sealed) return false
    sealed = true
    return true
  }

  // Rebuild a built-in sampling function from its persisted label (+ param) so a reload restores the
  // original strategy even when the caller does not re-supply distribution(...). Returns null for
  // 'custom' (the closure is code and cannot be reconstructed) or an unrecognized label.
  private static Closure strategyForLabel(String label, Integer param) {
    if (label == null) return null
    if (label.equals('random')) return randomFn()
    if (label.equals('round-robin')) return roundRobinFn()
    if (label.equals('weighted')) return weightedFn()
    if (label.equals('random-block')) return randomBlockFn((param != null) ? param.intValue() : 1)
    return null
  }

  // Must hold `lock`. The positional cursor (round-robin / random-block) assumes `order` is rebuilt
  // identically across reloads; if the arms common to both the file and this run were reordered, the
  // resumed cursor maps to different arms. We cannot fix that without breaking positional resume, so
  // warn. Pure additions/removals are ignored -- only a true reordering of shared arms is flagged.
  private void warnIfOrderDriftsFromFile() {
    if (loadedOrder.isEmpty()) return
    if (!('round-robin'.equals(strategyLabel) || 'random-block'.equals(strategyLabel))) return
    List liveCommon = []
    for (Treatment t : order) { if (loadedOrder.contains(t.name)) liveCommon.add(t.name) }
    List fileCommon = []
    for (Object n : loadedOrder) { if (byName.containsKey(n)) fileCommon.add(n) }
    if (!liveCommon.equals(fileCommon)) {
      println "[TreatmentManager] treatment order changed since the persisted run (file=${fileCommon}, " +
        "now=${liveCommon}); the ${strategyLabel} cursor resumes by position and may now map to different arms"
    }
  }

  private File resolvePersistFile(Map config) {
    def k = config.key
    if (k == null || k.toString().trim().isEmpty()) return null
    String dir = (config.dir != null) ? config.dir.toString() : "./data/treatments"
    return new File(dir, sanitizeKey(k.toString()) + ".json")
  }

  private static String sanitizeKey(String k) {
    return k.replaceAll(/[^A-Za-z0-9._-]/, '_')
  }

  // Parse the persisted file (if any) into loadedTreatments + seed/cursor/loadedDistType. Best-effort:
  // a missing/empty/corrupt/newer file just means "start fresh" (logged).
  private void loadFromFile() {
    if (persistFile == null) return
    try {
      if (!persistFile.exists()) return
      String text = persistFile.getText('UTF-8')
      if (text == null || text.trim().isEmpty()) return
      def data = new JsonSlurper().parseText(text)
      if (!(data instanceof Map)) return
      int ver = (data.version != null) ? (data.version as int) : 0
      if (ver > CURRENT_SCHEMA_VERSION) {
        println "[TreatmentManager] persisted version $ver > supported $CURRENT_SCHEMA_VERSION; ignoring ${persistFile}"
        return
      }
      def dist = data.distribution
      if (dist instanceof Map) {
        loadedDistType = (dist.type != null) ? dist.type.toString() : null
        loadedDistParam = (dist.mult != null) ? (dist.mult as int) : null
        if (dist.seed != null) { this.seed = dist.seed as long; seedLoaded = true }
        if (dist.cursor != null) { this.cursor = dist.cursor as long }
      }
      def ts = data.treatments
      if (ts instanceof Map) {
        ts.each { tk, tv ->
          if (tv instanceof Map) {
            loadedTreatments[tk.toString()] = [
              target:    (tv.target != null) ? (tv.target as int) : 0,
              weight:    (tv.weight != null) ? (tv.weight as double) : 1.0d,
              completed: (tv.completed != null) ? (tv.completed as int) : 0
            ]
          }
        }
        loadedOrder.addAll(loadedTreatments.keySet())
      }
    } catch (Exception e) {
      println "[TreatmentManager] failed to read ${persistFile}: $e (starting fresh)"
      // Reset ALL state that may have been partially applied before the exception (e.g. seed/cursor
      // parsed from a valid distribution block before a corrupt treatments block threw) so "start
      // fresh" really means fresh, not a half-loaded cursor against wiped treatments.
      loadedTreatments.clear()
      loadedOrder.clear()
      loadedDistType = null
      loadedDistParam = null
      this.seed = 0L
      this.cursor = 0L
      this.seedLoaded = false
    }
  }

  // Snapshot under `lock`, then write under `persistLock` via temp-file + atomic rename. Best-effort:
  // an IO failure must never crash a game lifecycle callback (complete() runs inside onFinish).
  private void persist() {
    if (persistFile == null) return
    Map snapshot
    long seq
    lock.lock()
    try { seq = ++snapshotSeq; snapshot = buildSnapshot() } finally { lock.unlock() }
    persistLock.lock()
    try {
      // Snapshots are stamped under `lock`, so a higher seq reflects strictly newer (or equal) state.
      // If a newer snapshot already reached disk, skip this stale write instead of clobbering it.
      if (seq <= lastWrittenSeq) return
      File dir = persistFile.getAbsoluteFile().getParentFile()
      if (dir != null) Files.createDirectories(dir.toPath())
      String json = new JsonBuilder(snapshot).toPrettyString()
      File tmp = File.createTempFile(persistFile.getName() + ".", ".tmp", dir)
      Files.write(tmp.toPath(), json.getBytes("UTF-8"))
      try {
        Files.move(tmp.toPath(), persistFile.toPath(),
          StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
      } catch (Exception atomicEx) {
        Files.move(tmp.toPath(), persistFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
      }
      lastWrittenSeq = seq
    } catch (Exception e) {
      println "[TreatmentManager] failed to persist ${persistFile}: $e"
    } finally { persistLock.unlock() }
  }

  // Must hold `lock`. Dormant (loaded-but-not-redefined) entries first, then live treatments overlaid.
  private Map buildSnapshot() {
    def treatmentsOut = new LinkedHashMap()
    loadedTreatments.each { lk, lv ->
      treatmentsOut[lk] = [target: (lv.target as int), weight: (lv.weight as double), completed: (lv.completed as int)]
    }
    order.each { t ->
      treatmentsOut[t.name] = [target: t.target, weight: t.weight, completed: t.completed]
    }
    return [
      version:      CURRENT_SCHEMA_VERSION,
      key:          key,
      distribution: [type: strategyLabel, mult: strategyParam, seed: seed, cursor: cursor],
      treatments:   treatmentsOut,
      savedAt:      System.currentTimeMillis()
    ]
  }

  // --- built-in sampling functions: pure (long seed, long cursor, List<Treatment> order) -> Treatment ---

  static Closure randomFn() {
    return { long s, long c, List ts ->
      def a = ts.findAll { !it.isFull() }
      if (a.isEmpty()) return null
      return a[ new Random(TreatmentManager.mix(s, c)).nextInt(a.size()) ]
    }
  }

  static Closure roundRobinFn() {
    return { long s, long c, List ts ->
      int n = ts.size()
      if (n == 0) return null
      for (int i = 0; i < n; i++) {
        int idx = (int) Math.floorMod(c + i, (long) n)
        def t = ts[idx]
        if (!t.isFull()) return t
      }
      return null
    }
  }

  static Closure weightedFn() {
    return { long s, long c, List ts ->
      def a = ts.findAll { !it.isFull() }
      if (a.isEmpty()) return null
      double total = 0.0d
      for (t in a) { double w = t.weight; if (w > 0.0d) total += w }
      Random r = new Random(TreatmentManager.mix(s, c))
      if (total <= 0.0d) return a[ r.nextInt(a.size()) ]
      double draw = r.nextDouble() * total
      double acc = 0.0d
      for (t in a) {
        double w = t.weight
        if (w <= 0.0d) continue
        acc += w
        if (draw < acc) return t
      }
      return a[ a.size() - 1 ]
    }
  }

  static Closure randomBlockFn(int mult) {
    final int m = (mult < 1) ? 1 : mult
    return { long s, long c, List ts ->
      int n = ts.size()
      if (n == 0) return null
      int bs = n * m
      long blockId = Math.floorDiv(c, (long) bs)
      int pos = (int) Math.floorMod(c, (long) bs)
      List perm = TreatmentManager.shuffledIndices(bs, TreatmentManager.mix(s, blockId))
      for (int j = 0; j < bs; j++) {
        int idx = ((int) perm[(pos + j) % bs]) % n
        def t = ts[idx]
        if (!t.isFull()) return t
      }
      return null
    }
  }

  // Avalanche mix so consecutive (seed, cursor) draws produce well-separated RNG seeds. Uses the
  // well-known MMIX-LCG constants kept as POSITIVE decimal longs: this Groovy build's number parser
  // rejects hex literals with the sign bit set. The exact constants don't matter for correctness,
  // only that the mix is deterministic and well-distributed.
  static long mix(long seed, long k) {
    long z = seed + (k * 6364136223846793005L) + 1442695040888963407L
    z = (z ^ (z >>> 33)) * 6364136223846793005L
    z = (z ^ (z >>> 29)) * 1442695040888963407L
    return z ^ (z >>> 32)
  }

  // Deterministic Fisher-Yates permutation of [0, n) seeded by `seed`.
  static List shuffledIndices(int n, long seed) {
    List idx = new ArrayList(n)
    for (int i = 0; i < n; i++) idx.add(i)
    Random r = new Random(seed)
    for (int i = n - 1; i > 0; i--) {
      int j = r.nextInt(i + 1)
      def tmp = idx[i]; idx[i] = idx[j]; idx[j] = tmp
    }
    return idx
  }
}

// Named sampling functions for TreatmentManager.distribution(...). Each returns a Sampler wrapping a
// pure pick(long seed, long cursor, List<Treatment> order) -> Treatment. Call them: simpleRandom(),
// roundRobin(), randomBlock(2), weighted(). A custom strategy is any closure with that signature.
simpleRandom = { -> new Sampler('random',       TreatmentManager.randomFn()) }
roundRobin   = { -> new Sampler('round-robin',  TreatmentManager.roundRobinFn()) }
weighted     = { -> new Sampler('weighted',     TreatmentManager.weightedFn()) }
randomBlock  = { Integer mult = 1 -> int m = (mult == null) ? 1 : (int) mult; new Sampler('random-block', TreatmentManager.randomBlockFn(m), m) }

// Bind the engine handles from the script binding at load time (see GroupContext).
GroupContext.bind(g, a, events)
