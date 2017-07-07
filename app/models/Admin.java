package models;

import akka.actor.ActorRef;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;
import controllers.D3Utils;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;

import java.util.Map;

public class Admin implements ClientListener {
  /*
   * This class represents a logged in administrator
   */
  private User user;
  private ActorRef scriptBoardController;
  private ThrottledWebSocketOut out;

  public Admin(User user, ActorRef scriptBoardController, ThrottledWebSocketOut out) {
    this.user = user;
    this.scriptBoardController = scriptBoardController;
    this.out = out;
  }

  public void graphChanged(Graph wholeGraph) {
    //Logger.debug("graphChanged");

    ObjectNode jsonOutput = Json.newObject();

    ObjectNode graph = D3Utils.graphToJsonString(wholeGraph);
    jsonOutput.put("graph", graph);

    out.write(jsonOutput);
  }

  public void vertexAdded(Vertex vertex) {
    vertexAdded(vertex, true);
  }

  public void vertexAdded(Vertex vertex, Boolean runOnJoin) {
    //Logger.debug("Admin.vertexAdded");
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
    //Logger.debug("vertexRemoved");
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
    //Logger.debug("vertexPropertyChanged");

    ObjectNode jsonOutput = Json.newObject();

		/*
    if (key.startsWith("private")) {
        	String privateKey = key.substring(7);
			jsonOutput = Json.newObject();
			jsonOutput.put("action", "nodePropertyChanged");
			jsonOutput.put("id", vertex.getId().toString());
			jsonOutput.put("key", privateKey);
			jsonOutput.put("value", Json.toJson(setValue));
		} else {
			jsonOutput.put("action", "nodePropertyChanged");
			jsonOutput.put("id", vertex.getId().toString());
			jsonOutput.put("key", key);
			jsonOutput.put("value", Json.toJson(setValue));
        }
        */
    if (key.equals("private")) {
      if (vertex.getProperty("private") instanceof Map) {
        //Logger.debug("vertex.getProperty(private) instanceof Map");
        Map newMap = (Map) vertex.getProperty("private");

				/*
				 * TODO: oldValue and setValue don't seem to be Maps, is there any way to only send the changed private variables?
				Map oldMap = new HashMap();
				if (oldValue instanceof Map) {
					Logger.debug("oldValue instanceof Map");
					oldMap = (Map) oldValue;
				}
				*/

        // Find the changed Property and write it out
        for (Object k : newMap.keySet()) {
          //Logger.debug("k.toString() = " + k.toString());
          jsonOutput = Json.newObject();
          jsonOutput.put("action", "nodePropertyChanged");
          jsonOutput.put("id", vertex.getId().toString());
          jsonOutput.put("key", k.toString());
          jsonOutput.put("value", Json.toJson(newMap.get(k)));
          out.write(jsonOutput);

					/*
					if ((! oldMap.containsKey(k)) || (! oldMap.get(k).equals(newMap.get(k)))) {
						Logger.debug("(! oldMap.containsKey(k)) || (! oldMap.get(k).equals(newMap.get(k)))");
						jsonOutput.put("action", "nodePropertyChanged");
						jsonOutput.put("id", vertex.getId().toString());
						jsonOutput.put("key", k.toString());
						jsonOutput.put("value", Json.toJson(newMap.get(k)));
					}
					*/
        }
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
    //Logger.debug("vertexPropertyRemoved");

    ObjectNode jsonOutput = Json.newObject();

    jsonOutput.put("action", "nodePropertyRemoved");
    jsonOutput.put("id", vertex.getId().toString());
    jsonOutput.put("key", key);

    out.write(jsonOutput);
  }

  public void edgeAdded(Edge edge) {
    //Logger.debug("edgeAdded");

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
    //Logger.debug("edgeRemoved");

    ObjectNode jsonOutput = Json.newObject();

    jsonOutput.put("action", "removeLink");
    jsonOutput.put("id", edge.getId().toString());
    jsonOutput.put("source", edge.getVertex(Direction.OUT).getId().toString());
    jsonOutput.put("target", edge.getVertex(Direction.IN).getId().toString());

    out.write(jsonOutput);
  }

  public void edgePropertyChanged(Edge edge, String key, Object setValue) {
    //Logger.debug("edgePropertyChanged");

    ObjectNode jsonOutput = Json.newObject();

    jsonOutput.put("action", "linkPropertyChanged");
    jsonOutput.put("id", edge.getId().toString());
    jsonOutput.put("key", key);
    jsonOutput.put("value", setValue.toString());

    out.write(jsonOutput);
  }

  public void edgePropertyRemoved(Edge edge, String key) {
    //Logger.debug("edgePropertyRemoved");

    ObjectNode jsonOutput = Json.newObject();

    jsonOutput.put("action", "linkPropertyRemoved");
    jsonOutput.put("id", edge.getId().toString());
    jsonOutput.put("key", key);

    out.write(jsonOutput);
  }

  public void setOut(ThrottledWebSocketOut out) {
    this.out = out;
  }
}
