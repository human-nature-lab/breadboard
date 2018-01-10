package controllers;

import com.fasterxml.jackson.databind.JsonNode;

import models.*;

import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Security;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
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
      // Add the user's default language
      experiment.languages.add(user.defaultLanguage);
    }

    experiment.name = newExperimentName;
    experiment.save();

    user.ownedExperiments.add(experiment);
    user.update();
    user.saveManyToManyAssociations("ownedExperiments");

    return ok(experiment.toJson());
  }

  @Security.Authenticated(Secured.class)
  public static Result importExperiment(){

    Http.MultipartFormData body = request().body().asMultipartFormData();
    Http.MultipartFormData.FilePart filePart = body.getFile("file");
    File zipFile = filePart.getFile();
    String experimentName = request().body().asJson().get("name").toString();

    if(experimentName == null || zipFile == null){
      return badRequest("Must include zipFile and experiment name");
    }

    // TODO: Save all the files to disk and then use the previously created logic to import them

    return ok("TODO: Unzip, validate and import the files");

  }

  @Security.Authenticated(Secured.class)
  public static Result exportExperiment(Long experimentId){

    Experiment experiment = Experiment.findById(experimentId);
    String uid = session().get("uid");
    User user = User.findByUID(uid);
    if(experiment == null){
      return badRequest("No experiment found with that ID");
    }

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    try(ZipOutputStream zos = new ZipOutputStream(outputStream)) {

      // Client style/html/graph
      ZipEntry e = new ZipEntry("style.css");
      zos.putNextEntry(e);
      zos.write(experiment.style.getBytes());
      zos.closeEntry();

      e = new ZipEntry("client.html");
      zos.putNextEntry(e);
      zos.write(experiment.clientHtml.getBytes());
      zos.closeEntry();

      e = new ZipEntry("client-graph.js");
      zos.putNextEntry(e);
      zos.write(experiment.clientGraph.getBytes());
      zos.closeEntry();

      // Steps
      for (Step step : experiment.steps) {
        e = new ZipEntry("Steps/" + step.name.concat(".groovy"));
        zos.putNextEntry(e);
        zos.write(step.source.getBytes());
        zos.closeEntry();
      }

      // Content in language subfolders
      for (Content c : experiment.content){
        for(Translation t : c.translations){
          String language = (t.language == null || t.language.code == null) ? user.defaultLanguage.getName() : t.language.code;
          e = new ZipEntry("Content/" + language + "/" + c.name.concat(".html"));
          zos.putNextEntry(e);
          zos.write(t.html.getBytes());
          zos.closeEntry();
        }
      }

      // Create the parameters.csv file
      e = new ZipEntry("parameters.csv");
      zos.putNextEntry(e);
      String ls = System.getProperty("line.separator");
      zos.write(("Name,Type,Min.,Max.,Default,Short Description" + ls).getBytes());
      for (Parameter param : experiment.parameters) {
        zos.write((param.name + "," + param.type + "," + param.minVal + "," + param.maxVal + "," + param.defaultVal + "," + param.description + ls).getBytes());
      }
      zos.closeEntry();

      // Write image files to stream
      for (Image image : experiment.images) {
        e = new ZipEntry("Images/" + image.fileName);
        zos.putNextEntry(e);
        zos.write(image.file);
        zos.closeEntry();
      }

      // Finish by closing the stream
      zos.close();

    } catch(IOException ioe) {
      ioe.printStackTrace();
    }

    return ok(outputStream.toByteArray());

  }
}
