package actors;

import actors.ClientUpdateActorProtocol.ClientUpdate;
import akka.actor.UntypedActor;
import models.Client;
import models.ScriptLoader;
import play.Logger;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ClientUpdateActor extends UntypedActor {
  private static DateFormat dateFormat;

  public ClientUpdateActor() {
    dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  }

  @Override
  public void onReceive(Object message) {
    if (message instanceof ClientUpdate) {
      ClientUpdate clientUpdate = (ClientUpdate) message;

      clientUpdate.graphChangedListener.incrementUpdateIteration();
      Long updateIteration = clientUpdate.graphChangedListener.getUpdateIteration();
      if (updateIteration % 10 == 0) {
        Logger.debug(dateFormat.format(new Date()) + " - c.UpdateGraph:" + updateIteration);
      }

      for(Client c : clientUpdate.graphChangedListener.getClientListeners().values()) {
        //Logger.debug(dateFormat.format(new Date()) + " - Client update (" + c.id + ")");
        if (clientUpdate.graphChangedListener.getGraph().getVertex(c.id) != null) {
          try {
            c.updateGraph(clientUpdate.graphChangedListener.getGraph().getVertex(c.id));
          } catch (Exception e) {
            ScriptLoader.humanizeStackTrace(e);
            Logger.error("Failed to push client graph update for " + c.id, e);
          }
        } else {
          //Logger.debug(dateFormat.format(new Date()) + " - clientUpdate.graph.getVertex(c.id) == null");
          //Logger.debug(dateFormat.format(new Date()) + " - clientUpdate.graph = " + clientUpdate.graphChangedListener.getGraph());
          //Logger.debug(dateFormat.format(new Date()) + " - c.id = " + c.id);
        }
      }
    }
  }
}

