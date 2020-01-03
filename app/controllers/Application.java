package controllers;

import models.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mindrot.jbcrypt.BCrypt;
import play.Logger;
import play.data.Form;
import play.libs.Json;
import play.mvc.*;
import views.html.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class Application extends Controller {

  public static Result addFirstUser() {

    if (User.findRowCount() > 0) {
      return badRequest("User table is not empty.");
    }

    String email, password;
    Long defaultLanguageId;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      email = json.findPath("email").textValue();
      password = json.findPath("password").textValue();
      defaultLanguageId = json.findPath("defaultLanguageId").longValue();
    }

    Language defaultLanguage = Language.findById(defaultLanguageId);

    if (email == null || password == null || defaultLanguage == null) {
      return badRequest("Please provide email, password, and default language.");
    }

    User user = new User();
    user.email = email;
    user.password = BCrypt.hashpw(password, BCrypt.gensalt());
    user.role = "admin";
    user.currentScript = "";
    user.defaultLanguage = defaultLanguage;
    user.experimentInstanceId = -1L;
    user.selectedExperiment = null;
    user.save();

    // TODO: Make method for creating demo experiments and associating with new user
    /*
    Experiment experiment = Experiment.findById(321l);
    if (experiment != null) {
      user.ownedExperiments.add(experiment);
      user.update();
      user.saveManyToManyAssociations("ownedExperiments");
    }
    */
    return ok();
  }


  public static Result authenticate() {
    Form<Login> loginForm = Form.form(Login.class).bindFromRequest();
    ObjectNode result = Json.newObject();
    if (loginForm.hasErrors()) {
      result.put("message", "Invalid username or password");
      result.put("status", "error");
      return badRequest(result);
    } else {
      String email = loginForm.get().email;

      session("email", email);

      String uid = UUID.randomUUID().toString();
      String juid = UUID.randomUUID().toString();
      session("uid", uid);
      session("juid", uid);

      User user = User.findByEmail(email);

      if (user != null) {
        Logger.debug("authenticate: uid = " + uid);
        user.uid = uid;
        user.update();
        result = Json.newObject();
        result.put("uid", uid);
        result.put("email", email);
        result.put("juid", juid);
        return ok(result);
      }

      return badRequest(login.render(loginForm));
    }
  }

  public static Result logout() {
    session().clear();
    return unauthorized();
  }

  public static Result index() {
      final File file = play.Play.application().getFile("assets/templates/breadboard.html");
      return ok(file, true);
  }

  @Security.Authenticated(Secured.class)
  public static Result getState() {
    ObjectNode result = Json.newObject();
    result.put("uid", session("uid")); // remove this
    result.put("juid", session("juid"));
    result.put("email", session("email"));
    result.put("connectSocket", play.Play.application().configuration().getString("breadboard.wsUrl"));
    return ok(result);
  }

  @Security.Authenticated(Secured.class)
  public static Result saveUserSettings() {
    Form<UserSettings> userSettingsForm = Form.form(UserSettings.class);
    userSettingsForm = userSettingsForm.bindFromRequest();

    ObjectNode result = Json.newObject();

    result.put("success", false);
    if (userSettingsForm.hasErrors()) {
      result.put("error", userSettingsForm.globalError().message());
      return ok(result);
    }

    UserSettings userSettings = userSettingsForm.get();

    userSettings.user.password = BCrypt.hashpw(userSettings.newPassword, BCrypt.gensalt());
    userSettings.user.update();
    result.put("success", true);

    return ok(result);
  }

  @Security.Authenticated(Secured.class)
  public static Result dataCsv(Long experimentId) {
    //TODO: Escape double quotes in ExperimentInstance parameters
    Experiment experiment = Experiment.findById(experimentId);
    final StringBuilder csv = new StringBuilder();
    Set<String> parameterNames = new TreeSet<>();
    for (ExperimentInstance instance : experiment.instances) {
      for (Data d : instance.data) {
        parameterNames.add(d.name);
      }
    }

    csv.append("\"id\",\"instance\",\"status\",\"created_at\",");

    // Add all parameter names
    for (String parameterName : parameterNames) {
      csv.append("\"" + parameterName + "\",");
    }

    // Delete trailing comma
    csv.deleteCharAt(csv.length() - 1);

    csv.append("\n");

    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss:SSS'Z'");

    List<ExperimentInstance> instances = experiment.instances;
    for (ExperimentInstance instance : instances) {
      csv.append("\"").append(instance.id).append("\",");
      csv.append("\"").append(instance.name).append("\",");
      csv.append("\"").append(instance.status).append("\",");
      csv.append("\"").append(simpleDateFormat.format(instance.creationDate)).append("\",");

      HashMap<String, String> dataMap = new HashMap<>();
      for (Data d : instance.data) {
        dataMap.put(d.name, d.value);
      }

      for (String parameterName : parameterNames) {
        if (dataMap.containsKey(parameterName)) {
          csv.append("\"").append(dataMap.get(parameterName)).append("\",");
        } else {
          csv.append(",");
        }
      }

      //Delete trailing comma
      csv.deleteCharAt(csv.length() - 1);
      csv.append("\n");
    }

    return ok(csv.toString());
  }

  @Security.Authenticated(Secured.class)
  public static Result eventCsv(Long experimentInstanceId) {
    ExperimentInstance ei = ExperimentInstance.findById(experimentInstanceId);

    final StringBuilder csv = new StringBuilder();
    csv.append("\"id\",\"event\",\"event_date\",\"data_name\",\"data_value\"\n");
    Collections.sort(ei.events, new Comparator<Event>() {
      @Override
      public int compare(Event o1, Event o2) {
        return o1.datetime.compareTo(o2.datetime);
      }
    });
    for (Event event : ei.events) {
      List<EventData> eventDatas = event.eventData;
      for (EventData eventData : eventDatas) {
        csv.append("\"").append(event.id).append("\",\"")
            .append(event.name).append("\",\"")
            .append(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss:SSS'Z'").format(event.datetime))
            .append("\",\"").append(eventData.name).append("\",")
            .append(eventData.valueToCSV()).append("\n");
      }
    }

    return ok(csv.toString());
  }

  /**
   * Handle the websocket.
   */
  public static WebSocket<JsonNode> connect() {
    WebSocket socket = new WebSocket<JsonNode>(){
      // Called when the Websocket Handshake is done.
      public void onReady(final WebSocket.In<JsonNode> in, final WebSocket.Out<JsonNode> out) {
        try {
          Breadboard.connect(in, out);
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
    };
    return socket;
  }

  public static class Login {
    public String email;
    public String password;

    public String validate() {
      Logger.debug("email: " + email);
      if (User.authenticate(email, password) == null) {
        return "Invalid user or password";
      }
      return null;
    }
  }

}
