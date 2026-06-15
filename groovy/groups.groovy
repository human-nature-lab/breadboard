import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

// This is here to make the compiler happy
def doNothing = {}

// Static holder for Breadboard's script-scope globals (g, c, a,
// removePlayers). Bound once at startup so Groups / BaseGroupSteps
// don't need to take them at construction time.
class GroupRegistry {
  private static final Map<String, Object> byId = new ConcurrentHashMap<>()
  private static final Map<String, Object> contextMap = new ConcurrentHashMap<>()

  static void bindContext(Map ctx) {
    if (ctx == null) return
    ctx.each { k, v -> if (v != null) contextMap[k as String] = v }
  }

  static Object context(String key) { contextMap[key] }

  static void put(Object group) { byId[group.id as String] = group }
  static Object get(String id) { byId[id] }
  static Object forPlayer(Object v) {
    def gid = v?._system?.groupId
    gid == null ? null : byId[gid as String]
  }
  static void remove(String id) { byId.remove(id) }
  static Collection<Object> all() { byId.values() }
}

// Base class for group-scoped steps. Subclasses override run/done with
// whatever arg shape they want (Groovy dispatches dynamically).
abstract class BaseGroupStep {
  Object group      // Group instance
  String name
  Object a          // GroupActions for this group
  Object g          // global Graph
  Object c          // global currency (or whatever the script binds as 'c')

  void run(Object... args) {
    throw new UnsupportedOperationException("BaseGroupStep subclass must override run()")
  }

  void done(Object... args) {
    throw new UnsupportedOperationException("BaseGroupStep subclass must override done()")
  }

  // Start another step in the SAME group by name.
  void start(String stepName, Object... args) {
    group.start(stepName, args)
  }

  String getGroupId() { group?.id }
}

// Group-scoped emulation of Breadboard's `a` action queue.
//
// Mirrors the platform behaviour: adding an action assigns a choice to
// a player's node, the action stays "pending" until the player picks
// it, and when the pending queue for the current step empties, the
// step's `done` closure fires automatically.
//
// State lives per-group (not per-process), so groups don't share a
// queue and don't interfere with each other's `done` firing.
//
// Node assignment (the bit copied from how the platform attaches an
// action to a vertex) is centralised in `assignChoice` /
// `unassignChoice` / `attachChoiceListener` below — if the platform's
// vertex API differs from what's assumed there, those three methods
// are the only places to adjust.
class GroupActions {
  // Event the client sends on the eventbus when a player picks a
  // choice from this group's queue. Payload must include `uid` — the
  // uid of the action the player selected.
  static final String SUBMIT_EVENT = "group-action-submit"

  Object group
  String _currentStepName

  private final Lock lock = new ReentrantLock(true)
  // stepName -> ordered list of pending entries.
  // Each entry is a Map: [player, uid, stepName, handler].
  private final Map<String, List> pending = new LinkedHashMap<>()

  GroupActions(Object group) {
    this.group = group
  }

  // Add an action: assign the choice to the player's node, register a
  // handler for when they pick it, and track it in this group's
  // per-step pending queue. When the queue for the current step
  // empties, fires Group.done() (which fires currentStep.done()).
  //
  // `choice` should at minimum carry a `name`; a `uid` is generated if
  // missing so multiple actions on the same player can be told apart.
  void add(Object player, Map choice, Closure handler) {
    if (player == null) throw new IllegalArgumentException("add() requires a player")
    if (choice == null) choice = [:]
    if (choice.uid == null) choice.uid = UUID.randomUUID().toString()
    if (choice.name == null) choice.name = choice.uid

    String stepName = _currentStepName
    if (stepName == null) {
      throw new IllegalStateException(
        "GroupActions.add called before any step has been started in group ${group?.id}"
      )
    }

    def entry = [
      player:    player,
      uid: choice.uid,
      stepName:  stepName,
      handler:   handler,
    ]

    lock.lock()
    try {
      // NB: not Map.computeIfAbsent — Groovy 1.8.6 can't coerce a Closure to java.util.function.Function.
      def list = pending[stepName]
      if (list == null) {
        list = new ArrayList()
        pending[stepName] = list
      }
      list << entry
    } finally {
      lock.unlock()
    }

    assignChoice(player, choice)
    attachChoiceListener(player, entry)
  }

  // Convenience: action with a name + handler, no extra params.
  void add(Object player, String name, Closure handler) {
    add(player, [name: name], handler)
  }

  // Cancel anything queued for this player in this group. If this
  // empties the current step's queue, fires done.
  void remove(Object player) {
    if (player == null) return
    def pid = player.id
    List affected = []

    lock.lock()
    try {
      pending.each { stepName, list ->
        def matches = list.findAll { it.player?.id == pid }
        list.removeAll(matches)
        affected.addAll(matches)
      }
      // NB: not Collection.removeIf — Groovy 1.8.6 can't coerce a Closure to java.util.function.Predicate.
      def emptyKeys = pending.findAll { k, v -> v.isEmpty() }.keySet().toList()
      emptyKeys.each { pending.remove(it) }
    } finally {
      lock.unlock()
    }

    if (affected) {
      def uids = affected.collect { it.uid } as Set
      unassignChoices(player, uids)
    }

    maybeFireDoneIfCurrentStepEmpty()
  }

  // Cancel everything queued for a specific step in this group. Does
  // NOT fire done — the assumption is the step is exiting anyway.
  void removeStep(String stepName) {
    List affected = []
    lock.lock()
    try {
      def list = pending.remove(stepName)
      if (list) affected.addAll(list)
    } finally {
      lock.unlock()
    }
    affected.groupBy { it.player }.each { player, entries ->
      def uids = entries.collect { it.uid } as Set
      unassignChoices(player, uids)
    }
  }

  // Emit a log event. Delegates to global a.addEvent with groupId/step
  // merged in (without overwriting caller-supplied keys).
  void addEvent(String name, Map data = [:]) {
    def enriched = new LinkedHashMap<String, Object>()
    if (data != null) enriched.putAll(data)
    enriched.putIfAbsent('groupId', group?.id)
    enriched.putIfAbsent('step',    _currentStepName)
    globalA().addEvent(name, enriched)
  }

  void dropPlayers(List players, String completionCode, String message) {
    group.dropPlayers(players, completionCode, message)
  }

  // Number of pending actions in this group for the given step (or the
  // current step if not specified). Useful from tests / debugging.
  int pendingCount(String stepName = null) {
    String key = stepName ?: _currentStepName
    lock.lock()
    try {
      return (pending[key] ?: []).size()
    } finally {
      lock.unlock()
    }
  }

  // --- node-assignment internals ---
  //
  // These three methods are the only place that touches the
  // platform's "assign action to vertex" mechanism. If the actual
  // Breadboard API differs (different choice-list property, different
  // event name, etc.), adjust here.

  private void assignChoice(Object player, Map choice) {
    if (player.choices == null) player.choices = []
    // Tag the choice as group-scoped so the client routes the click to this group's
    // handler (the SUBMIT_EVENT custom event) instead of the platform's global
    // a.choose(uid), which knows nothing about per-group queues. Push a copy so the
    // caller's choice map isn't mutated.
    player.choices << (choice + [_route: 'group'])
  }

  private void unassignChoices(Object player, Set<String> uids) {
    if (player?.choices instanceof List) {
      player.choices = player.choices.findAll { !(it?.uid in uids) }
    }
  }

  private void attachChoiceListener(Object player, Map entry) {
    def uid = entry.uid
    player.on(SUBMIT_EVENT, { v, data ->
      if (data?.uid != uid) return  // not our action
      resolve(entry, v, data)
    })
  }

  // --- queue bookkeeping ---

  private void resolve(Map entry, Object v, Object data) {
    unassignChoices(v, [entry.uid] as Set)

    String stepName = entry.stepName
    lock.lock()
    try {
      def list = pending[stepName]
      if (list != null) {
        list.remove(entry)
        if (list.isEmpty()) pending.remove(stepName)
      }
    } finally {
      lock.unlock()
    }

    // Run the caller's handler outside the lock so it can re-enter
    // a.add / group.start without deadlocking.
    try {
      entry.handler(v, data)
    } catch (Exception e) {
      println "[GroupActions] handler threw: $e"
      e.printStackTrace()
    }

    maybeFireDoneIfCurrentStepEmpty()
  }

  private void maybeFireDoneIfCurrentStepEmpty() {
    String stepName
    boolean empty
    lock.lock()
    try {
      stepName = _currentStepName
      empty = stepName != null && !pending.containsKey(stepName)
    } finally {
      lock.unlock()
    }
    if (empty) {
      // Outside the lock — done() may re-enter the group.
      group.done()
    }
  }

  private Object globalA() {
    def a = GroupRegistry.context('a')
    if (a == null) {
      throw new IllegalStateException(
        "GroupRegistry has no 'a' bound — call GroupRegistry.bindContext(a: a, ...) at startup"
      )
    }
    return a
  }
}

class Group {
  String id
  Map<String, BaseGroupStep> steps = new LinkedHashMap<>()
  BaseGroupStep currentStep = null
  GroupActions a
  List activePlayers = new ArrayList()
  private final Lock lock = new ReentrantLock(true)

  Group(String id, List initialPlayers) {
    this.id = id
    this.a = new GroupActions(this)
    initialPlayers?.each { addPlayer(it) }
  }

  void register(String name, Class<? extends BaseGroupStep> stepClass) {
    lock.lock()
    try {
      if (steps.containsKey(name)) {
        throw new IllegalStateException("Step '$name' already registered for group $id")
      }
      def step = stepClass.newInstance()
      step.group = this
      step.name  = name
      step.a     = this.a
      step.g     = GroupRegistry.context('g')
      step.c     = GroupRegistry.context('c')
      steps[name] = step
    } finally {
      lock.unlock()
    }
  }

  void start(String name, Object... args) {
    BaseGroupStep step
    lock.lock()
    try {
      step = steps[name]
      if (step == null) {
        throw new IllegalArgumentException("No step '$name' in group $id")
      }
      currentStep = step
      a._currentStepName = name
    } finally {
      lock.unlock()
    }
    // Run outside the lock so step bodies that re-enter the group
    // (e.g. via this.start) don't deadlock.
    step.run(*args)
  }

  void done(Object... args) {
    BaseGroupStep step
    lock.lock()
    try { step = currentStep } finally { lock.unlock() }
    step?.done(*args)
  }

  void addPlayer(Object v) {
    lock.lock()
    try {
      if (v._system == null) v._system = [:]
      v._system.groupId = this.id
      if (!activePlayers.contains(v)) activePlayers.add(v)
    } finally {
      lock.unlock()
    }
  }

  void removePlayer(Object v) {
    lock.lock()
    try {
      activePlayers.remove(v)
      if (v?._system?.groupId == this.id) {
        v._system.remove('groupId')
      }
    } finally {
      lock.unlock()
    }
  }

  // Group-scoped drop. Filters players against activePlayers so a step
  // cannot accidentally drop someone from another group. Delegates the
  // actual removal work to the global `removePlayers` helper from
  // 0Globals.groovy (bound via GroupRegistry.bindContext).
  void dropPlayers(List players, String completionCode, String message) {
    def scoped
    lock.lock()
    try {
      scoped = players.findAll { activePlayers.contains(it) }
    } finally {
      lock.unlock()
    }
    if (scoped.isEmpty()) return
    def removePlayersFn = GroupRegistry.context('removePlayers')
    if (removePlayersFn != null) {
      removePlayersFn(scoped, completionCode, message)
    }
    scoped.each { removePlayer(it) }
  }

  // Clear per-player groupId tags and unregister.
  void dispose() {
    lock.lock()
    try {
      activePlayers.each { v ->
        if (v?._system?.groupId == this.id) v._system.remove('groupId')
      }
      activePlayers.clear()
      steps.clear()
      currentStep = null
    } finally {
      lock.unlock()
    }
    GroupRegistry.remove(this.id)
  }
}
