package controllers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;
import play.libs.Json;
import java.util.ArrayList;
import java.util.HashMap;

public class D3Utils {
  public static ObjectNode graphToJsonString(Graph graph) {
    ArrayList<Vertex> vertices = new ArrayList<Vertex>();
    ArrayList<Edge> edges = new ArrayList<Edge>();

    for (Vertex v : graph.getVertices())
      vertices.add(v);

    for (Edge e : graph.getEdges())
      edges.add(e);

    return toJsonString(vertices, edges);
  }

  public static ObjectNode toJsonString(ArrayList<Vertex> vertices, ArrayList<Edge> edges) {
    ArrayList<HashMap<String, Object>> nodes = new ArrayList<HashMap<String, Object>>();

    for (int i = 0; i < vertices.size(); i++) {
      Vertex v = vertices.get(i);

      HashMap<String, Object> n = new HashMap<String, Object>();
      n.put("id", v.getId().toString());

      // TODO: Make this thread safe, the Vertex's properties may be modified during the execution of the loop
      // Will copying the property keys to an array solve the Concurrent Modification problem?
      String[] propertyKeyArray = v.getPropertyKeys().toArray(new String[0]);
      ArrayList<String> ignoreKeys = new ArrayList<String>();

      for (String key : propertyKeyArray) {
        if (ignoreKeys.indexOf(key) < 0) {
          n.put(key, Json.toJson(v.getProperty(key)));
        }
      }

      nodes.add(n);
    }

    ArrayList<HashMap<String, Object>> links = new ArrayList<HashMap<String, Object>>();

    for (Edge e : edges) {
      if (e.getLabel().equals("connected")) {
        HashMap<String, Object> l = new HashMap<String, Object>();
        l.put("id", Integer.parseInt(e.getId().toString()));
        l.put("name", e.getLabel());

        l.put("source", indexOfNode(e.getVertex(Direction.IN).getId().toString(), nodes));
        l.put("target", indexOfNode(e.getVertex(Direction.OUT).getId().toString(), nodes));

        for (String key : e.getPropertyKeys()) {
          l.put(key, e.getProperty(key));
        }

        if (l.get("source") == null || l.get("target") == null)
          System.out.println("source or target is null");
        else
          links.add(l);
      }
    }

    ObjectNode rootNode = Json.newObject();
    rootNode.put("nodes", Json.toJson(nodes));
    rootNode.put("links", Json.toJson(links));

    return rootNode;
  }

  private static int indexOfNode(String nodeId, ArrayList<HashMap<String, Object>> nodes) {
    for (int i = 0; i < nodes.size(); i++) {
      if (nodes.get(i).get("id").equals(nodeId)) {
        return i;
      }
    }
    return -1;
  }
}
