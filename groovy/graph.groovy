import com.tinkerpop.blueprints.Direction
import com.tinkerpop.blueprints.Edge
import com.tinkerpop.blueprints.Vertex
import com.tinkerpop.blueprints.impls.tg.TinkerGraph
import com.tinkerpop.blueprints.util.wrappers.event.EventEdge
import com.tinkerpop.blueprints.util.wrappers.event.EventGraph
import com.tinkerpop.blueprints.util.wrappers.event.listener.GraphChangedListener
import java.beans.PropertyChangeListener

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
        vertex.setProperty("_system", [:])
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
  def addTimer(Integer time,
               String name = UUID.randomUUID().toString(),
               String timerText = "",
               String direction = "down",
               String type = "time",
               String currencyAmount = "0",
               Closure result = {},
               Vertex player = null,
               String appearance = "") {
    return addTimer([
      time: time,
      name: name,
      timerText: timerText,
      direction: direction,
      type: type,
      currencyAmount: currencyAmount,
      result: result,
      player: player,
      appearance: appearance
    ])    
  }

  def addTimer(Map params) {
    def sharedTimer = new SharedTimer(params)
    if ("player" in params && params.player != null) {
      sharedTimer.addPlayer(params.player)
    } else {
      sharedTimer.addPlayers(this.V)
    }
    return sharedTimer
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
    def added = []
    for (i in 0..n - 1) {
      while (hasVertex(startId)) {
        startId++;
      }
      added << addVertex(startId)
    }
    return added
  }

  def addPlayers(n) {
    return addVertices(n)
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

  def addTrackedEdge(v1, v2, label, track = true) {
    def edge = addEdge(v1, v2, label)

    if (track) {
      def data = [[name: "playerId1", value: v1.id.toString()], [name: "playerId2", value: v2.id.toString()]]
      if (eventTracker) {
        eventTracker.track("Connected", data)
      }
    }

    return edge
  }

  // TODO: Is there a way to simplify this method so we don't have to pass in the PlayerActions object?
  def addAI(a, int n, behavior = null) {
    int startId = 1
    def added = []
    if (n > 0) {
      for (i in 0..n - 1) {
        while (hasVertex("_" + startId))
          startId++

        def v = addVertex("_" + startId)
        added << v
        if (behavior == null)
          a.ai.add(v)
        else
          a.ai.add(v, behavior)
      }
    }
    return added
  }

  def addAIPlayer(a, String id, behavior = null) {
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

  def defaultGraphOptions = [
      filter: { true },
      track: true,
      label: "connected",
      randomize: true,
      removeEdges: true,
      trackRemoveEdges: false
  ]

  def ring(Boolean random) {
    def options = defaultGraphOptions + [randomize: random]
    ring(options)
  }

  def ring(Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    List<Vertex> players = setupGraphAlgorithm(options)
    int n = players.size()

    if (n < 3) throw new IllegalArgumentException("Ring algorithm requires at least 3 players.")

    for (i in 0..(n - 1)) {
      def p = players.get(i)
      p.index = i
      addTrackedEdge(p, players.get((i + 1) % n), options.label, options.track)
    }
  }

  def geometricRandom(v, Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    List<Vertex> players = setupGraphAlgorithm(options)
    int n = players.size()

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
          addTrackedEdge(p1, p2, options.label, options.track)
        }
      }
    }
  }

  def wattsStrogatz(int k, double p, Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    List<Vertex> players = setupGraphAlgorithm(options)
    int n = players.size()

    // starting from a ring lattice with k edges per vertex we rewire each edge at random with probability p
    // First, create a ring lattice graph with k edges per node
    if (n < (2 * k + 1)) throw new IllegalArgumentException("Watts-Strogatz algorithm requires at least 2k + 1 players.")

    for (i in 0..(n - 1)) {
      for (j in 1..k) {
        Vertex p1 = players.get(i)
        Vertex p2 = players.get((i + j) % n)
        addEdge(p1, p2, options.label.toString())
      }
    }

    // Then, iterate through the vertices and rewire each degree at probability p
    Random r = new Random()
    for (j in 1..k) {
      for (i in 0..(n - 1)) {
        if (r.nextDouble() < p) {
          Vertex p1 = players.get(i)
          Vertex p2 = players.get((i + j) % n)
          def edge = getEdge(p1, p2)
          removeEdge(edge)
          def newEdge = false
          while (!newEdge) {
            def randomPlayer = players.get(r.nextInt(n))
            if (getEdge(players.get(i), randomPlayer) == null) {
              addEdge(players.get(i), randomPlayer, options.label.toString())
              newEdge = true
            }
          }
        }
      }
    }

    // Finally, record the final edges in the data
    if (options.track) {
      E.each { edge ->
        def data = [[name: "playerId1", value: edge.getVertex(Direction.IN).id], [name: "playerId2", value: edge.getVertex(Direction.OUT).id]]
        if (eventTracker) {
          eventTracker.track("Connected", data)
        }
      }
    }
  }

  def smallWorld(int k, Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    List players = setupGraphAlgorithm(options)
    int n = players.size()

    // Create a ring network
    for (i in 0..(n - 1)) {
      def p = players.get(i)
      addTrackedEdge(p, players.get((i + 1) % n), options.label, options.track)
    }

    // Add k edges at random
    def edgesAdded = 0
    Random r = new Random()
    while (edgesAdded < k) {
      def p1 = players.get(r.nextInt(players.size()))
      def p2 = players.get(r.nextInt(players.size()))
      if ((p1 != p2) && (!p1.both.retain([p2]).hasNext())) {
        addTrackedEdge(p1, p2, options.label, options.track)
        edgesAdded++
      }
    }
  }

  // This generates a ring network with n additional edges added so a 2-color coloring game is possible
  def smallWorldColoring(int k, Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    List players = setupGraphAlgorithm(options)
    int n = players.size()

    // Create a ring network
    for (i in 0..(n - 1)) {
      def p = players.get(i)
      p.index = i
      addTrackedEdge(p, players.get((i + 1) % n), options.label, options.track)
    }

    // Add k edges where the network is still 2-color solvable
    def edgesAdded = 0
    Random r = new Random()
    while (edgesAdded < k) {
      def p1 = players.get(r.nextInt(players.size()))
      def p2 = players.get(r.nextInt(players.size()))
      if ((p1 != p2) && (!p1.both.retain([p2]).hasNext()) && (((p1.index - p2.index) % 2) == 1)) {
        addTrackedEdge(p1, p2, options.label, options.track)
        edgesAdded++
      }
    }
  }


  def empty() {
    removeEdges()
    removeVertices()
  }

  def ringLattice(int m, Boolean random) {
    mRing(m, random)
  }

  def ringLattice(int m, Map opts = defaultGraphOptions) {
    mRing(m, opts)
  }

  def mRing(int m, Boolean random) {
    def options = defaultGraphOptions + [randomize: random]
    mRing(m, options)
  }

  def mRing(int m, Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    List players = setupGraphAlgorithm(options)
    int n = players.size()

    if (n < (2 * m + 1)) throw new IllegalArgumentException("Ring lattice algorithm requires at least 2k + 1 players.")

    for (i in 0..(n - 1)) {
      for (j in 1..m) {
        addTrackedEdge(players.get(i), players.get((i + j) % n), options.label, options.track)
      }
    }
  }

  // For compatibility with experiments before this typo was corrected
  def barbasiAlbert(int v, Map opts = defaultGraphOptions) {
    barabasiAlbert(v, opts)
  }

  def barabasiAlbert(int v, Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    List players = setupGraphAlgorithm(options)
    int n = players.size()

    Random rand = new Random()

    def neighborList = [] // Target vertices for new edges.  The array size is always v.
    def inNetwork = []    // Array with each vertex added once per degree

    for (int i = 0; i < n; i++) {
      def player = players.get(i)

      //If we've already added v players to the inNetwork array
      if (i >= v) {

        neighborList.each { neighbor ->
          addTrackedEdge(player, neighbor, options.label, options.track)
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

  def random(double connectivity, Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    List players = setupGraphAlgorithm(options)
    int n = players.size()

    Random r = new Random()

    if (n < 2) {
      return
    }

    for (i in 0..(n - 2)) {
      for (j in i + 1..(n - 1)) {
        def player = players.get(i)
        def neighbor = players.get(j)
        if (r.nextDouble() < connectivity) {
          addTrackedEdge(player, neighbor, options.label, options.track)
        }
      }
    }
  }

  def complete(Map opts = defaultGraphOptions) {
    random(1.0, opts)
  }

  def star (Vertex centerPlayer, Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    List players = setupGraphAlgorithm(options)
    int n = players.size()

    for (j in 0..(n - 1)) {
      def neighbor = players.get(j)
      if (!centerPlayer.id.toString().equals(neighbor.id.toString())) {
        addTrackedEdge(centerPlayer, neighbor, options.label, options.track)
      }
    }
  }

  def star(int index, Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    List players = V.filter(options.filter).iterator().toList()
    Vertex player = (Vertex) players.get(index)
    star(player, options)
  }

  def star(Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    int n = V.filter(options.filter).count()
    Random r = new Random()
    star(r.nextInt(n), options)
  }

  def wheel(Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    int n = V.filter(options.filter).count()
    if (n < 5) throw new IllegalArgumentException("Wheel algorithm requires at least 5 players.")
    List players = setupGraphAlgorithm(options)

    for (i in 0..(n - 2)) {
      addTrackedEdge(players.get(n - 1), players.get(i), options.label, options.track)
      addTrackedEdge(players.get(i), players.get((i + 1) % (n - 1)), options.label, options.track)
    }
  }

  def grid(int maxX, Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    List players = setupGraphAlgorithm(options)
    int n = players.size()

    int maxY = Math.floor(n / maxX)
    for (x in 0..(maxX - 1)) {
      for (y in 0..(maxY - 1)) {
        if ((x + 1) < maxX) {
          addTrackedEdge(players.get(x + (y * maxX)), players.get(x + (y * maxX) + 1), options.label, options.track)
        }

        if ((y + 1) < maxY) {
          addTrackedEdge(players.get(x + (y * maxX)), players.get(x + (maxX * (y + 1))), options.label, options.track)
        }
      }
    }
  }

  def grid(Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    int n = V.filter(options.filter).count()
    // As close to a square as we can get
    int maxX = (int) Math.floor(Math.sqrt(n))

    grid(maxX, options)
  }

  def ladder(Map opts = defaultGraphOptions) {
    grid(2, opts)
  }

  def pairs(Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    List players = setupGraphAlgorithm(options)
    final int n = players.size()

    List pairIds = []
    for (int i = 0; i < n; i += 2) {
      if (players.size() > (i + 1)) {
        def p1 = players.get(i)
        def p2 = players.get(i + 1)
        pairIds << [p1.id, p2.id]
        addTrackedEdge(p1, p2, options.label, options.track)
      }
    }

    return pairIds
  }

  def lattice(int maxX, Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    List players = setupGraphAlgorithm(options)

    int n = players.size()
    int maxY = Math.floor(n / maxX)
    for (x in 0..(maxX - 1)) {
      for (y in 0..(maxY - 1)) {
        addTrackedEdge(players.get(x + (y * maxX)), players.get(((x + 1) % maxX) + (y * maxX)), options.label, options.track)
        addTrackedEdge(players.get(x + (y * maxX)), players.get(x + (((y + 1) % maxY) * maxX)), options.label, options.track)
      }
    }
  }

  def lattice(Map opts = defaultGraphOptions) {
    def options = defaultGraphOptions + opts
    int n = V.filter(options.filter).count()
    // As close to a square as we can get
    int maxX = (int) Math.floor(Math.sqrt(n))
    lattice(maxX, options)
  }

  def setupGraphAlgorithm(Map opts) {
    def options = defaultGraphOptions + opts
    if (options.removeEdges) {
      removeEdges(options.trackRemoveEdges)
    }
    List players = V.filter(options.filter).iterator().toList()

    if (options.randomize) {
      Collections.shuffle(players)
    }

    return players
  }

  def removeEdges(Vertex v, track = false) {
    v.getEdges(Direction.BOTH).each { e->
      _removeEdge(e, track)
    }
  }

  def removeEdges(track = false) {
    getEdges().each({ e ->
      _removeEdge(e, track)
    })
  }

  def _removeEdge(Edge e, Boolean track) {
    if (track) {
      def data = [[name: "playerId1", value: e.getVertex(Direction.IN).id.toString()], [name: "playerId2", value: e.getVertex(Direction.OUT).id.toString()]]
      if (eventTracker) {
        eventTracker.track("Disconnected", data)
      }
    }
    removeEdge(e)
  }

  def removeVertices() {
    getVertices().each({
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
        _removeEdge(connectedEdge, false)
      }
    }
  }

  def addTrackedEdgeList (List vertices, List edges, String label) {
    for (def edge : edges) {
      addTrackedEdge(vertices[edge[0]], vertices[edge[1]], label)
    }
  }

  def addEdgeList (List vertices, List edges, String label) {
    for (def edge : edges) {
      addEdge(vertices[edge[0]], vertices[edge[1]], label)
    }
  }

  def numVertices() {
    return getVertices().count()
  }

  def numEdges() {
    return getEdges().count()
  }
}

g = new BreadboardGraph(new TinkerGraph(), eventTracker)
