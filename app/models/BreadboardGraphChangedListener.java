package models;

import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.util.wrappers.event.listener.GraphChangedListener;

import java.util.ArrayList;

public interface BreadboardGraphChangedListener extends GraphChangedListener {
  void setGraph(Graph g);
  void addAdminListener(ClientListener a);
  void addClientListener(Client c);
  // Symmetric with addClientListener so callers (e.g. ScriptBoard.disconnectClients)
  // can drop a client from the dispatch registry when it disconnects / on reload,
  // instead of leaking it. No-op if the client was never registered.
  void removeClientListener(Client c);
  ArrayList<ClientListener> getAdminListeners();
}
