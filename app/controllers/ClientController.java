package controllers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.FileUtils;
import play.*;
import play.libs.Json;
import play.mvc.*;
import views.html.*;
import com.fasterxml.jackson.databind.*;
import models.*;
import java.io.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

// TODO: Why does this always redirect to login?
//@Security.Authenticated(SecuredClient.class)
public class ClientController extends Controller 
{
    public static Result index(String experimentId, String experimentInstanceId, String clientId, String connectionSpeed) 
    {
        ExperimentInstance experimentInstance = null;
        Experiment experiment = null;
        
        try {
          experimentInstance = ExperimentInstance.findById(Long.valueOf(experimentInstanceId));
          experiment = Experiment.findById(Long.valueOf(experimentId));
        } catch (NumberFormatException ignored) {}

        if (experimentInstance == null || experiment == null || experimentInstance.status != ExperimentInstance.Status.RUNNING) {
          return ok( amtError.render() );
        }
        String assetsRoot = play.Play.application().configuration().getString("breadboard.assetsRoot", "/assets");
        if (assetsRoot != null) {
            return ok(client.render(assetsRoot));
        } else {
            return ok(client.render(""));
        }
    }

    public static Result getState(String experimentId, String experimentInstanceId, String clientId, String connectionSpeed){
        Map<String, String> vals = new HashMap();
        vals.put("Referer", "referer");
        vals.put("Connection", "connection");
        vals.put("Accept", "accept");
        vals.put("Cache-Control", "cacheControl");
        vals.put("Accept-Charset", "acceptCharset");
        vals.put("Cookie", "cookie");
        vals.put("Accept-Language", "acceptLanguage");
        vals.put("Accept-Encoding", "acceptEncoding");
        vals.put("User-Agent", "userAgent");
        vals.put("Host", "host");
        ObjectNode result = Json.newObject();
        Iterator it = vals.entrySet().iterator();
        while(it.hasNext()){
            Map.Entry pair = (Map.Entry)it.next();
            String header = request().getHeader(pair.getKey().toString());
            result.put((String) pair.getValue(), header);
            it.remove();
        }
        result.put("ipAddress", request().remoteAddress());
        result.put("requestURI", request().uri());
        result.put("clientId", clientId);
        result.put("experimentId", experimentId);
        result.put("experimentInstanceId", experimentInstanceId);
        result.put("clientGraph", getClientGraph(experimentId));
        result.put("clientHtml", getClientHtml(experimentId));
        result.put("connectSocket", routes.ClientController.connectClient(experimentId, experimentInstanceId, clientId).webSocketURL(request(), play.Play.application().configuration().getString("breadboard.wsUrl").contains("wss://")));
        return ok(result);
    }

    /**
     * Return the custom experiement graph if it exists
     * @return
     */
    public static String getClientGraph(String experimentId){
        Experiment experiment = null;
        try {
            experiment = Experiment.findById(Long.valueOf(experimentId));
        } catch (NumberFormatException ignored) {}

        if (experiment == null) {
            File file = play.Play.application().getFile("conf/defaults/client-graph.js");
            try {
                return FileUtils.readFileToString(file, "UTF-8");
            } catch(IOException ignored) {}
        }
        return experiment.getClientGraph();
    }

    /**
     * Return the custom experiment client html if it exists
     */
    public static String getClientHtml(String experimentId){
        Experiment experiment = null;
        try {
            experiment = Experiment.findById(Long.valueOf(experimentId));
        } catch (NumberFormatException ignored) {}

        if (experiment == null) {
            File file = play.Play.application().getFile("conf/defaults/default-client-html.html");
            try {
                return FileUtils.readFileToString(file, "UTF-8");
            } catch(IOException ignored) {}
        }
        return experiment.getClientHtml();
    }

    /**
    * Handle the client websocket.
    */
    public static WebSocket<JsonNode> connectClient(final String experimentId, final String experimentInstanceId, final String clientId) 
    {
        return new WebSocket<JsonNode>() 
        {
            // Called when the Websocket Handshake is done.
            public void onReady(final WebSocket.In<JsonNode> in, final WebSocket.Out<JsonNode> out)
            {
                Logger.debug("ClientController.connectClient.onReady");
                try
                {
                    ScriptBoard.addClient(experimentId, experimentInstanceId, clientId, in, new ThrottledWebSocketOut(out, Breadboard.WEBSOCKET_RATE));
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        };
    }
}
