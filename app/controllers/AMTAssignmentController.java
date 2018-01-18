package controllers;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;

public class AMTAssignmentController extends Controller {

  @Security.Authenticated(Secured.class)
  public static Result getAMTAssignments(Long experimentId, Boolean sandbox) {
    Experiment experiment = Experiment.findById(experimentId);

    if (experiment == null) {
      return badRequest("Experiment not found");
    }

    ObjectNode returnJson = Json.newObject();

    ArrayNode jsonAssignments = returnJson.putArray("assignments");

    for (ExperimentInstance instance : experiment.instances) {
      for (AMTHit hit : instance.amtHits) {
        if (hit.sandbox == sandbox) {
          for (AMTAssignment assignment : hit.amtAssignments) {
            jsonAssignments.add(assignment.toJson());
          }
        }
      }
    }
    return ok(returnJson);
  }
}
