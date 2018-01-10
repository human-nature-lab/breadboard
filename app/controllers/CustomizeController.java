package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Experiment;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

public class CustomizeController extends Controller {

  public static Result getClientHtml(Long experimentId) {
    Experiment experiment = Experiment.findById(experimentId);
    if(experiment == null) {
      return badRequest("Invalid Experiment ID");
    }

    ObjectNode returnJson = Json.newObject();
    returnJson.put("clientHtml", experiment.clientHtml);
    return ok(returnJson);
  }

  public static Result getClientGraph(Long experimentId) {
    Experiment experiment = Experiment.findById(experimentId);
    if(experiment == null) {
      return badRequest("Invalid Experiment ID");
    }

    ObjectNode returnJson = Json.newObject();
    returnJson.put("clientGraph", experiment.clientGraph);
    return ok(returnJson);
  }

  public static Result getStyle(Long experimentId) {
    Experiment experiment = Experiment.findById(experimentId);
    if(experiment == null) {
      return badRequest("Invalid Experiment ID");
    }

    ObjectNode returnJson = Json.newObject();
    returnJson.put("style", experiment.style);
    return ok(returnJson);
  }

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
