package models;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;
import com.tinkerpop.blueprints.util.wrappers.event.EventGraph;
import controllers.D3Utils;
import play.Logger;
import play.Play;
import play.db.ebean.Model;
import play.libs.Json;
import play.mvc.WebSocket;

import java.util.HashSet;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class Client extends Model {
  public String id;
  public WebSocket.In<JsonNode> in;
  public ThrottledWebSocketOut out;
  public ExperimentInstance experimentInstance;
  //private int updates = 0;
  private long lastWrite = 0;
  private static final long DELAY = Play.application().configuration().getLong("breadboard.client.messageBuffer");
  //private static long delay = 250000000;
  private Timer delayTimer = new Timer();
  private boolean timerScheduled = false;

  public Client(String id, ExperimentInstance experimentInstance, WebSocket.In<JsonNode> in, ThrottledWebSocketOut out) {
    this.id = id;
    this.experimentInstance = experimentInstance;
    this.in = in;
    this.out = out;
  }

  private class DelayTimerTask extends TimerTask {
    private Vertex me;
    DelayTimerTask(Vertex me) {
      this.me = me;
    }
    public void run() {
      //Logger.info("DelayTimer.run()");
      timerScheduled = false;
      updateGraph(me);
    }
  }

  public synchronized void updateGraph(Vertex me) {

    //Logger.debug("updateGraph(" + me + ")");
    long curTime = System.nanoTime();
    //Logger.debug("curTime - lastWrite = " + (curTime - lastWrite));
    //Logger.debug("DELAY = " + DELAY);

    // If we already updated within the past delay nanoseconds, don't update again
    // Set a timer in case this is the last of a series of updates
    /*
    TODO: replacing this performance enhancement with an iterated update
    if ( (curTime - lastWrite < DELAY) || timerScheduled) {
      if (! timerScheduled) {
        //Logger.debug("Timer scheduled for: " + ((DELAY/1000000) + 1) + "ms");
        delayTimer.schedule(new DelayTimerTask(me), (DELAY/1000000) + 1);
        timerScheduled = true;
      }
      return;
    }
    */

    //Logger.debug("Update");
    // If we get here update the graph
    lastWrite = curTime;

    // Create an in-memory graph to store the sub-graph in
    TinkerGraph inMemoryGraph = new TinkerGraph();
    EventGraph subGraph = new EventGraph(inMemoryGraph);
    Vertex m = subGraph.addVertex(me.getId());

    // Private keys overwrite public properties so let's get private keys, if they exist
    HashSet<String> privateKeys = new HashSet<String>();
    if (me.getPropertyKeys().contains("private")) {
      if (me.getProperty("private") instanceof Map) {
        Map privateMap = (Map) me.getProperty("private");
        for (Object key : privateMap.keySet()) {
          privateKeys.add(key.toString());
          m.setProperty(key.toString(), privateMap.get(key));
        }
      }
    }

    // Add client vertex with all private properties
    for (String key : me.getPropertyKeys()) {
      if ((!key.equals("private")) && (!privateKeys.contains(key))) {
        if (me.getProperty(key) != null) {
          m.setProperty(key.toString(), me.getProperty(key));
        } else {
          Logger.debug("me.getProperty(key) == null");
        }
      }
    }

    // Get all neighbors and add them
    for (Vertex v : me.getVertices(Direction.BOTH)) {
      // If this vertex hasn't already been added
      if (subGraph.getVertex(v.getId()) == null) {
        Vertex neighbor = subGraph.addVertex(v.getId());

        for (String key : v.getPropertyKeys()) {
          // private, text, and choices are always private
          if ((!key.equals("private")) && (!key.equals("text")) && (!key.equals("choices"))) {
            neighbor.setProperty(key.toString(), v.getProperty(key));
          }
        }
      }
    }

    // Now edges
    // IN edges, handling inProps
    for (Edge e : me.getEdges(Direction.IN, "connected")) {

      Vertex outVertex = subGraph.getVertex(e.getVertex(Direction.OUT).getId());
      Vertex inVertex = subGraph.getVertex(e.getVertex(Direction.IN).getId());


      if (outVertex != null && inVertex != null) {
        Edge inE = subGraph.addEdge(e.getId(), outVertex, inVertex, "connected");
        HashSet<String> inPropKeys = new HashSet<String>();
        if (e.getPropertyKeys().contains("inProps")) {
          if (e.getProperty("inProps") instanceof Map) {
            Map inProps = (Map) e.getProperty("inProps");
            for (Object key : inProps.keySet()) {
              inPropKeys.add(key.toString());
              inE.setProperty(key.toString(), inProps.get(key));
            }
          }
        }

        for (String key : e.getPropertyKeys()) {
          if ((!key.equals("outProps")) && (!key.equals("inProps")) && (!inPropKeys.contains(key))) {
            inE.setProperty(key.toString(), e.getProperty(key));
          }
        }
      }
    }

    // OUT edges, handling outProps
    for (Edge e : me.getEdges(Direction.OUT, "connected")) {

      Vertex outVertex = subGraph.getVertex(e.getVertex(Direction.OUT).getId());
      Vertex inVertex = subGraph.getVertex(e.getVertex(Direction.IN).getId());


      if (outVertex != null && inVertex != null) {
        Edge outE = subGraph.addEdge(e.getId(), outVertex, inVertex, "connected");
        HashSet<String> outPropKeys = new HashSet<String>();
        if (e.getPropertyKeys().contains("outProps")) {
          if (e.getProperty("outProps") instanceof Map) {
            Map outProps = (Map) e.getProperty("outProps");
            for (Object key : outProps.keySet()) {
              outPropKeys.add(key.toString());
              outE.setProperty(key.toString(), outProps.get(key));
            }
          }
        }

        for (String key : e.getPropertyKeys()) {
          if ((!key.equals("outProps")) && (!key.equals("inProps")) && (!outPropKeys.contains(key))) {
            outE.setProperty(key.toString(), e.getProperty(key));
          }
        }
      }
    }

    /*
    long curTime = System.nanoTime();
    if (curTime - lastWrite > delay) {
      writeGraph(m, subGraph);
      lastWrite = curTime;
      delayTimer.purge();
      if (timerScheduled) {
        Logger.info("Timer cancelled");
        timerScheduled = false;
      }
    } else {
      if (!timerScheduled) {
        Logger.info("Timer scheduled");
        delayTimer.schedule(new DelayTimerTask(m, subGraph), delay/1000000);
        timerScheduled = true;
      }
    }
    */
    writeGraph(m, subGraph);
  }

  public void writeGraph(Vertex me, Graph graph) {
    //Logger.debug("writeGraph(" + me + "," + graph + ")");
    ObjectNode jsonOutput = Json.newObject();

    ObjectNode jsonGraph = D3Utils.graphToJsonString(graph);
    jsonOutput.put("graph", jsonGraph);

    // Write client vertex properties to "client" object
    ObjectNode client = Json.newObject();

    for (String key : me.getPropertyKeys()) {
      client.put(key, Json.toJson(me.getProperty(key)));
    }

    jsonOutput.put("player", client);

    //Logger.info("writeGraph: " + (updates++));
    out.write(jsonOutput);
  }

  public void graphChanged(Graph wholeGraph) {
    ObjectNode jsonOutput = Json.newObject();

    ObjectNode graph = D3Utils.graphToJsonString(wholeGraph);
    jsonOutput.put("graph", graph);

    out.write(jsonOutput);
  }

  public void vertexAdded(Vertex vertex) {
    ObjectNode jsonOutput = Json.newObject();

    jsonOutput.put("action", "addNode");
    jsonOutput.put("id", vertex.getId().toString());
    out.write(jsonOutput);

    for (String key : vertex.getPropertyKeys()) {
      vertexPropertyChanged(vertex, key, vertex.getProperty(key));
    }
  }

  public void vertexRemoved(Vertex vertex) {
    ObjectNode jsonOutput = Json.newObject();

    jsonOutput.put("action", "removeNode");
    jsonOutput.put("id", vertex.getId().toString());

    out.write(jsonOutput);
  }

  public void vertexPropertyChanged(Vertex vertex, String key, Object setValue) {
    ObjectNode jsonOutput = Json.newObject();

    // Only send "text" and "choices" properties to the player whose text or choices is changing
    if (key == "text") {
      if (vertex.getId().toString().equals(id)) {
        jsonOutput.put("text", Json.toJson(setValue));
        out.write(jsonOutput);
      }
    } else if (key == "choices") {
      if (vertex.getId().toString().equals(id)) {
        jsonOutput.put("choices", Json.toJson(setValue));
        out.write(jsonOutput);
      }
    } else if (key.startsWith("private")) {
      // private key should be sent, without the containing private key, to vertex player only
      if (vertex.getId().toString().equals(id)) {
        String privateKey = key.substring(7);
        jsonOutput.put("action", "nodePropertyChanged");
        jsonOutput.put("id", vertex.getId().toString());
        jsonOutput.put("key", privateKey);
        jsonOutput.put("value", Json.toJson(setValue));
        out.write(jsonOutput);
      }
    } else {
      jsonOutput.put("action", "nodePropertyChanged");
      jsonOutput.put("id", vertex.getId().toString());
      jsonOutput.put("key", key);
      jsonOutput.put("value", Json.toJson(setValue));
      out.write(jsonOutput);
    }
  }

  public void vertexPropertyRemoved(Vertex vertex, String key) {
    ObjectNode jsonOutput = Json.newObject();

    jsonOutput.put("action", "nodePropertyRemoved");
    jsonOutput.put("id", vertex.getId().toString());
    jsonOutput.put("key", key);

    out.write(jsonOutput);
  }

  public void edgeAdded(Edge edge) {
    ObjectNode jsonOutput = Json.newObject();

    jsonOutput.put("action", "addLink");
    jsonOutput.put("id", edge.getId().toString());
    jsonOutput.put("source", edge.getVertex(Direction.OUT).getId().toString());
    jsonOutput.put("target", edge.getVertex(Direction.IN).getId().toString());
    jsonOutput.put("value", edge.getLabel());

    out.write(jsonOutput);
  }

  public void edgeRemoved(Edge edge) {
    ObjectNode jsonOutput = Json.newObject();

    jsonOutput.put("action", "removeLink");
    jsonOutput.put("id", edge.getId().toString());
    jsonOutput.put("source", edge.getVertex(Direction.OUT).getId().toString());
    jsonOutput.put("target", edge.getVertex(Direction.IN).getId().toString());

    out.write(jsonOutput);
  }

  public void edgePropertyChanged(Edge edge, String key, Object setValue) {
    ObjectNode jsonOutput = Json.newObject();

    jsonOutput.put("action", "linkPropertyChanged");
    jsonOutput.put("id", edge.getId().toString());
    jsonOutput.put("key", key);
    jsonOutput.put("value", setValue.toString());

    out.write(jsonOutput);
  }

  public void edgePropertyRemoved(Edge edge, String key) {
    ObjectNode jsonOutput = Json.newObject();

    jsonOutput.put("action", "linkPropertyRemoved");
    jsonOutput.put("id", edge.getId().toString());
    jsonOutput.put("key", key);

    out.write(jsonOutput);
  }

  public ObjectNode toJson() {
    ObjectNode client = Json.newObject();
    client.put("style", experimentInstance.experiment.style);
    return client;
  }

  public void setIn(WebSocket.In<JsonNode> in) {
    this.in = in;
  }

  public void setOut(ThrottledWebSocketOut out) {
    this.out = out;
  }

  public String toString() {
    return "Client(" + id + ")";
  }

}
