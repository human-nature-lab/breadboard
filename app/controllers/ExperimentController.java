package controllers;

import com.fasterxml.jackson.databind.JsonNode;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;

import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import play.Logger;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Security;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.apache.commons.io.FileUtils.deleteDirectory;

public class ExperimentController extends Controller {

  @Security.Authenticated(Secured.class)
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
      experiment = newExperiment(user, true);
    }

    experiment.name = newExperimentName;
    if (user.defaultLanguage != null) {
      experiment.languages.add(user.defaultLanguage);
    }
    experiment.save();

    user.ownedExperiments.add(experiment);
    user.update();
    user.saveManyToManyAssociations("ownedExperiments");

    return ok(experiment.toJson());
  }

  @Security.Authenticated(Secured.class)
  public static Result importExperiment(String experimentName) throws IOException{

    Http.MultipartFormData body = request().body().asMultipartFormData();
    Long maxUploadSize = play.Play.application().configuration().getLong("maxUploadSize", 50L * 1024L * 1024L);

    // Validate Content-Length header
    try {
      Long fileSize = Long.parseLong(request().getHeader("Content-Length"), 10);
      if (fileSize > maxUploadSize) {
        return badRequest("Uploaded file is too large");
      }
    } catch(Exception e){
      return badRequest("Upload was malformed");
    }

    // Validate the size of the file
    Http.MultipartFormData.FilePart filePart = body.getFile("file");
    File zippedFile = filePart.getFile();

    // Validate the other data
    if(experimentName == null || zippedFile == null){
      return badRequest("Must include zipFile and experiment name");
    }

    if(zippedFile.length() > maxUploadSize){
      return badRequest("Uploaded file is too large");
    }

    String uid = session().get("uid");
    User user = User.findByUID(uid);
    Experiment experiment = newExperiment(user, false);
    experiment.name = experimentName;
    experiment.save();

    String timeString = new Date().getTime() + "";
    String rootOutputFolder = "experiments/" + experiment.name + "_" + experiment.id + "_" + timeString;
    String outputFolder = rootOutputFolder;

    try {
      ZipFile zipFile = new ZipFile(zippedFile);
      zipFile.extractAll(outputFolder);

      //Delete the __MAC_OSX directory if it exists
      File[] outputFiles = (new File(outputFolder)).listFiles();
      for(File outputFile: outputFiles){
        if(outputFile.getName().equals("__MACOSX") || outputFile.getName().equals("__MAC_OSX")){
          deleteDirectory(outputFile);
        }
      }

      outputFiles = (new File(outputFolder)).listFiles();
      if(outputFiles.length == 1){
        // Pull out the contents of this sub-directory into the main directory
        File subDirectory = outputFiles[0];
        Logger.debug("Single subdirectory found: " + outputFiles);
        File stepsDirectory = new File(subDirectory, "Steps");
        File contentDirectory = new File(subDirectory, "Content");
        if(stepsDirectory.exists() || contentDirectory.exists()) {
          outputFolder = subDirectory.getAbsolutePath();
          Logger.debug("Using subdirectory, " + outputFolder + " for import instead.");
        } else {
          String msg = "No Steps or Content directories found. Please upload a valid experiment";
          Logger.debug(msg);
          deleteDirectory(new File(rootOutputFolder));
          return badRequest(msg);
        }
      } else {
        File stepsDirectory = new File(outputFolder, "Steps");
        File contentDirectory = new File(outputFolder, "Content");
        if(!stepsDirectory.exists() || !contentDirectory.exists()){
          String msg = "No Steps or Content directories found. Please upload a valid experiment";
          Logger.debug(msg);
          deleteDirectory(new File(rootOutputFolder));
          return badRequest(msg);
        }
      }
    } catch (ZipException e){
      e.printStackTrace();
    }

    try{
      String dotBreadboard = readFile(outputFolder + File.separator + ".breadboard", StandardCharsets.UTF_8);
      ObjectMapper mapper = new ObjectMapper();
      JsonNode dotBreadboardJson = mapper.readTree(dotBreadboard);
      String eVersion = dotBreadboardJson.findPath("version").textValue();
      String eUid = dotBreadboardJson.findPath("experimentUid").textValue();
      String eName = dotBreadboardJson.findPath("experimentName").textValue();
      // TODO: offer the option to import the Experiment UID and/or Name from the .breadboard file

      Logger.debug("Read .breadboard file: experimentVersion = " + eVersion + " experimentUid = " + eUid + " experimentName = " + eName);

      if(eVersion.startsWith("v2.3")){
        import23To23(experiment, user, outputFolder);
      } else if (eVersion.startsWith("v2.2")) {
        import22To23(experiment, user, outputFolder);
      } else {
        // Default to v2.2 import for now
        import22To23(experiment, user, outputFolder);
      }
    } catch(IOException e){
      Logger.debug("No .breadboard file present");
      import22To23(experiment, user, outputFolder);
    } finally{
      deleteDirectory(new File(rootOutputFolder));
    }

    return ok(experiment.toJson());
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
      ZipEntry e;

      // TODO: Write the version based on the build version
      ObjectNode dotBreadboard = Json.newObject();
      dotBreadboard.put("version", "v2.3.0");
      dotBreadboard.put("experimentName", experiment.name);
      dotBreadboard.put("experimentUid", experiment.uid);

      e = new ZipEntry(".breadboard");
      zos.putNextEntry(e);
      zos.write(dotBreadboard.toString().getBytes());
      zos.closeEntry();

      // Client style/html/graph
      e = new ZipEntry("style.css");
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
          String language = (t.language == null || t.language.getCode() == null) ? user.defaultLanguage.getCode(): t.language.getCode();
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

  private static String readFile(String path, Charset encoding) throws IOException{
    byte[] encoded = Files.readAllBytes(Paths.get(path));
    return new String(encoded, encoding);
  }

  /**
   * Specific import function for import v2.2 exports into v2.3 of breadboard. There are some small differences in the
   * export format that must be dealt with as well as breaking changes to the client-html and client-graph that are
   * avoided by simply not importing those files.
   * @param experiment
   * @param user
   * @param directory
   * @return
   */
  private static Boolean import22To23(Experiment experiment, User user,  String directory) throws IOException{
    try {
      String style = FileUtils.readFileToString(new File(directory, "style.css"));
      Logger.debug("style: " + style);
      experiment.setStyle(style);
    } catch(IOException e){
      Logger.error("Unable to read style.css", e);
    }
    Logger.debug("Skipping client.html. Using default instead. Please merge any customizations by hand");
    Logger.debug("Skipping client-graph.js. Using default instead. Please merge any customizations by hand.");

    // Import content
    File contentDir = new File(directory, "/Content");
    for (File langFileOrDir : contentDir.listFiles()){
      if(!langFileOrDir.isDirectory()){
        Logger.debug("Content is in root of Content directory. Attempting to import as default language.");
        try {
          importTranslations(experiment, user.defaultLanguage, contentDir);
        } catch(IOException e){
          Logger.error("Unable to import content from 'Content' directory", e);
          return false;
        } finally{
          break;
        }
      } else {
        // Content is broken out by language
        String languageIso3 = langFileOrDir.getName().equals("null") ? user.defaultLanguage.getCode() : langFileOrDir.getName();
        Language language = Language.findByIso3(languageIso3);
//        Language language = Language.findByIso3(langFileOrDir.getName());
        try{
          importTranslations(experiment, language, langFileOrDir);
        } catch(IOException e){
          Logger.error("Unable to import content from " + langFileOrDir.getName(), e);
        }
      }
    }

    // Import steps
    try {
      File stepsDirectory = new File(directory, "/Steps");
      importSteps(experiment, stepsDirectory);
    } catch(IOException e){
      Logger.error("Unable to import step", e);
      return false;
    }

    // Import parameters
    try {
      File parametersFile = new File(directory, "parameters.csv");
      importParameters(experiment, parametersFile);
    } catch(IOException e){
      Logger.error("Unable to import parameters.csv", e);
    }

    // Import images
    try {
      File imagesDirectory = new File(directory, "/Images");
      importImages(experiment, imagesDirectory);
    } catch(IOException e){
      Logger.error("Unable to import images", e);
      return false;
    }

    // Write changes to DB
    experiment.save();

    user.ownedExperiments.add(experiment);
    user.update();
    user.saveManyToManyAssociations("ownedExperiments");

    return true;
  }


  private static Boolean import23To23(Experiment experiment, User user, String directory) throws IOException{

    String style = FileUtils.readFileToString(new File(directory, "style.css"));
    experiment.setStyle(style);
    String clientGraph = FileUtils.readFileToString(new File(directory, "client-graph.js"));
    experiment.clientGraph = clientGraph;
    String clientHtml = FileUtils.readFileToString(new File(directory, "client-html.html"));
    experiment.clientHtml = clientHtml;

    // Import Steps
    importSteps(experiment, new File(directory, "/Steps"));
    // Import Content
    importContent(experiment, user, new File(directory, "/Content"));
    // Import Parameters
    importParameters(experiment, new File(directory, "parameters.csv"));
    // Import Images
    importImages(experiment, new File(directory, "/Images"));
    // Save
    experiment.save();

    user.ownedExperiments.add(experiment);
    user.update();
    user.saveManyToManyAssociations("ownedExperiments");

    return true;
  }


  private static void importContent(Experiment experiment, User user, File contentDir) throws IOException{
    for (File langFileOrDir : contentDir.listFiles()){
      if(!langFileOrDir.isDirectory()){
        Logger.debug("Content is in root of Content directory. Attempting to import as default language.");
        try {
          importTranslations(experiment, user.defaultLanguage, contentDir);
        } catch(IOException e){
          Logger.error("Unable to import content from 'Content' directory", e);
        } finally{
          break;
        }
      } else {
        // Content is broken out by language
        Language language = Language.findByIso3(langFileOrDir.getName());

        // Sometimes the subdirectory is named null or something else
        if(language == null){
          language = user.defaultLanguage;
        }
        try{
          importTranslations(experiment, language, langFileOrDir);
        } catch(IOException e){
          Logger.error("Unable to import content from " + langFileOrDir.getName(), e);
        }
      }
    }
  }


  /**
   * Import all html files in the supplied directory as translations of the supplied language and experiment.
   * @param experiment
   * @param language
   * @param directory
   * @throws IOException
   */
  private static void importTranslations(Experiment experiment, Language language, File directory) throws IOException{

    for(File file: directory.listFiles()){
      if(FilenameUtils.getExtension(file.getName()).equals("html")){
        Translation translation = new Translation();
        translation.language = language;
        translation.html = FileUtils.readFileToString(file);

        // Check for existing experiment language and add it if it doesn't exist
        boolean hasExperimentLanguage = false;
        for(Language l: experiment.languages){
          if(l.id == language.id){
            hasExperimentLanguage = true;
          }
        }
        if(!hasExperimentLanguage){
          experiment.languages.add(language);
        }

        // Check for existing content and create if it doesn't exist
        String contentName = FilenameUtils.removeExtension(file.getName());
        Content content = null;
        for (Content c: experiment.content){
          if(c.name.equals(contentName)) {
            content = c;
            Logger.debug("Using existing content: " + content.name + " with language " + language.name);
            break;
          }
        }
        if(content == null){
          content = new Content();
          content.name = contentName;
          experiment.content.add(content);
        }
        Logger.debug("Adding translation to " + content.name + " for language " + language.name);
        content.translations.add(translation);
        Logger.debug("Translation length: " + content.translations.size());
      }
    }

  }

  /**
   * Import all of the images from the specified directory as members of the specified experiment.
   * @param experiment
   * @param directory
   * @throws IOException
   */
  private static void importImages(Experiment experiment, File directory) throws IOException{

    File[] imageFiles = directory.listFiles();
    if (imageFiles != null) {
      for (File imageFile : imageFiles) {
        String imageName = FilenameUtils.removeExtension(imageFile.getName());
        String ext = FilenameUtils.getExtension(imageFile.getName());
        if(ext.matches("(jpg|jpeg|png|bmp|gif|svg|webp)")) {
          byte[] imageBytes = FileUtils.readFileToByteArray(imageFile);
          Image image = new Image();
          image.fileName = imageFile.getName();
          image.file = imageBytes;
          String extension = FilenameUtils.getExtension(imageFile.getName());
          image.contentType = "image/" + extension;
          if(extension.equals("svg")){
            image.contentType += "+xml";
          }
          experiment.images.add(image);
          Logger.debug("Adding image: " + imageName);
        } else {
          Logger.debug("Skipping file of unsupported type: " + imageName);
        }
      }
    }

  }

  private static void importParameters(Experiment experiment, File file) throws IOException{

    Reader in = new FileReader(file);
    CSVFormat format = CSVFormat.DEFAULT.withHeader("Name", "Type", "Min.", "Max.", "Default", "Short Description").withFirstRecordAsHeader();
    for (CSVRecord record : format.parse(in)) {
      Parameter parameter = new Parameter();
      parameter.name = record.get("Name");
      parameter.type = record.get("Type");
      parameter.minVal = record.get("Min.");
      parameter.maxVal = record.get("Max.");
      parameter.defaultVal = record.get("Default");
      parameter.description = record.get("Short Description");
      experiment.parameters.add(parameter);
    }

    in.close();

  }

  // Reusable code for importing steps
  private static void importSteps(Experiment experiment, File stepsDirectory) throws IOException{
    File[] stepFiles = stepsDirectory.listFiles();
    if (stepFiles != null) {
      for (File stepFile : stepFiles) {
        if(FilenameUtils.getExtension(stepFile.getName()).equals("groovy")) {
          Step step = new Step();
          String stepName = FilenameUtils.removeExtension(stepFile.getName());
          String source = FileUtils.readFileToString(stepFile);
          step.name = stepName;
          step.source = source;
          experiment.steps.add(step);
          Logger.debug("Adding step: " + stepName);
        } else {
          Logger.debug("Skipping " + stepFile.getName() + " with unsupported file extension");
        }
      }
    }
  }

  private static Experiment newExperiment(User user, Boolean isNewExperiment){
    Experiment experiment = new Experiment();
    if (isNewExperiment) {
      Step onJoin = Experiment.generateOnJoinStep();
      Step onLeave = Experiment.generateOnLeaveStep();
      Step init = Experiment.generateInitStep();
      experiment.steps.add(onJoin);
      experiment.steps.add(onLeave);
      experiment.steps.add(init);
    }
    experiment.clientHtml = Experiment.defaultClientHTML();
    experiment.clientGraph = Experiment.defaultClientGraph();
    // Add the user's default language
    experiment.languages.add(user.defaultLanguage);
    return experiment;
  }
}
