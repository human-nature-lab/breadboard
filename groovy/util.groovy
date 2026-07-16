import com.tinkerpop.blueprints.Direction
import com.tinkerpop.blueprints.Vertex
import com.tinkerpop.blueprints.util.wrappers.event.EventEdge
import com.tinkerpop.blueprints.util.wrappers.event.EventVertex
import com.tinkerpop.gremlin.groovy.Gremlin
import com.tinkerpop.pipes.Pipe
import java.text.DecimalFormat
import groovy.transform.ToString
import static java.math.RoundingMode.UP

// This defines a 'neighbors' property of a vertex that returns the collection of connected vertices
/*
Usage:
g.V.each { ego->
  ego.neighbors.each { alter->
    println("""${ego.id} is connected with ${alter.id}""")
  }
}
*/
Gremlin.defineStep('neighbors', [Vertex, Pipe], { _().both('connected') })


EventEdge.metaClass.randV = {
  def rand = new Random()
  if (rand.nextDouble() < 0.5) {
    return delegate.getVertex(Direction.IN)
  } else {
    return delegate.getVertex(Direction.OUT)
  }
}

EventEdge.metaClass.private = { _privateTo, _prop ->
  if (!(_privateTo instanceof EventVertex && _prop instanceof LinkedHashMap)) {
    return
  }

  def privateTo = (EventVertex) _privateTo
  def prop = (LinkedHashMap) _prop

  def privateProps = inProps
  if (getVertex(Direction.OUT).equals(privateTo)) {
    privateProps = outProps
  }

  for (Object key : prop.keySet()) {
    if (key != null && prop.get(key) != null) {
      privateProps.put(key, prop.get(key))
    } else {
      println("key == null || prop.get(key) == null, hello.groovy, EventEdge.metaClass.private")
    }
  }
}

currency = new DecimalFormat('$0.00')
// round up to the nearest cent
currency.setRoundingMode(UP)

d = [:] as ObservableMap

final alphaNumeric = (('A'..'Z')+('0'..'9')).join()

/**
 * Generate a random alphanumeric string of the given length
 */
randomString = { int len ->
  return new Random().with {
    (1..len).collect { alphaNumeric[ nextInt( alphaNumeric.length() ) ] }.join()
  }
}

/**
 * Naive solution which shuffles the collection and takes the first N
 * items.
 */
def randomSubset (List vals, int n, Random random) {    
  def indices = (0..(vals.size() - 1)).toList()
  Collections.shuffle(indices, random)
  indices = indices.take(n)
  println "indices " + n + " " + indices.toString()
  return indices.collect{ vals[it] }
}
def randomSubset (List vals, int n) {
  return randomSubset(vals, n, new Random())
}


// Extend this for simple access to the content and action interfaces
public class BreadboardBase {}

BreadboardBase.metaClass.fetchContent = { Map opts ->
  // TODO: Handle locale property
  if (!("content" in opts || "contentKey" in opts)) {
    throw new Exception("Must supply either the 'content' or 'contentKey' property")
  }
  String content = opts.get("content") ?: c.get(opts.contentKey)
  if ("fills" in opts) {
    content = c.interpolate(content, *opts.fills)
  }
  return content
}

/**
 * Accessible alias for a.addEvent
 */
BreadboardBase.metaClass.addEvent = { String name, Map data ->
  a.addEvent(name, data)
}

/**
 * Accessible alias for a.add -- attach one or more action choices to a player in a single call
 */
BreadboardBase.metaClass.addChoices = { Vertex player, HashMap... choices ->
  a.add(player, *choices)
}

_ensureSystem = { Vertex v, String key ->
  // Use the real property/key accessors: hasProperty() on a vertex or a Map checks for a *bean
  // property*, not a graph property or a map key, so it always reported "missing" and clobbered
  // any existing _system[key] (e.g. wiping the source that register* set before complete* runs).
  if (v.getProperty('_system') == null) {
    v._system = [:] as ObservableMap
  }
  if (!v._system.containsKey(key)) {
    v._system[key] = [:] as ObservableMap
  }
}

BreadboardBase.metaClass._ensureSystem = _ensureSystem

// --- vertex lifecycle status (study-level) --------------------------------------------------------
// Every vertex carries a single study-level lifecycle flag at _system.status -- the one flag used
// throughout breadboard (group membership, the kick redirect, recruitment completion; the frontend
// reads _system.status directly):
//   'active'    -- still participating (the default, stamped on every vertex at creation)
//   'completed' -- finished the study successfully
//   'kicked'    -- removed by an admin
//   'dropped'   -- left / abandoned without completing
// 'active' is the only non-terminal value; the other three are terminal ("inactive" == not active).
STATUS_ACTIVE    = 'active'
STATUS_COMPLETED = 'completed'
STATUS_KICKED    = 'kicked'
STATUS_DROPPED   = 'dropped'

// Set a vertex's study-level status. Terminal statuses (completed/kicked/dropped) are sticky: once
// set, a vertex cannot move to a DIFFERENT status (re-asserting the same one is a no-op). Kept
// self-contained (no other script-binding references) so it also works when invoked as a
// BreadboardBase method (e.g. from the recruitment controller).
setVertexStatus = { Vertex v, String status ->
  if (v == null) return null
  def allStatuses = ['active', 'completed', 'kicked', 'dropped'] as Set
  def terminalStatuses = ['completed', 'kicked', 'dropped'] as Set
  if (!allStatuses.contains(status)) {
    throw new IllegalArgumentException("Unknown vertex status '${status}' (expected one of ${allStatuses})")
  }
  if (v.getProperty('_system') == null) v._system = [:]
  def current = v._system.status
  if (current != null && current != status && terminalStatuses.contains(current)) {
    println "[status] refusing to move vertex ${v.id} from terminal '${current}' to '${status}'"
    return current
  }
  v._system.status = status
  return status
}
BreadboardBase.metaClass.setVertexStatus = setVertexStatus

// Convenience wrappers + an "is this node still participating?" predicate (binding-scope helpers;
// class contexts should call setVertexStatus directly).
markActive     = { Vertex v -> setVertexStatus(v, 'active') }
markCompleted  = { Vertex v -> setVertexStatus(v, 'completed') }
markKicked     = { Vertex v -> setVertexStatus(v, 'kicked') }
markDropped    = { Vertex v -> setVertexStatus(v, 'dropped') }
isVertexActive = { Vertex v -> v?.getProperty('_system')?.status == 'active' }

// This tracks lots of information about the screen and sends it to the backend
def trackPlayerScreen = { Vertex v ->
  player.on("system-screen-tracker", { ev, data ->
    data.groupCondition = ev.private.condition
    data.groupId = ev.private.groupId
    data.playerId = ev.id
    a.addEvent("screen-tracker", data)
  })
}


@ToString(includeNames = true)
class FrontendConfigOpts {
  Boolean trackScreen
  Boolean prolific
  // Enables the client's force-submit redirect (useBreadboard's forceSubmit feature): once on, the
  // client watches v.immediatelySubmitCode and force-redirects to the Prolific submit URL when it is
  // set -- e.g. by recruitment.complete(..., kickAfter: N). Set this from the backend so an experiment
  // gets the post-completion kick without editing its client bundle.
  Boolean forceSubmit
}

configureFrontend = { Vertex v, Map opts ->
  FrontendConfigOpts config = opts as FrontendConfigOpts
  _ensureSystem(v, 'frontend')
  v._system.frontend.trackScreen = config.trackScreen
  v._system.frontend.prolific = config.prolific
  v._system.frontend.forceSubmit = config.forceSubmit
  if (config.trackScreen) {
    trackPlayerScreen(v)
  }
}

// Send the kick event to the specified players
kickPlayers = { ...playerIds ->
  playerIds.each { playerId ->
    def vertex = g.getVertex(playerId)
    vertex.send("kick")
    setVertexStatus(vertex, 'kicked')
  }
}