package models;

import com.tinkerpop.blueprints.Direction;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.util.wrappers.event.listener.GraphChangedListener;
import play.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class IteratedBreadboardGraphChangedListener implements GraphChangedListener {
  private Graph graph;
  private ArrayList<ClientListener> adminListeners = new ArrayList<ClientListener>();
  private static HashMap<String, Client> clientListeners = new HashMap<String, Client>();

  private static ScheduledExecutorService executor;

  public IteratedBreadboardGraphChangedListener(Graph graph) {
    this.graph = graph;
    this.executor = Executors.newSingleThreadScheduledExecutor();
  }

  public void addAdminListener(ClientListener adminListener) {
    adminListeners.add(adminListener);
  }

  public ArrayList<ClientListener> getAdminListeners() {
    return this.adminListeners;
  }

  public ArrayList<Client> getClientListeners() {
    ArrayList<Client> returnArrayList = new ArrayList<Client>();
    for (Client client : clientListeners.values()) {
      returnArrayList.add(client);
    }
    return returnArrayList;
  }

  public void addClientListener(Client clientListener) {
    clientListeners.put(clientListener.id, clientListener);
  }

  public void removeAdminListener(ClientListener adminListener) {
    adminListeners.remove(adminListener);
  }

  @Override
  public void edgeAdded(Edge edge) {
    //Logger.debug("BreadboardGraphChangedListener edgeAdded");
    for (ClientListener al : adminListeners)
      al.edgeAdded(edge);

    //clientEdgeChanged(edge);
  }

  @Override
  public void edgePropertyChanged(Edge edge, String key, Object oldValue, Object setValue) {
    for (ClientListener al : adminListeners)
      al.edgePropertyChanged(edge, key, setValue);

    //clientEdgePropertyChanged(edge, key, setValue);
  }

  @Override
  public void edgePropertyRemoved(Edge edge, String key, Object removedValue) {
    for (ClientListener al : adminListeners)
      al.edgePropertyRemoved(edge, key);

    //clientEdgePropertyChanged(edge, key, removedValue);
  }

  @Override
  public void edgeRemoved(Edge edge, Map<String, Object> props) {
    for (ClientListener al : adminListeners)
      al.edgeRemoved(edge);

    //clientEdgeChanged(edge);
  }

  @Override
  public void vertexAdded(Vertex vertex) {
    for (ClientListener al : adminListeners)
      al.vertexAdded(vertex);

    //clientVertexChanged(vertex, false);
  }

  @Override
  public void vertexPropertyChanged(Vertex vertex, String key, Object oldValue, Object setValue) {
    for (ClientListener al : adminListeners)
      al.vertexPropertyChanged(vertex, key, oldValue, setValue);

    //boolean pvt = (key.equals("private") || key.equals("choices") || key.equals("text"));
    //clientVertexChanged(vertex, pvt);
  }

  @Override
  public void vertexPropertyRemoved(Vertex vertex, String key, Object removedValue) {
    for (ClientListener al : adminListeners)
      al.vertexPropertyRemoved(vertex, key);

    //boolean pvt = (key.equals("private") || key.equals("choices") || key.equals("text"));
    //clientVertexChanged(vertex, pvt);
  }

  @Override
  public void vertexRemoved(Vertex vertex, Map<String, Object> props) {
    for (ClientListener al : adminListeners)
      al.vertexRemoved(vertex);

    //clientVertexChanged(vertex, false);
  }
}
