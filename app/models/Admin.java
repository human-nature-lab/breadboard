package models;

import akka.actor.ActorRef;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;
import controllers.D3Utils;
import play.libs.Json;

import java.util.Map;

public class Admin implements ClientListener {
  /*
   * This class represents a logged in administrator
   */
  private User user;
  private ActorRef scriptBoardController;
  private ThrottledWebSocketOut out;
  private static final Gson gson = new Gson();

  public Admin(User user, ActorRef scriptBoardController, ThrottledWebSocketOut out) {
    this.user = user;
    this.scriptBoardController = scriptBoardController;
    this.out = out;
  }

  public void graphChanged(Graph wholeGraph) {
    ObjectNode jsonOutput = Json.newObject();

    ObjectNode graph = D3Utils.graphToJsonString(wholeGraph);
    jsonOutput.put("graph", graph);

    out.write(jsonOutput);
  }

  public void vertexAdded(Vertex vertex) {
    vertexAdded(vertex, true);
  }

  public void vertexAdded(Vertex vertex, Boolean runOnJoin) {
    user.refresh();

    if (runOnJoin) {
      Breadboard.RunOnJoinStep onJoinStep = new Breadboard.RunOnJoinStep(user, vertex, out);
      scriptBoardController.tell(onJoinStep, null);
    }

    ObjectNode jsonOutput = Json.newObject();

    jsonOutput.put("action", "addNode");
    jsonOutput.put("id", vertex.getId().toString());

    out.write(jsonOutput);

    if (!runOnJoin) {
      for (String key : vertex.getPropertyKeys()) {
        vertexPropertyChanged(vertex, key, null, vertex.getProperty(key));
      }
    }
  }

  public void vertexRemoved(Vertex vertex) {
    vertexRemoved(vertex, true);
  }

  public void vertexRemoved(Vertex vertex, Boolean runOnLeave) {
    user.refresh();

    if (runOnLeave) {
      Breadboard.RunOnLeaveStep onLeaveStep = new Breadboard.RunOnLeaveStep(user, vertex, out);
      scriptBoardController.tell(onLeaveStep, null);
    }

    ObjectNode jsonOutput = Json.newObject();

    jsonOutput.put("action", "removeNode");
    jsonOutput.put("id", vertex.getId().toString());

    out.write(jsonOutput);
  }

  public void vertexPropertyChanged(Vertex vertex, String key, Object oldValue, Object setValue) {
    ObjectNode jsonOutput = Json.newObject();

    if (key.equals("private")) {
      if (vertex.getProperty("private") instanceof Map) {
        Map newMap = (Map) vertex.getProperty("private");

				/*
         * TODO: oldValue and setValue don't seem to be Maps, is there any way to only send the changed private variables?
				Map oldMap = new HashMap();
				if (oldValue instanceof Map) {
					oldMap = (Map) oldValue;
				}
				*/

        // Find the changed Property and write it out
        for (Object k : newMap.keySet()) {
          jsonOutput = Json.newObject();
          jsonOutput.put("action", "nodePropertyChanged");
          jsonOutput.put("id", vertex.getId().toString());
          jsonOutput.put("key", k.toString());
          jsonOutput.put("value", gson.toJson(newMap.get(k)));
          out.write(jsonOutput);
        }
      }
    } else {
      jsonOutput.put("action", "nodePropertyChanged");
      jsonOutput.put("id", vertex.getId().toString());
      jsonOutput.put("key", key);
      jsonOutput.put("value", gson.toJson(setValue));
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

    // If there are properties on the edge, notify the listener
    for (String key : edge.getPropertyKeys()) {
      edgePropertyChanged(edge, key, edge.getProperty(key));
    }
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

  public void setOut(ThrottledWebSocketOut out) {
    this.out = out;
  }

  public ThrottledWebSocketOut getOut() {
    return this.out;
  }
}
