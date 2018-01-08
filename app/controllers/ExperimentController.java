package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.Experiment;
import models.Step;
import models.User;
import play.mvc.Controller;
import play.mvc.Result;

public class ExperimentController extends Controller {

  public static Result createExperiment() {
    String newExperimentName;
    Long copyExperimentId;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      newExperimentName = json.findPath("newExperimentName").textValue();
      copyExperimentId = json.findPath("copyExperimentId").longValue();
    }

    if (newExperimentName == null) {
      return badRequest("Please provide a name for the new experiment.");
    }

    String uid = session().get("uid");
    User user = User.findByUID(uid);

    if (user == null) {
      return badRequest("Unable to determine the user who made this request.");
    }

    Experiment experiment = null;

    Experiment copyExperiment = Experiment.findById(copyExperimentId);

    if (copyExperiment != null) {
      experiment = new Experiment(copyExperiment);
    }

    if (experiment == null) {
      experiment = new Experiment();
      Step onJoin = Experiment.generateOnJoinStep();
      Step onLeave = Experiment.generateOnLeaveStep();
      Step init = Experiment.generateInitStep();
      experiment.steps.add(onJoin);
      experiment.steps.add(onLeave);
      experiment.steps.add(init);
      experiment.clientHtml = Experiment.defaultClientHTML();
      experiment.clientGraph = Experiment.defaultClientGraph();
    }

    experiment.name = newExperimentName;
    experiment.save();

    user.ownedExperiments.add(experiment);
    user.update();
    user.saveManyToManyAssociations("ownedExperiments");

    return ok(experiment.toJson());
  }
}