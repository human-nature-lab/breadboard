package controllers;

import models.*;
import org.codehaus.jackson.JsonNode;
import play.Logger;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;
import views.html.amtError;
import views.html.client;

// TODO: Why does this always redirect to login?
//@Security.Authenticated(SecuredClient.class)
public class ClientController extends Controller {
  public static Result index(String experimentId, String experimentInstanceId, String clientId, String connectionSpeed) {
    ExperimentInstance experimentInstance = null;
    Experiment experiment = null;

    try {
      experimentInstance = ExperimentInstance.findById(Long.valueOf(experimentInstanceId));
      experiment = Experiment.findById(Long.valueOf(experimentId));
    } catch (NumberFormatException nfe) {
    }

    if (experimentInstance == null || experiment == null || experimentInstance.status != ExperimentInstance.Status.RUNNING) {
      return ok(amtError.render());
    }

    return ok(client.render(experimentId, experimentInstanceId, clientId, connectionSpeed, experiment.clientHtml, experiment.clientGraph));
  }

  /**
   * Handle the client websocket.
   */
  public static WebSocket<JsonNode> connectClient(final String experimentId, final String experimentInstanceId, final String clientId) {
    return new WebSocket<JsonNode>() {
      // Called when the Websocket Handshake is done.
      public void onReady(final WebSocket.In<JsonNode> in, final WebSocket.Out<JsonNode> out) {
        Logger.debug("ClientController.connectClient.onReady");
        try {
          ScriptBoard.addClient(experimentId, experimentInstanceId, clientId, in, new ThrottledWebSocketOut(out, Breadboard.WEBSOCKET_RATE));
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
    };
  }
}
