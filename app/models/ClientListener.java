package models;

import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;

public interface ClientListener {
  public void graphChanged(Graph wholeGraph);

  public void vertexAdded(Vertex vertex);

  public void vertexRemoved(Vertex vertex);

  public void vertexPropertyChanged(Vertex vertex, String key, Object oldValue, Object setValue);

  public void vertexPropertyRemoved(Vertex vertex, String key);

  public void edgeAdded(Edge edge);

  public void edgeRemoved(Edge edge);

  public void edgePropertyChanged(Edge edge, String key, Object setValue);

  public void edgePropertyRemoved(Edge edge, String key);
}
