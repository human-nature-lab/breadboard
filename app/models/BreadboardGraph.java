package models;

import com.tinkerpop.blueprints.*;
import com.tinkerpop.blueprints.impls.tg.TinkerGraph;

public class BreadboardGraph implements Graph {
  // The underlying graph structure, may be changed to Neo4J or other later on
  private TinkerGraph graph = new TinkerGraph();

  // Keeps track of the current revision number
  private Long revision = 0L;

  public Edge addEdge(Object id, Vertex outVertex, Vertex inVertex, String label) {
    return graph.addEdge(id, outVertex, inVertex, label);
  }

  public Vertex addVertex(Object id) {
    return graph.addVertex(id);
  }

  public Edge getEdge(Object id) {
    return graph.getEdge(id);
  }

  public Iterable<Edge> getEdges() {
    return graph.getEdges();
  }

  public Iterable<Edge> getEdges(String key, Object value) {
    return graph.getEdges(key, value);
  }

  public Features getFeatures() {
    return graph.getFeatures();
  }

  public Vertex getVertex(Object id) {
    return graph.getVertex(id);
  }

  public Iterable<Vertex> getVertices() {
    return graph.getVertices();
  }

  public Iterable<Vertex> getVertices(String key, Object value) {
    return graph.getVertices(key, value);
  }

  public void removeEdge(Edge edge) {
    graph.removeEdge(edge);
  }

  public void removeVertex(Vertex vertex) {
    graph.removeVertex(vertex);
  }

  public void shutdown() {
    graph.shutdown();
  }

  public GraphQuery query() {
    return graph.query();
  }
}
