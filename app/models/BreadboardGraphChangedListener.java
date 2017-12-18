package models;

import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.util.wrappers.event.listener.GraphChangedListener;

public interface BreadboardGraphChangedListener extends GraphChangedListener {
  void setGraph(Graph g);
  void addAdminListener(ClientListener a);
  void addClientListener(Client c);
}
