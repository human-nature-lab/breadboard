import com.tinkerpop.blueprints.Direction
import com.tinkerpop.blueprints.Edge
import com.tinkerpop.blueprints.Vertex
import com.tinkerpop.blueprints.impls.tg.TinkerGraph
import com.tinkerpop.blueprints.util.wrappers.event.EventEdge
import com.tinkerpop.blueprints.util.wrappers.event.EventGraph
import com.tinkerpop.blueprints.util.wrappers.event.listener.GraphChangedListener
import java.beans.PropertyChangeListener

class GroovyTimerTask extends TimerTask {
  Closure closure

  void run() {
    closure()
  }
}

class TimerMethods {
  static TimerTask runEvery(Timer timer, long delay, long period, Closure codeToRun) {
    TimerTask task = new GroovyTimerTask(closure: codeToRun)
    timer.schedule task, delay, period
    task
  }
}

class BreadboardGraph extends EventGraph<TinkerGraph> {
  //TODO: consider tracking number of edges and number of vertices to avoid iterating over graph to count number of edges or vertices

  def eventTracker

  public BreadboardGraph(baseGraph, eventTracker) {
    super(baseGraph)
    this.eventTracker = eventTracker

    //this is useful for pre-event before the client/admin listeners are triggered...
    //for example, it is possible causing the NPE when OnJoinStep is executed before the private map is added to the vertex
    //which can be avoid to initialized here as this is the first listener to be triggered
    this.addListener(new GraphChangedListener() {
      @Override
      void vertexAdded(Vertex vertex) {
        def v = BreadboardGraph.this.getVertex(vertex.id)
        def pvt = [:] as ObservableMap
        pvt.addPropertyChangeListener({ evt ->
          v.onVertexPropertyChanged(v, "private", evt.oldValue, evt.newValue)
        } as PropertyChangeListener)
        vertex.setProperty("private", pvt);
      }

      @Override
      void vertexPropertyChanged(Vertex v, String s, Object o, Object o1) {
        /* Look at this again for v2.3.1
        if (o1 instanceof List && (!(o1 instanceof ObservableList))) {
          def observableList = new ObservableList()
          observableList.addAll(o1)
          observableList.addPropertyChangeListener({ evt ->
            if (evt instanceof groovy.util.ObservableList.ElementEvent) {
              // Filter out property changes to the list (length changing, etc)
              v.onVertexPropertyChanged(v, s, evt.oldValue, observableList)
            }
          } as PropertyChangeListener)
          v.setProperty(s, observableList)
        }
        if (o1 instanceof Map && (!(o1 instanceof ObservableMap))) {
          def observableMap = new ObservableMap()
          observableMap.putAll(o1)
          observableMap.addPropertyChangeListener({ evt ->
            if (evt instanceof groovy.util.ObservableMap.PropertyEvent) {
              // Filter out property changes to the map (length changing, etc)
              v.onVertexPropertyChanged(v, s, evt.oldValue, observableMap)
            }
          } as PropertyChangeListener)
          v.setProperty(s, observableMap)

        */
      }

      @Override
      void vertexPropertyRemoved(Vertex vertex, String s, Object o) {}

      @Override
      void vertexRemoved(Vertex vertex, Map props) {}

      @Override
      void edgeAdded(Edge edge) {}

      @Override
      void edgePropertyChanged(Edge edge, String s, Object o, Object o1) {}

      @Override
      void edgePropertyRemoved(Edge edge, String s, Object o) {}

      @Override
      void edgeRemoved(Edge edge, Map props) {}
    })
  }

  /*
    timer usage examples:
    g.addTimer(time: 20,
    type: "currency",
    appearance: "success",
    timerText: "currency timer",
    currencyAmount: "500", direction: "down", result: { player-> player.color = r.nextInt(19) + 1 })
    g.addTimer(time: 30, type: "percent", appearance: "info", timerText: "percent timer", direction: "up", result: { player-> player.color = r.nextInt(19) + 1 })
    g.addTimer(time: 10,
    type: "time", appearance: "warning", timerText: "time timer", direction: "down", result: { player-> player.color = r.nextInt(19) + 1 })
    g.addTimer(time: 15, type: "time", appearance: "danger", timerText: "time timer", direction: "up", result: { player-> player.color = r.nextInt(19) + 1 })
   */

  def addTimer(Map params) {
    def time = params.time ?: null
    def name = params.name ?: UUID.randomUUID().toString()
    def timerText = params.timerText ?: ""
    def direction = params.direction ?: "down"
    def type = params.type ?: "time"
    def currencyAmount = params.currencyAmount ?: "0"
    def result = params.result ?: {}
    def player = params.player ?: null
    def appearance = params.appearance ?: ""

    if (time) {
      addTimer(time, name, timerText, direction, type, currencyAmount, result, player, appearance)
    }
  }

  def addTimer(Integer time,
               String name = UUID.randomUUID().toString(),
               String timerText = "",
               String direction = "down",
               String type = "time",
               String currencyAmount = "0",
               Closure result = {},
               Vertex player = null,
               String appearance = "") {
    def startTime = System.currentTimeMillis()
    def endTime = startTime + (time * 1000)

    if (player == null) {
      V.each { v ->
        addTimer(time, name, timerText, direction, type, currencyAmount, result, v, appearance)
      }
    } else {
      if (player.timers == null) {
        player.timers = [:]
      }

      /*
      // TODO: Adding a reference to the timer here, although useful, is causing an exception on JSON serialization:
      java.lang.IllegalArgumentException: No serializer found for class org.codehaus.groovy.runtime.DefaultGroovyMethods$3
      and no properties discovered to create BeanSerializer (to avoid exception, disable SerializationFeature.FAIL_ON_EMPTY_BEANS) )
      (through reference chain: java.util.LinkedHashMap["a12f7856-42aa-4911-9d69-93376ba7ed15"]->java.util.LinkedHashMap["timer"])
      player.timers[name] = ["startTime":startTime,
                             "endTime":endTime,
                             "timerType":type,
                             "timerText":timerText,
                             "direction":direction,
                             "currencyAmount":currencyAmount,
                             "appearance":appearance,
                             "timer":timer]
      */

      player.timers[name] = [
          type          : type,
          elapsed       : 0,
          duration      : time * 1000,
          timerText     : timerText,
          direction     : direction,
          currencyAmount: currencyAmount,
          appearance    : appearance,
          order         : player.timers.size()
      ]

      // Update the elapsed time for this timer
      def timerUpdateRate = 1000
      def tim = new Timer()
      tim.scheduleAtFixedRate({
        if (player.timers && player.timers.containsKey(name)) {
          player.timers[name].elapsed += timerUpdateRate
        }
        // Updating this property triggers an update
        // when breadboard is in event based update mode
        player.timerUpdatedAt = System.currentTimeMillis()
      } as GroovyTimerTask, 0, timerUpdateRate)
      tim.runAfter(time * 1000) {
        //println "Removing timer: " + name
        if (player.timers) {
          player.timers.remove(name)
        }
        if (result != null) {
          result(player)
        }
        tim.cancel()
      }
      return tim
    }
  }

  def getSubmitForm(player, dollars, reason = "completed", sandbox = false, comments = false) {
    def url = (sandbox) ? "https://workersandbox.mturk.com/mturk/externalSubmit" : "https://www.mturk.com/mturk/externalSubmit"
    def submitForm = "<form action=\"" + url + "\" method=\"get\">"
    if (comments) submitForm += "Comments:<br><textarea name=\"comments\" rows=\"5\" cols=\"50\"></textarea><br>"
    submitForm += "<button type=\"submit\">Submit HIT</button>"
    submitForm += "<input type=\"hidden\" name=\"assignmentId\" value=\"" + player.id + "\">"
    submitForm += "<input type=\"hidden\" name=\"bonus\" value=\"" + dollars + "\">"
    submitForm += "<input type=\"hidden\" name=\"reason\" value=\"" + reason + "\">"
    submitForm += "</form>"

    return submitForm
  }

  def addVertices(n) {
    int startId = 1;

    for (i in 0..n - 1) {
      while (hasVertex(startId)) {
        startId++;
      }
      addVertex(startId)
    }
  }

  def addPlayers(n) {
    addVertices(n)
  }

  public Edge addEdge(Vertex v1, Vertex v2, String label) {
    return addEdge(null, v1, v2, label)
  }

  public Edge addEdge(Vertex v1, Vertex v2) {
    return addEdge(null, v1, v2, "connected")
  }

  public Edge addEdge(Object id, Vertex v1, Vertex v2, String label) {
    Edge edge = super.addEdge(id, v1, v2, label)

    EventEdge eventEdge = new EventEdge(edge, this)

    def inProps = [:] as ObservableMap
    inProps.addPropertyChangeListener({ evt ->
      eventEdge.onEdgePropertyChanged(edge, "inProps", evt.oldValue, evt.newValue)
    } as PropertyChangeListener)
    eventEdge.setProperty("inProps", inProps);

    def outProps = [:] as ObservableMap
    outProps.addPropertyChangeListener({ evt ->
      eventEdge.onEdgePropertyChanged(edge, "outProps", evt.oldValue, evt.newValue)
    } as PropertyChangeListener)
    eventEdge.setProperty("outProps", outProps);

    return eventEdge
  }

  public Vertex addVertex(Object id) {
    //TODO probably can just remove this method
    return super.addVertex(id)
  }

  def addTrackedEdge(v1, v2, label) {
    def edge = addEdge(v1, v2, label)

    def data = [[name: "playerId1", value: v1.id.toString()], [name: "playerId2", value: v2.id.toString()]]
    if (eventTracker) {
      eventTracker.track("Connected", data)
    }

    return edge
  }

  // TODO: Is there a way to simplify this method so we don't have to pass in the PlayerActions object?
  def addAI(a, int n, behavior = null) {
    int startId = 1;

    if (n > 0) {
      for (i in 0..n - 1) {
        while (hasVertex("_" + startId))
          startId++;

        def v = addVertex("_" + startId)
        if (behavior == null)
          a.ai.add(v)
        else
          a.ai.add(v, behavior)
      }
    }
  }

  def addAIPlayer(a, String id, behavior = null) {
    //println("Adding AI Player: " + id)
    //println("Current players: " + getVertices())
    def v = addPlayer(id)
    if (behavior == null)
      a.ai.add(v)
    else
      a.ai.add(v, behavior)
  }

  def addPlayer(id) {
    if (!hasVertex(id))
      addVertex(id)
  }

  def removePlayer(id) {
    if (hasVertex(id)) {
      def v = getVertex(id)
      v.getEdges(Direction.BOTH, "connected").each { removeEdge(it) }
      removeVertex(v)
    }
  }

  def hasVertex(id) {
    if (getVertex(id) == null)
      return false

    return true
  }

  def ring(random = true) {

    List players = getVertices().iterator().toList()
    final int n = numVertices()

    if (n < 3) throw new IllegalArgumentException("Ring algorithm requires at least 3 players.")

    removeEdges()

    if (random) {
      Collections.shuffle(players)
    } else {
      Collections.sort(players, [compare: { a, b -> a.getId().toInteger() - b.getId().toInteger() }] as Comparator)
    }

    for (i in 0..(n - 1)) {
      def p = players.get(i)
      p.index = i
      addTrackedEdge(p, players.get((i + 1) % n), "connected")
    }
  }

  def geometricRandom(v) {
    removeEdges()
    List players = V.filter { it.active }.iterator().toList()
    int n = V.filter { it.active }.iterator().toList().size()

    Random rand = new Random()
    for (i in 0..(n - 1)) {
      def p = players.get(i)
      p.setProperty("posX", rand.nextDouble())
      p.setProperty("posY", rand.nextDouble())
      p.setProperty("even", (i % 2 == 0))
    }
    for (i in 0..(n - 2)) {
      def p1 = players.get(i)
      for (j in (i + 1)..(n - 1)) {
        def p2 = players.get(j)
        def d = (p1.posX - p2.posX)**2 + (p1.posY - p2.posY)**2
        if (d <= v**2) {
          addTrackedEdge(p1, p2, "connected")
        }
      }
    }
  }

  def wattsStrogatz(k, p) {
    // starting from a ring lattice with k edges per vertex we rewire each edge at random with probability p

    // First, create a ring lattice graph with k edges per node
    List players = getVertices().iterator().toList()
    final int n = players.size()
    if (n < (2 * k + 1)) throw new IllegalArgumentException("Watts-Strogatz algorithm requires at least 2k + 1 players.")
    removeEdges()

    Collections.shuffle(players)

    for (i in 0..(n - 1)) {
      for (j in 1..k) {
        addEdge(players.get(i), players.get((i + j) % n), "connected")
      }
    }

    // Then, iterate through the vertices and rewire each degree at probability p
    Random r = new Random()
    for (j in 1..k) {
      for (i in 0..(n - 1)) {
        if (r.nextDouble() < p) {
          def edge = getEdge(players.get(i), players.get((i + j) % n))
          removeEdge(edge)
          def newEdge = false
          while (!newEdge) {
            def randomPlayer = players.get(r.nextInt(n))
            if (getEdge(players.get(i), randomPlayer) == null) {
              addEdge(players.get(i), randomPlayer, "connected")
              newEdge = true
            }
          }
        }
      }
    }

    // Finally, record the final edges in the data
    E.each { edge ->
      def data = [[name: "playerId1", value: edge.getVertex(Direction.IN).id], [name: "playerId2", value: edge.getVertex(Direction.OUT).id]]
      if (eventTracker) {
        eventTracker.track("Connected", data)
      }
    }
  }

  def smallWorld(n) {
    ring()
    def edgesAdded = 0
    List players = getVertices().iterator().toList()
    Random r = new Random()
    while (edgesAdded < n) {
      def p1 = players.get(r.nextInt(players.size()))
      def p2 = players.get(r.nextInt(players.size()))
      if ((p1 != p2) && (!p1.both.retain([p2]).hasNext())) {
        addTrackedEdge(p1, p2, "connected")
        edgesAdded++
      }
    }
  }

  // This generates a ring network with n additional edges added so a 2-color coloring game is possible
  def smallWorldColoring(n) {
    ring()
    def edgesAdded = 0
    List players = getVertices().iterator().toList()
    Random r = new Random()
    while (edgesAdded < n) {
      def p1 = players.get(r.nextInt(players.size()))
      def p2 = players.get(r.nextInt(players.size()))
      if ((p1 != p2) && (!p1.both.retain([p2]).hasNext()) && (((p1.index - p2.index) % 2) == 1)) {
        addTrackedEdge(p1, p2, "connected")
        edgesAdded++
      }
    }
  }


  def empty() {
    removeEdges()
    removeVertices()
  }

  def ringLattice(int m, random = true) {
    mRing(m, random)
  }

  def mRing(int m, random = true) {
    List players = getVertices().iterator().toList()
    final int n = numVertices()

    if (n < (2 * m + 1)) throw new IllegalArgumentException("Ring lattice algorithm requires at least 2k + 1 players.")

    removeEdges()

    if (random) {
      Collections.shuffle(players)
    } else {
      Collections.sort(players, [compare: { a, b -> a.getId().toInteger() - b.getId().toInteger() }] as Comparator)
    }

    for (i in 0..(n - 1)) {
      for (j in 1..m)
        addTrackedEdge(players.get(i), players.get((i + j) % n), "connected")
    }
  }

  // For compatibility with experiments before this typo was corrected
  def barbasiAlbert(int v) {
    barabasiAlbert(v)
  }

  def barabasiAlbert(int v) {
    removeEdges()
    Random rand = new Random()

    def neighborList = [] //Target vertices for new edges.  The array size is always v.
    def inNetwork = [] //Array with each vertex added once per degree
    V.shuffle.eachWithIndex { player, i ->
      //If we've already added v players to the inNetwork array
      if (i >= v) {

        neighborList.each { neighbor ->
          addEdge(player, neighbor, "connected")
          inNetwork << neighbor
          inNetwork << player
        }

        //Now choose v unique vertices from the existing vertices
        //Pick uniformly from inNetwork (preferential attachment)
        neighborList = []
        def keyList = []
        while (neighborList.size() < v) {
          def key = rand.nextInt(inNetwork.size()) //Select the array key for inNetwork randomly
          if ((!keyList.contains(key)) && (!neighborList.contains(inNetwork.get(key)))) {
            //Avoid previously picked-up keys
            neighborList << inNetwork.get(key)
            keyList << key
          }
        }
      } else {
        player.even = rand.nextInt(v + 1)
        neighborList << player //Initial neighbors
      }
    }
  }

  def random(double connectivity) {
    removeEdges()
    Random r = new Random()
    List players = getVertices().iterator().toList()
    int n = numVertices()

    if (n < 2) {
      return;
    }

    for (i in 0..(n - 2)) {
      for (j in i + 1..(n - 1)) {
        def player = players.get(i)
        def neighbor = players.get(j)
        //def data = [[name: "playerId", value: player.id.toString()], [name: "neighborId", value: neighbor.id.toString()]]
        if (r.nextDouble() < connectivity) {
          addTrackedEdge(player, neighbor, "connected")
        }
      }
    }
  }

  def complete() {
    random(1.0)
  }

  def star(int index) {
    removeEdges()

    List players = getVertices().iterator().toList()

    def player = players.get(index)
    int n = numVertices()

    for (j in 0..(n - 1)) {
      def neighbor = players.get(j)
      if (!player.id.toString().equals(neighbor.id.toString())) {
        addTrackedEdge(player, neighbor, "connected")
      }
    }
  }

  def star() {
    Random r = new Random()
    star(r.nextInt(numVertices()))
  }

  def wheel() {
    List players = getVertices().iterator().toList()
    Collections.shuffle(players)
    final int n = numVertices()

    if (n < 5) throw new IllegalArgumentException("Wheel algorithm requires at least 5 players.")

    removeEdges()

    for (i in 0..(n - 2)) {
      addTrackedEdge(players.get(n - 1), players.get(i), "connected")
      addTrackedEdge(players.get(i), players.get((i + 1) % (n - 1)), "connected")
    }
  }

  def grid(int maxX) {
    removeEdges()

    List players = getVertices().iterator().toList()
    Collections.shuffle(players)
    int maxY = Math.floor(numVertices() / maxX)
    for (x in 0..(maxX - 1)) {
      for (y in 0..(maxY - 1)) {
        if ((x + 1) < maxX) {
          addTrackedEdge(players.get(x + (y * maxX)), players.get(x + (y * maxX) + 1), "connected")
        }

        if ((y + 1) < maxY) {
          addTrackedEdge(players.get(x + (y * maxX)), players.get(x + (maxX * (y + 1))), "connected")
        }
      }
    }
  }

  def grid() {
    // As close to a square as we can get
    grid((int) Math.floor(Math.sqrt(numVertices())))
  }

  def ladder() {
    grid(2)
  }

  def pairs() {
    removeEdges()

    List players = getVertices().iterator().toList()
    Collections.shuffle(players)
    final int n = numVertices()

    for (int i = 0; i < n; i += 2) {
      if (players.size() > (i + 1))
        addTrackedEdge(players.get(i), players.get(i + 1), "connected")
    }

  }

  def lattice(int maxX) {
    // Each player should have as close to 4 neighbors as possible
    List players = getVertices().iterator().toList()
    Collections.shuffle(players)
    int maxY = Math.floor(numVertices() / maxX)
    for (x in 0..(maxX - 1)) {
      for (y in 0..(maxY - 1)) {
        addTrackedEdge(players.get(x + (y * maxX)), players.get(((x + 1) % maxX) + (y * maxX)), "connected")
        addTrackedEdge(players.get(x + (y * maxX)), players.get(x + (((y + 1) % maxY) * maxX)), "connected")
      }
    }
  }

  def lattice() {
    // As close to a square as we can get
    lattice((int) Math.floor(Math.sqrt(numVertices())))
  }

  def removeEdges(Vertex v) {
    v.getEdges(Direction.BOTH).each { removeEdge(it) }
  }

  def removeEdges() {
    getEdges().each(
        {
          removeEdge(it)
        })
  }

  def removeVertices() {
    getVertices().each(
        {
          removeVertex(it)
        })
  }

  def getEdge(Vertex v1, Vertex v2) {
    def connectedEdges = v1.getEdges(Direction.BOTH, "connected")
    for (def connectedEdge : connectedEdges) {
      if (connectedEdge.getVertex(Direction.IN) == v2 || connectedEdge.getVertex(Direction.OUT) == v2) {
        return connectedEdge
      }
    }
  }

  def hasEdge(Vertex v1, Vertex v2) {
    def connectedEdges = v1.getEdges(Direction.BOTH, "connected")
    for (def connectedEdge : connectedEdges) {
      if (connectedEdge.getVertex(Direction.IN) == v2 || connectedEdge.getVertex(Direction.OUT) == v2) {
        return true
      }
    }
    return false
  }

  def removeConnectedEdge(Vertex v1, Vertex v2) {
    def connectedEdges = v1.getEdges(Direction.BOTH, "connected")
    for (def connectedEdge : connectedEdges) {
      if (connectedEdge.getVertex(Direction.IN) == v2 || connectedEdge.getVertex(Direction.OUT) == v2) {
        removeEdge(connectedEdge)
      }
    }
  }

  def numVertices() {
    return getVertices().iterator().size()
  }

  def numEdges() {
    def n = 0
    getEdges().each(
        {
          n++
        })
    return n
  }
}

g = new BreadboardGraph(new TinkerGraph(), eventTracker)
