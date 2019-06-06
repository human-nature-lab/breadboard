package models;

import actors.ClientUpdateActor;
import actors.ClientUpdateActorProtocol;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;
import play.Logger;
import play.libs.Akka;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import akka.actor.*;
import scala.concurrent.duration.Duration;

public class IteratedBreadboardGraphChangedListener implements BreadboardGraphChangedListener {
  private Graph graph;
  private Long updateIteration = 0L;
  private ArrayList<ClientListener> adminListeners = new ArrayList<>();
  private HashMap<String, Client> clientListeners = new HashMap<>();

  static ActorRef clientUpdateActor;

  public IteratedBreadboardGraphChangedListener(Graph graph) {
    this.graph = graph;
    clientUpdateActor = Akka.system().actorOf(new Props(ClientUpdateActor.class));
    Long clientUpdateRate = play.Play.application().configuration().getMilliseconds("breadboard.clientUpdateRate");
    if (clientUpdateRate == null) {
      Logger.debug("clientUpdateRate = null");
      clientUpdateRate = 1000L;
    }
    Akka.system().scheduler().schedule(
        Duration.create(0, TimeUnit.MILLISECONDS),
        Duration.create(clientUpdateRate, TimeUnit.MILLISECONDS),
        clientUpdateActor,
        new ClientUpdateActorProtocol.ClientUpdate(this),
        Akka.system().dispatcher(),
        null
    );
  }

  private class ClientUpdateTask implements Runnable {
    @Override
    public void run() {
      updateIteration++;
      if (updateIteration % 10 == 0) {
        Logger.debug("Client update: " + updateIteration);
      }

      for(Client c : clientListeners.values()) {
        if (graph.getVertex(c.id) != null) {
          try {
            c.updateGraph(graph.getVertex(c.id));
          } catch (Exception e) {
            Logger.debug("Caught exception in ClientUpdateTask: " + e.getLocalizedMessage());
          }
        }
      }
    }
  }

  public HashMap<String, Client> getClientListeners() {
    return this.clientListeners;
  }

  public void incrementUpdateIteration() {
    this.updateIteration++;
  }

  public Long getUpdateIteration() {
    return this.updateIteration;
  }

  public Graph getGraph() {
    return this.graph;
  }

  public void setGraph(Graph g) {
    this.graph = g;
  }

  public void addAdminListener(ClientListener adminListener) {
    adminListeners.add(adminListener);
  }

  public ArrayList<ClientListener> getAdminListeners() {
    return this.adminListeners;
  }

  public void addClientListener(Client clientListener) {
    clientListeners.put(clientListener.id, clientListener);
  }

  @Override
  public void edgeAdded(Edge edge) {
    for (ClientListener al : adminListeners)
      al.edgeAdded(edge);
  }

  @Override
  public void edgePropertyChanged(Edge edge, String key, Object oldValue, Object setValue) {
    for (ClientListener al : adminListeners)
      al.edgePropertyChanged(edge, key, setValue);
  }

  @Override
  public void edgePropertyRemoved(Edge edge, String key, Object removedValue) {
    for (ClientListener al : adminListeners)
      al.edgePropertyRemoved(edge, key);
  }

  @Override
  public void edgeRemoved(Edge edge, Map<String, Object> props) {
    for (ClientListener al : adminListeners)
      al.edgeRemoved(edge);
  }

  @Override
  public void vertexAdded(Vertex vertex) {
    for (ClientListener al : adminListeners)
      al.vertexAdded(vertex);
  }

  @Override
  public void vertexPropertyChanged(Vertex vertex, String key, Object oldValue, Object setValue) {
    for (ClientListener al : adminListeners)
      al.vertexPropertyChanged(vertex, key, oldValue, setValue);
  }

  @Override
  public void vertexPropertyRemoved(Vertex vertex, String key, Object removedValue) {
    for (ClientListener al : adminListeners)
      al.vertexPropertyRemoved(vertex, key);
  }

  @Override
  public void vertexRemoved(Vertex vertex, Map<String, Object> props) {
    for (ClientListener al : adminListeners)
      al.vertexRemoved(vertex);
  }
}
