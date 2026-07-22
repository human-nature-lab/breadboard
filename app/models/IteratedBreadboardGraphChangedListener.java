package models;

import actors.ClientUpdateActor;
import actors.ClientUpdateActorProtocol;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;
import play.Logger;
import play.libs.Akka;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import akka.actor.*;
import scala.concurrent.duration.Duration;

public class IteratedBreadboardGraphChangedListener implements BreadboardGraphChangedListener {
  private Graph graph;
  private Long updateIteration = 0L;
  // CopyOnWriteArrayList, not ArrayList: the vertex*/edge* event handlers below iterate this list on
  // whatever thread fired the graph change (the ScriptBoard actor, but also timer-callback threads),
  // while addAdminListener runs on a WebSocket connect thread. A plain ArrayList would throw
  // ConcurrentModificationException if an admin connected mid-dispatch. Iteration vastly outnumbers
  // mutation (admins connect rarely), so copy-on-write is the right trade-off.
  private final List<ClientListener> adminListeners = new CopyOnWriteArrayList<>();
  // ConcurrentHashMap, not HashMap: this map is iterated on the Akka scheduler thread
  // (ClientUpdateActor, once per clientUpdateRate) while WebSocket connect/disconnect
  // threads call addClientListener/removeClientListener. A plain HashMap threw
  // ConcurrentModificationException under concurrent-user churn; CHM's iterator is
  // weakly consistent and tolerates concurrent put/remove.
  private final Map<String, Client> clientListeners = new ConcurrentHashMap<>();

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
            ScriptLoader.humanizeStackTrace(e);
            Logger.error("Failed to push client graph update for " + c.id, e);
          }
        }
      }
    }
  }

  public Map<String, Client> getClientListeners() {
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

  public List<ClientListener> getAdminListeners() {
    return this.adminListeners;
  }

  public void addClientListener(Client clientListener) {
    clientListeners.put(clientListener.id, clientListener);
  }

  @Override
  public void removeClientListener(Client clientListener) {
    if (clientListener != null) {
      clientListeners.remove(clientListener.id);
    }
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
