import com.tinkerpop.blueprints.Direction
import com.tinkerpop.blueprints.Vertex
import com.tinkerpop.blueprints.util.wrappers.event.EventEdge
import com.tinkerpop.blueprints.util.wrappers.event.EventVertex
import com.tinkerpop.gremlin.groovy.Gremlin
import com.tinkerpop.pipes.Pipe
import java.text.DecimalFormat
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
 * Generate a random string from the supplied characters of the given length
 */
def randomString (String alphabet, int len) {
  return new Random().with {
    (1..len).collect { alphabet[ nextInt( alphabet.length() ) ] }.join()
  }
}

/**
 * Generate a random alphanumeric string of the given length
 */
def randomString (int len) {
  return randomString(alphaNumeric, len)
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