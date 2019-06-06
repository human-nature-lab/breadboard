package models;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class EventGraphChangedListener implements BreadboardGraphChangedListener {
  private Graph graph;
  private ArrayList<ClientListener> adminListeners = new ArrayList<>();
  private static HashMap<String, Client> clientListeners = new HashMap<>();

  public EventGraphChangedListener(Graph graph) {
    this.graph = graph;
  }

  public void addAdminListener(ClientListener adminListener) {
    adminListeners.add(adminListener);
  }

  public ArrayList<ClientListener> getAdminListeners() {
    return this.adminListeners;
  }

  @Override
  public void setGraph(Graph g) {
    this.graph = g;
  }

  public void addClientListener(Client clientListener) {
    clientListeners.put(clientListener.id, clientListener);
  }

  @Override
  public void edgeAdded(Edge edge) {
    //Logger.debug("EventGraphChangedListener edgeAdded");
    for (ClientListener al : adminListeners)
      al.edgeAdded(edge);

    clientEdgeChanged(edge);
  }

  @Override
  public void edgePropertyChanged(Edge edge, String key, Object oldValue, Object setValue) {
    for (ClientListener al : adminListeners)
      al.edgePropertyChanged(edge, key, setValue);

    clientEdgePropertyChanged(edge, key, setValue);
  }

  @Override
  public void edgePropertyRemoved(Edge edge, String key, Object removedValue) {
    for (ClientListener al : adminListeners)
      al.edgePropertyRemoved(edge, key);

    clientEdgePropertyChanged(edge, key, removedValue);
  }

  @Override
  public void edgeRemoved(Edge edge, Map<String, Object> props) {
    for (ClientListener al : adminListeners)
      al.edgeRemoved(edge);

    clientEdgeChanged(edge);
  }

  @Override
  public void vertexAdded(Vertex vertex) {
    for (ClientListener al : adminListeners)
      al.vertexAdded(vertex);

    clientVertexChanged(vertex, false);
  }

  @Override
  public void vertexPropertyChanged(Vertex vertex, String key, Object oldValue, Object setValue) {
    for (ClientListener al : adminListeners)
      al.vertexPropertyChanged(vertex, key, oldValue, setValue);

    boolean pvt = (key.equals("private") || key.equals("choices") || key.equals("text"));
    clientVertexChanged(vertex, pvt);
  }

  @Override
  public void vertexPropertyRemoved(Vertex vertex, String key, Object removedValue) {
    for (ClientListener al : adminListeners)
      al.vertexPropertyRemoved(vertex, key);

    boolean pvt = (key.equals("private") || key.equals("choices") || key.equals("text"));
    clientVertexChanged(vertex, pvt);
  }

  @Override
  public void vertexRemoved(Vertex vertex, Map<String, Object> props) {
    for (ClientListener al : adminListeners)
      al.vertexRemoved(vertex);

    clientVertexChanged(vertex, false);
  }

  private void clientEdgePropertyChanged(Edge edge, String key, Object value) {
    // inProps are only visible by the inVertex and outProps are only visible by the outVertex
    if (key.equals("inProps")) {
      Vertex inVertex = edge.getVertex(Direction.IN);
      String inId = (String) inVertex.getId();
      if (clientListeners.containsKey(inId)) {
        Client inClient = clientListeners.get(inId);
        inClient.updateGraph(inVertex);
      }
    } else if (key.equals("outProps")) {
      Vertex outVertex = edge.getVertex(Direction.OUT);
      String outId = (String) outVertex.getId();
      if (clientListeners.containsKey(outId)) {
        Client outClient = clientListeners.get(outId);
        outClient.updateGraph(outVertex);
      }
    } else {
      clientEdgeChanged(edge);
    }
  }

  private void clientEdgeChanged(Edge edge) {
    Vertex[] vertices = getVerticesByEdge(edge);

    String id1 = (String) vertices[0].getId();
    String id2 = (String) vertices[1].getId();

    if (clientListeners.containsKey(id1)) {
      Client c1 = clientListeners.get(id1);
      c1.updateGraph(vertices[0]);
    }

    if (clientListeners.containsKey(id2)) {
      Client c2 = clientListeners.get(id2);
      c2.updateGraph(vertices[1]);
    }
  }

  private void clientVertexChanged(Vertex vertex, boolean pvt) {
    // The vertex itself
    String id = (String) vertex.getId();
    if (clientListeners.containsKey(id)) {
      clientListeners.get(id).updateGraph(vertex);
    }
    // And all neighbors, if it isn't a private property
    if (!pvt) {
      for (Vertex v : vertex.getVertices(Direction.BOTH)) {
        id = (String) v.getId();
        if (clientListeners.containsKey(id)) {
          clientListeners.get(id).updateGraph(v);
        }
      }
    }
  }

  private Vertex[] getVerticesByEdge(Edge edge) {
    Vertex[] returnArray = new Vertex[2];
    returnArray[0] = edge.getVertex(Direction.IN);
    returnArray[1] = edge.getVertex(Direction.OUT);
    return returnArray;
  }
}
