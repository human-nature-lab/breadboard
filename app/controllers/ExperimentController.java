package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import models.Experiment;
import models.Step;
import models.User;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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


  public static Result importExperiment(){

    return ok("TODO: Unzip, validate and import the files");

  }

  public static Result exportExperiment(Long experimentId){

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try(ZipOutputStream zos = new ZipOutputStream(baos)) {

      ZipEntry entry = new ZipEntry("test.txt");
      zos.putNextEntry(entry);
      zos.write("These are the zipped file contents".getBytes());
      zos.closeEntry();
      zos.close();

    } catch(IOException ioe) {
      ioe.printStackTrace();
    }

    return ok(baos.toByteArray());

  }
}
