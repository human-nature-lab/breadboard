package actors;

import models.IteratedBreadboardGraphChangedListener;

public class ClientUpdateActorProtocol {
  public static class ClientUpdate {
    public final IteratedBreadboardGraphChangedListener graphChangedListener;

    public ClientUpdate(IteratedBreadboardGraphChangedListener graphChangedListener) {
      this.graphChangedListener = graphChangedListener;
    }
  }
}

