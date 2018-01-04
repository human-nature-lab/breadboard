package models;

import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.util.wrappers.event.listener.GraphChangedListener;

import java.util.ArrayList;

public interface BreadboardGraphChangedListener extends GraphChangedListener {
  void setGraph(Graph g);
  void addAdminListener(ClientListener a);
  void addClientListener(Client c);
  ArrayList<ClientListener> getAdminListeners();
}
