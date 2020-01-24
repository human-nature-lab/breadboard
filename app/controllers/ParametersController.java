package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Parameter;
import models.Experiment;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;

public class ParametersController extends Controller {

  @Security.Authenticated(Secured.class)
  public static Result createParameter(Long experimentId) {

    Experiment experiment = Experiment.findById(experimentId);
    if (experiment == null) {
      return badRequest("Invalid experiment ID");
     }
    String name, type, minVal, maxVal, defaultVal, description;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      name = json.findPath("name").textValue();
      type = json.findPath("type").textValue();
      minVal = json.findPath("minVal").textValue();
      maxVal = json.findPath("maxVal").textValue();
      defaultVal = json.findPath("defaultVal").textValue();
      description = json.findPath("description").textValue();
    }

    Parameter parameter = new Parameter();
    parameter.name = name;
    parameter.type = type;
    parameter.minVal = (type.equals("Text") || type.equals("Boolean")) ? "" : minVal;
    parameter.maxVal = (type.equals("Text") || type.equals("Boolean")) ? "" : maxVal;
    parameter.defaultVal = defaultVal;
    parameter.description = description;

    experiment.parameters.add(parameter);
    experiment.save();

    ObjectNode returnJson = Json.newObject();
    returnJson.put("parameter", parameter.toJson());
    return ok(returnJson);
  }

  @Security.Authenticated(Secured.class)
  public static Result removeParameter(Long experimentId, Long parameterId) {
    Experiment experiment = Experiment.findById(experimentId);
    Parameter parameter = Parameter.find.byId(parameterId+"");
    if (parameter != null && experiment != null) {
      experiment.parameters.remove(parameter);
      experiment.update();
      parameter.delete();
    } else {
      return badRequest("Invalid experiment or parameter ID");
    }
    return ok();
  }
}
