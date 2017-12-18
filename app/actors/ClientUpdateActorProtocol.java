package actors;

import com.tinkerpop.blueprints.Graph;
import models.Client;
import models.IteratedBreadboardGraphChangedListener;

import java.util.HashMap;

public class ClientUpdateActorProtocol {
  public static class ClientUpdate {
    public final IteratedBreadboardGraphChangedListener graphChangedListener;

    public ClientUpdate(IteratedBreadboardGraphChangedListener graphChangedListener) {
      this.graphChangedListener = graphChangedListener;
    }
  }
}

