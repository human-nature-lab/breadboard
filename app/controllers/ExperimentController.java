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
import java.util.ArrayList;
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
      copyExperimentId = json.findPath("copyExperimentId").asLong();
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
    if (user.defaultLanguage != null && copyExperiment == null) {
      experiment.languages.add(user.defaultLanguage);
    }
    experiment.fileMode = false;
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
      String version = play.Play.application().configuration().getString("application.version");
      dotBreadboard.put("version", version);
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

      e = new ZipEntry("client-html.html");
      zos.putNextEntry(e);
      zos.write(experiment.clientHtml.getBytes());
      zos.closeEntry();

      e = new ZipEntry("client-graph.js");
      zos.putNextEntry(e);
      zos.write(experiment.clientGraph.getBytes());
      zos.closeEntry();

      // Steps
      for (Step step : experiment.getSteps()) {
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
      zos.write(experiment.parametersToCsv().getBytes());
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

  /**
   * If experimentId is null or invalid, creates a new experiment and returns the newly created experiment ID.
   * Otherwise, imports the experiment files over the existing experiment and returns the provided experimentID.
   * @param experimentId  The ID of the experiment to import over, if null or invalid create new experiment
   * @param user          The User who is making the import request, newly created experiments will be associated with the user
   * @param directory     The File location of the experiment files to impoart
   * @return              the experimentId of the imported experiment
   * @throws IOException
   */
  public static Long importExperimentFromDirectory(Long experimentId, User user, File directory) throws IOException {
    Experiment experiment;
    if (experimentId == null || ((experiment = Experiment.findById(experimentId)) == null) ) {
      // New experiment
      experiment = new Experiment();
      user.ownedExperiments.add(experiment);
      user.update();
      user.saveManyToManyAssociations("ownedExperiments");
    } else {
      // Existing experiment, delete before re-importing
      experiment.removeSteps();
      experiment.removeContent();
      experiment.removeParameters();
    }
    experiment.setClientGraph(FileUtils.readFileToString(new File(directory, "client-graph.js")));
    experiment.setClientHtml(FileUtils.readFileToString(new File(directory, "client-html.html")));
    experiment.setStyle(FileUtils.readFileToString(new File(directory, "style.css")));
    importParameters(experiment, new File(directory, "parameters.csv"));
    importSteps(experiment, new File(directory, "/Steps"));
    importContent(experiment, new File(directory, "/Content"));
    experiment.save();
    return experiment.id;
  }

  public static void exportExperimentToDirectory(Long experimentId, File directory) throws IOException {
    Experiment experiment = Experiment.findById(experimentId);
    if(experiment == null){
      throw new IOException("Experiment with the ID " + experimentId + " not found.");
    }

    // Clean up existing files
    if (directory.exists() && directory.isDirectory()) {
      FileUtils.deleteDirectory(directory);
    }

    // Create the directory
    FileUtils.forceMkdir(directory);

    String version = play.Play.application().configuration().getString("application.version");
    ObjectNode dotBreadboard = Json.newObject();
    dotBreadboard.put("version", version);
    dotBreadboard.put("experimentName", experiment.name);
    dotBreadboard.put("experimentUid", experiment.uid);

    FileUtils.writeStringToFile(new File(directory, ".breadboard"), dotBreadboard.toString());
    FileUtils.writeStringToFile(new File(directory, "style.css"), experiment.getStyle());
    FileUtils.writeStringToFile(new File(directory, "client-html.html"), experiment.getClientHtml());
    FileUtils.writeStringToFile(new File(directory, "client-graph.js"), experiment.getClientGraph());

    File stepsDirectory = new File(directory, "Steps");
    for (Step step : experiment.getSteps()) {
      FileUtils.writeStringToFile(new File(stepsDirectory, step.name.concat(".groovy")), step.source);
    }

    File contentDirectory = new File(directory, "Content");
    for (Content c : experiment.getContent()) {
      for (Translation t : c.translations) {
        String language = (t.language == null || t.language.getCode() == null) ? "en" : t.language.getCode();
        File translationDirectory = new File(contentDirectory, language);
        FileUtils.writeStringToFile(new File(translationDirectory, c.name.concat(".html")), t.html);
      }
    }

    FileUtils.writeStringToFile(new File(directory, "parameters.csv"), experiment.parametersToCsv());

    File imagesDirectory = new File(directory, "Images");
    for (Image image : experiment.images) {
      FileUtils.writeByteArrayToFile(new File(imagesDirectory, image.fileName), image.file);
    }
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
    experiment.setClientGraph(FileUtils.readFileToString(new File(directory, "client-graph.js")));
    experiment.setClientHtml(FileUtils.readFileToString(new File(directory, "client-html.html")));

    // Import Steps
    importSteps(experiment, new File(directory, "/Steps"));
    // Import Content
    importContent(experiment, new File(directory, "/Content"));
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


  private static void importContent(Experiment experiment, File contentDir) throws IOException {
    ArrayList<Content> content = getContentFromDirectory(contentDir);
    for (Content c : content) {
      for (Translation t : c.translations) {
        boolean hasExperimentLanguage = false;
        for(Language l: experiment.languages){
          if(l.id.equals(t.language.id)){
            hasExperimentLanguage = true;
          }
        }
        if(!hasExperimentLanguage){
          experiment.languages.add(t.language);
        }
      }
      experiment.content.add(c);
    }
  }

  public static ArrayList<Content> getContentFromDirectory(File contentDir) throws IOException {
    ArrayList<Content> returnContent = new ArrayList<>();
    // Default to English if the directory isn't specified
    Language defaultLanguage = Language.findByIso3("eng");
    if (contentDir == null || !contentDir.exists()) {
      throw new IOException("Directory not found.");
    }
    for (File langFileOrDir : contentDir.listFiles()){
      if(!langFileOrDir.isDirectory() && FilenameUtils.getExtension(langFileOrDir.getName()).equalsIgnoreCase("html")) {
        Logger.debug("Content is in root of Content directory. Attempting to import as default language.");
        try {
          ArrayList<Content> rootContent = getContentFromSubdirectory(contentDir, defaultLanguage);
          returnContent.addAll(rootContent);
        } catch(IOException e){
          Logger.error("Unable to import content from 'Content' directory", e);
        } finally{
          break;
        }
      } else if (langFileOrDir.isDirectory()) {
        // Content is broken out by language
        Language language = Language.findByIso3(langFileOrDir.getName());

        // Sometimes the subdirectory is named null or something else
        if(language == null){
          language = defaultLanguage;
        }
        try{
          ArrayList<Content> languageContent = getContentFromSubdirectory(langFileOrDir, language);
          returnContent.addAll(languageContent);
        } catch(IOException e){
          Logger.error("Unable to import content from " + langFileOrDir.getName(), e);
        }
      }
    }
    return returnContent;

  }


  /**
   * Import all html files in the supplied directory as translations of the supplied language and experiment.
   * @param experiment
   * @param language
   * @param directory
   * @throws IOException
   */
  private static void importTranslations(Experiment experiment, Language language, File directory) throws IOException {

    // Check for existing experiment language and add it if it doesn't exist
    boolean hasExperimentLanguage = false;
    for(Language l: experiment.languages){
      if(l.id.equals(language.id)){
        hasExperimentLanguage = true;
      }
    }
    if(!hasExperimentLanguage){
      experiment.languages.add(language);
    }

    ArrayList<Content> importedContent = getContentFromSubdirectory(directory, language);

    for(Content content: importedContent) {
      // Check for existing content and create if it doesn't exist
      boolean contentExists = false;
      for (Content c : experiment.content) {
        if (c.name.equals(content.name)) {
          contentExists = true;
          Logger.debug("Using existing content: " + content.name + " with language " + language.name);
          break;
        }
      }

      // If content with that name doesn't already exist, import it
      if (!contentExists) {
        experiment.content.add(content);
      }
    }

  }

  private static ArrayList<Content> getContentFromSubdirectory(File directory, Language language) throws IOException {
    ArrayList<Content> returnContent = new ArrayList<>();

    for(File file: directory.listFiles()){
      if(FilenameUtils.getExtension(file.getName()).equals("html")){
        Translation translation = new Translation();
        translation.language = language;
        translation.html = FileUtils.readFileToString(file);

        String contentName = FilenameUtils.removeExtension(file.getName());
        Content content = new Content();
        content.name = contentName;
        content.translations.add(translation);

        returnContent.add(content);
      }
    }

    return returnContent;
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

  private static void importParameters(Experiment experiment, File file) throws IOException {
    ArrayList<Parameter> parameters = getParametersFromFile(file);
    experiment.parameters.addAll(parameters);
    experiment.update();
  }

  public static ArrayList<Parameter> getParametersFromFile(File parameterFile) throws IOException {
    ArrayList<Parameter> returnParameters = new ArrayList<>();
    Reader in = new FileReader(parameterFile);
    CSVFormat format = CSVFormat.DEFAULT.withHeader("Name", "Type", "Min.", "Max.", "Default", "Short Description").withFirstRecordAsHeader();
    for (CSVRecord record : format.parse(in)) {
      Parameter parameter = new Parameter();
      parameter.name = record.get("Name");
      parameter.type = record.get("Type");
      parameter.minVal = record.get("Min.");
      parameter.maxVal = record.get("Max.");
      parameter.defaultVal = record.get("Default");
      parameter.description = record.get("Short Description");
      returnParameters.add(parameter);
    }
    return returnParameters;
  }

  // Reusable code for importing steps
  private static void importSteps(Experiment experiment, File stepsDirectory) throws IOException{
    ArrayList<Step> steps = getStepsFromDirectory(stepsDirectory);
    for (Step step : steps) {
      experiment.addStep(step);
    }
  }

  public static ArrayList<Step> getStepsFromDirectory(File stepsDirectory) throws IOException {
    ArrayList<Step> returnSteps = new ArrayList<>();
    File[] stepFiles = stepsDirectory.listFiles();
    if (stepFiles != null) {
      for (File stepFile : stepFiles) {
        if(FilenameUtils.getExtension(stepFile.getName()).equals("groovy")) {
          Step step = new Step();
          String stepName = FilenameUtils.removeExtension(stepFile.getName());
          String source = FileUtils.readFileToString(stepFile);
          step.name = stepName;
          step.source = source;
          returnSteps.add(step);
          Logger.debug("Adding step: " + stepName);
        } else {
          Logger.debug("Skipping " + stepFile.getName() + " with unsupported file extension");
        }
      }
    }
    return returnSteps;
  }

  private static Experiment newExperiment(User user, Boolean isNewExperiment){
    Experiment experiment = new Experiment();
    if (isNewExperiment) {
      Step onJoin = Experiment.generateOnJoinStep();
      Step onLeave = Experiment.generateOnLeaveStep();
      Step init = Experiment.generateInitStep();
      experiment.addStep(onJoin);
      experiment.addStep(onLeave);
      experiment.addStep(init);
    }
    experiment.setClientHtml(Experiment.defaultClientHTML());
    experiment.setClientGraph(Experiment.defaultClientGraph());
    // Add the user's default language
    experiment.languages.add(user.defaultLanguage);
    return experiment;
  }
}
