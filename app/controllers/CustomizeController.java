package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Experiment;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;

public class CustomizeController extends Controller {

  @Security.Authenticated(Secured.class)
  public static Result getClientHtml(Long experimentId) {
    Experiment experiment = Experiment.findById(experimentId);
    if(experiment == null) {
      return badRequest("Invalid Experiment ID");
    }

    ObjectNode returnJson = Json.newObject();
    returnJson.put("clientHtml", experiment.getClientHtml());
    return ok(returnJson);
  }

  @Security.Authenticated(Secured.class)
  public static Result getClientGraph(Long experimentId) {
    Experiment experiment = Experiment.findById(experimentId);
    if(experiment == null) {
      return badRequest("Invalid Experiment ID");
    }

    ObjectNode returnJson = Json.newObject();
    returnJson.put("clientGraph", experiment.getClientGraph());
    return ok(returnJson);
  }

  @Security.Authenticated(Secured.class)
  public static Result getStyle(Long experimentId) {
    Experiment experiment = Experiment.findById(experimentId);
    if(experiment == null) {
      return badRequest("Invalid Experiment ID");
    }

    ObjectNode returnJson = Json.newObject();
    returnJson.put("style", experiment.getStyle());
    return ok(returnJson);
  }

  @Security.Authenticated(Secured.class)
  public static Result updateClientHtml(Long experimentId) {
    Experiment experiment = Experiment.findById(experimentId);

    if(experiment == null) {
      return badRequest("Invalid Experiment ID");
    }

    String clientHtml;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      clientHtml = json.findPath("clientHtml").textValue();
    }

    if (clientHtml == null) {
      return badRequest("Please provide client HTML.");
    }

    experiment.setClientHtml(clientHtml);
    experiment.save();

    return ok();
  }

  @Security.Authenticated(Secured.class)
  public static Result updateClientGraph(Long experimentId) {
    Experiment experiment = Experiment.findById(experimentId);
    if(experiment == null) {
      return badRequest("Invalid Experiment ID");
    }

    String clientGraph;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      clientGraph = json.findPath("clientGraph").textValue();
    }

    if (clientGraph == null) {
      return badRequest("Please provide client graph.");
    }

    experiment.setClientGraph(clientGraph);
    experiment.save();

    return ok();
  }

  @Security.Authenticated(Secured.class)
  public static Result updateStyle(Long experimentId) {
    Experiment experiment = Experiment.findById(experimentId);
    if(experiment == null) {
      return badRequest("Invalid Experiment ID");
    }

    String style;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      style = json.findPath("style").textValue();
    }

    if (style == null) {
      return badRequest("Please provide style.");
    }

    experiment.setStyle(style);
    experiment.save();

    return ok();
  }

}
