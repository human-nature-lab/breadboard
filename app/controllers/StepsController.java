package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Experiment;
import models.Step;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

public class StepsController extends Controller {

  public static Result getSteps(Long experimentId) {
    Experiment experiment = Experiment.findById(experimentId);
    if(experiment == null) {
      return badRequest("Invalid Experiment ID");
    }

    ObjectNode returnJson = Json.newObject();

    ArrayNode jsonSteps = returnJson.putArray("steps");
    for (Step s : experiment.steps) {
      jsonSteps.add(s.toJson());
    }

    return ok(returnJson);
  }

  public static Result deleteStep(Long stepId) {
    Step step = Step.findById(stepId);
    if (step == null) {
      return badRequest("Invalid Step ID");
    }
    step.delete();
    return ok();
  }

  public static Result updateStep(Long stepId) {
    Step step;
    Experiment experiment = null;

    Boolean isNewStep = (stepId == -1);
    if (isNewStep) {
      step = new Step();
    } else {
      step = Step.findById(stepId);
    }

    if (step == null) {
      return badRequest("Invalid Step ID");
    }

    String name;
    String source;
    Long experimentId;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      name = json.findPath("name").textValue();
      source = json.findPath("stepSource").textValue();
      experimentId = json.findValue("experimentId").asLong();
    }

    if (source == null) {
      return badRequest("Please provide step source.");
    }

    if (isNewStep) {
      experiment = Experiment.findById(experimentId);
    }

    if (isNewStep && experiment == null) {
      return badRequest("Invalid experiment ID.");
    }

    if (isNewStep && name == null) {
      return badRequest("Please provide step name.");
    }

    step.setSource(source);
    step.setName(name);
    if (isNewStep) {
      experiment.steps.add(step);
      experiment.save();
    } else {
      step.update();
    }

    return ok(step.toJson());
  }
}
