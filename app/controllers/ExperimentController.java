package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.JsonSyntaxException;
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
        if(stepsDirectory.exists()) {
          outputFolder = subDirectory.getAbsolutePath();
          Logger.debug("Using subdirectory, " + outputFolder + " for import instead.");
        } else {
          String msg = "No Steps directory found. Please upload a valid experiment";
          Logger.debug(msg);
          deleteDirectory(new File(rootOutputFolder));
          return badRequest(msg);
        }
      } else {
        File stepsDirectory = new File(outputFolder, "Steps");
        if(!stepsDirectory.exists()){
          String msg = "No Steps directory found. Please upload a valid experiment";
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

      if(eVersion.startsWith("v2.4")){
        import24To24(experiment, user, outputFolder);
      } else {
        // Cannot import from previous versions
        String msg = "Breadboard v2.4 cannot import experiments from previous versions of breadboard.";
        Logger.debug(msg);
        return badRequest(msg);
      }
    } catch(IOException e){
      String msg = "Breadboard v2.4 cannot import experiments from previous versions of breadboard.";
      Logger.debug(msg);
      return badRequest(msg);
    } finally{
      deleteDirectory(new File(rootOutputFolder));
    }

    return ok(experiment.toJson());
  }

  @Security.Authenticated(Secured.class)
  public static Result exportExperiment(Long experimentId){
    Experiment experiment = Experiment.findById(experimentId);
    if(experiment == null){
      return badRequest("No experiment found with that ID");
    }

    File exportDirectory = new File("export/".concat(experiment.name).concat("/").concat(System.currentTimeMillis() + ""));
    File exportFile = new File("export/".concat(experiment.name).concat("/").concat(System.currentTimeMillis() + ".zip"));
    byte returnByteArray[];
    try {
      exportExperimentToDirectory(experimentId, exportDirectory);
      ZipUtil.zipFolder(exportDirectory, exportFile);
      returnByteArray = FileUtils.readFileToByteArray(exportFile);
    } catch (IOException ioe) {
      Logger.error("Unable to export experiment to directory ".concat(exportDirectory.getAbsolutePath()));
      return internalServerError(ioe.getLocalizedMessage());
    } finally {
      if (!exportDirectory.delete()) {
        Logger.error("Error deleting directory ".concat(exportDirectory.getAbsolutePath()));
      }

      if (!exportFile.delete()) {
        Logger.error("Error deleting file ".concat(exportFile.getAbsolutePath()));
      }
    }

    return ok(returnByteArray);
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
      experiment.removeImages();
    }
    importParameters(experiment, new File(directory, "parameters.csv"));
    importSteps(experiment, new File(directory, "/Steps"));
    importContent(experiment, new File(directory, "/Content"));
    importImages(experiment, new File(directory, "/Images"));
    importExperimentViews(experiment, new File(directory, "/Views"));
    experiment.save();
    return experiment.id;
  }

  public static void exportExperimentToDirectory(Long experimentId, File directory) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
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

    File viewsDirectory = new File(directory, "Views");
    for (ExperimentView ev : experiment.getExperimentViews()) {
      FileUtils.writeStringToFile(new File(viewsDirectory, ev.view.concat(".json")), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(ev));
    }

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
    for (Image image : experiment.getImages()) {
      FileUtils.writeByteArrayToFile(new File(imagesDirectory, image.fileName), image.file);
    }
  }


  private static String readFile(String path, Charset encoding) throws IOException{
    byte[] encoded = Files.readAllBytes(Paths.get(path));
    return new String(encoded, encoding);
  }

  private static Boolean import24To24(Experiment experiment, User user, String directory) throws IOException{

    // Import Steps
    importSteps(experiment, new File(directory, "/Steps"));
    // Import Content
    importContent(experiment, new File(directory, "/Content"));
    // Import Parameters
    importParameters(experiment, new File(directory, "parameters.csv"));
    // Import Images
    importImages(experiment, new File(directory, "/Images"));
    // Import Views
    importExperimentViews(experiment, new File(directory, "/Views"));
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
  private static void importImages(Experiment experiment, File directory) throws IOException {
    ArrayList<Image> images = getImagesFromDirectory(directory);
    experiment.images.addAll(images);
    experiment.save();
  }

  public static ArrayList<Image> getImagesFromDirectory(File directory) throws IOException {
    ArrayList<Image> returnImages = new ArrayList<>();
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
          returnImages.add(image);
          Logger.debug("Adding image: " + imageName);
        } else {
          Logger.debug("Skipping file of unsupported type: " + imageName);
        }
      }
    }
    return returnImages;
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
  public static void importSteps(Experiment experiment, File stepsDirectory) throws IOException{
    ArrayList<Step> steps = getStepsFromDirectory(stepsDirectory);
    for (Step step : steps) {
      experiment.addStep(step);
    }
    experiment.save();
  }

  // Reusable code for importing views
  public static void importExperimentViews(Experiment experiment, File viewsDirectory) throws IOException{
    ArrayList<ExperimentView> experimentViews = getExperimentViewsFromDirectory(viewsDirectory);
    for (ExperimentView ev : experimentViews) {
      experiment.addExperimentView(ev);
    }
    experiment.save();
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

  public static ArrayList<ExperimentView> getExperimentViewsFromDirectory(File experimentViewsDirectory) throws IOException, JsonSyntaxException {
    ArrayList<ExperimentView> returnExperimentViews = new ArrayList<>();
    File[] directories = experimentViewsDirectory.listFiles();
    if (directories != null) {
      for (File dir : directories) {
        if (dir.isDirectory()) {
          ExperimentView ev = ExperimentView.getExperimentViewFromDirectory(dir);
          returnExperimentViews.add(ev);
        }
      }
    }
    return returnExperimentViews;
  }

  private static Experiment newExperiment(User user, Boolean isNewExperiment){
    Experiment experiment = new Experiment();
    if (isNewExperiment) {
      try {
        importExperimentViews(experiment, new File("conf/defaults/default-experiment/Views"));
        importSteps(experiment, new File("conf/defaults/default-experiment/Steps"));
      } catch (IOException ioe) {
        Logger.error("Error reading default-experiment directory: ".concat(ioe.getLocalizedMessage()));
      }
    }
    // Add the user's default language
    experiment.languages.add(user.defaultLanguage);
    return experiment;
  }
}
