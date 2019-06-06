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
