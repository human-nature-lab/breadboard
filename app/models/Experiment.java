package models;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.annotation.ConcurrencyMode;
import com.avaje.ebean.annotation.EntityConcurrencyMode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import controllers.ExperimentController;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import play.Logger;
import play.Play;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.libs.Json;
import security.PathSafety;

import javax.persistence.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Entity
@EntityConcurrencyMode(ConcurrencyMode.NONE)
@Table(name = "experiments")
public class Experiment extends Model {
  @Id
  public Long id;

  @Constraints.Required
  @Formats.NonEmpty
  public String name;

  public String uid;

  @OneToMany(cascade = CascadeType.ALL)
  @OrderBy("name asc")
  private List<Step> steps = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL)
  public List<Content> content = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL)
  public List<Parameter> parameters = new ArrayList<>();

  @JsonIgnore
  public ContentFetcher contentFetcher = new ContentFetcher(this);

  @OneToMany(cascade = CascadeType.ALL)
  public List<ExperimentInstance> instances = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL)
  public List<Image> images = new ArrayList<>();

  @ManyToMany
  @JoinTable(name = "experiments_languages")
  public List<Language> languages = new ArrayList<>();

  public static final Long TEST_INSTANCE_ID = 0L;
  public ExperimentInstance TEST_INSTANCE = null;

  // The AMT QualificationTypeId for the Previous Worker qualification specific to this experiment type.
  public String qualificationTypeId;

  // QualificationTypeId in the AMT Sandbox
  public String qualificationTypeIdSandbox;

  public static final String ON_JOIN_STEP_NAME = "OnJoinStep";
  public static final String ON_LEAVE_STEP_NAME = "OnLeaveStep";

  public Boolean fileMode;

  /*
   * The CSS Style for the experiment
   */
  @Column(columnDefinition = "text")
  public String style = "";

  /*
   * The HTML + JavaScript for the client.
   */
  @Column(columnDefinition = "text")
  public String clientHtml = "";

  /*
   * The client-graph.js for the client.
   */
  @Column(columnDefinition = "text")
  public String clientGraph = "";

  @JsonIgnore
  public static Model.Finder<Long, Experiment> find = new Model.Finder(Long.class, Experiment.class);

  public static List<Experiment> findAll() {
    return find.all();
  }

  public static Experiment findByName(String name) {
    return find.where().eq("name", name).findUnique();
  }

  public static Experiment findByUid(String uid) {
    return find.where().eq("uid", uid).findUnique();
  }

  public static Experiment findById(Long id) {
    return find.where().eq("id", id).findUnique();
  }

  public ExperimentInstance getTestInstance() {
    if (TEST_INSTANCE == null) {
      TEST_INSTANCE = new ExperimentInstance("TESTING", this);
      TEST_INSTANCE.status = ExperimentInstance.Status.TESTING;
      TEST_INSTANCE.id = TEST_INSTANCE_ID;
    }
    return TEST_INSTANCE;
  }

  public Experiment() {
    this.uid = UUID.randomUUID().toString();
  }

  public Experiment(String uid) {
    this.uid = uid;
  }

  /**
   * Copy constructor. Everything is copied except the experimentInstances and name.
   *
   * @param experiment the experiment to be copied from
   */
  public Experiment(Experiment experiment) {
    this.uid = UUID.randomUUID().toString();
    this.style = experiment.getStyle();
    this.clientHtml = experiment.getClientHtml();
    this.clientGraph = experiment.getClientGraph();

    for (Step step : experiment.getSteps()) {
      this.steps.add(new Step(step));
    }
    for (Content c : experiment.getContent()) {
      this.content.add(new Content(c));
    }
    for (Parameter param : experiment.getParameters()) {
      this.parameters.add(new Parameter(param));
    }
    for (Image image : experiment.getImages()) {
      this.images.add(new Image(image));
    }
    for(Language language : experiment.languages) {
      this.languages.add(language);
    }
  }

  public String getDirectoryName() {
    String returnString = StringUtils.replace(this.name, " ", "-").concat("_").concat(this.id.toString());
    return returnString;
  }

  /**
   * Build a {@link File} inside this experiment's dev directory, guarding against path traversal that
   * a malicious experiment name could introduce via {@link #getDirectoryName()} (the name is set
   * straight from request params and only has spaces replaced, so "../" survives). Returns null if
   * the resolved path would escape the dev directory; callers must treat null as "not available".
   *
   * <p>Public so the (unauthenticated) image-serving endpoint can reuse the same guard instead of
   * rebuilding the dev path by hand — see {@code ImagesController.getImageByFileName}.
   */
  public File devPath(String... segments) {
    Path devRoot = new File(Play.application().path(), "dev").toPath();
    StringBuilder child = new StringBuilder(getDirectoryName());
    for (String segment : segments) {
      child.append('/').append(segment);
    }
    Path safe = PathSafety.resolveContained(devRoot, child.toString());
    if (safe == null) {
      Logger.error("Refusing to access path outside the dev directory for experiment " + this.id
          + " (name='" + this.name + "')");
      return null;
    }
    return safe.toFile();
  }

  public void setFileMode(Boolean fileMode) {
    this.fileMode = fileMode;
  }

  public List<Image> getImages() {
    if (this.fileMode) {
      ArrayList<Image> returnImages = new ArrayList<>();
      File imagesDirectory = devPath("Images");
      if (imagesDirectory != null && imagesDirectory.isDirectory()) {
        try {
          returnImages = ExperimentController.getImagesFromDirectory(imagesDirectory);
        } catch (IOException ioe) {
          Logger.error("Error reading images from " + imagesDirectory + ", check your permissions.");
          Logger.debug(ioe.getMessage());;
        }
      }
      return returnImages;
    }
    return this.images;
  }

  public List<Content> getContent() {
    if (this.fileMode) {
      ArrayList<Content> returnContent = new ArrayList<>();
      File contentDirectory = devPath("Content");
      if (contentDirectory != null && contentDirectory.isDirectory()) {
        try {
          returnContent = ExperimentController.getContentFromDirectory(contentDirectory);
        } catch (IOException ioe) {
          Logger.error("Error reading Content from " + contentDirectory + ", check your permissions.");
          Logger.debug(ioe.getMessage());;
        }
      }
      return returnContent;

    }
    return this.content;
  }

  public void removeContent() {
    Iterator<Content> iter = this.content.iterator();
    while (iter.hasNext()) {
      Content c = iter.next();
      iter.remove();
      c.delete();
    }
    this.update();
  }

  public void removeImages() {
    Iterator<Image> iter = this.images.iterator();
    while (iter.hasNext()) {
      Image i = iter.next();
      iter.remove();
      i.delete();
    }
    this.update();
  }

  public String getStyle() {
    if (this.fileMode) {
      String returnStyle = "";
      File styleFile = devPath("style.css");
      if (styleFile != null) {
        try {
          returnStyle = FileUtils.readFileToString(styleFile);
        } catch (IOException ioe) {
          Logger.error("Error reading style.css file from the dev directory, check your permissions.");
          Logger.debug(ioe.getMessage());;
        }
      }
      return returnStyle;
    } else {
      return this.style;
    }
  }

  public String getClientHtml() {
    if (this.fileMode) {
      String returnClientHtml = "";
      File clientHtmlFile = devPath("client-html.html");
      if (clientHtmlFile != null) {
        try {
          returnClientHtml = FileUtils.readFileToString(clientHtmlFile);
        } catch (IOException ioe) {
          Logger.error("Error reading client-html.html file from the dev directory, check your permissions.");
          Logger.debug(ioe.getMessage());;
        }
      }
      return returnClientHtml;
    } else {
      return this.clientHtml;
    }
  }

  // --- Image reference stability across export/import -------------------------------------------
  // Content translations should reference uploaded images through the {{imageBase}} placeholder
  // instead of a literal /images/<id> prefix. The experiment id changes whenever an experiment is
  // exported and re-imported, so a stored literal id breaks every image link on import. We keep the
  // placeholder in stored/exported content (edit and export paths see it verbatim) and substitute the
  // current id only when serving to a participant.
  private static final Pattern IMAGE_BASE_TOKEN = Pattern.compile("\\{\\{\\s*imageBase\\s*\\}\\}");

  /**
   * Replace the {{imageBase}} placeholder with this experiment's image URL prefix (/images/<id>).
   * Call only on the runtime serving path, never on the edit/export path (which must preserve the
   * placeholder so image links survive a round-trip through export/import).
   */
  public String expandImageBase(String markup) {
    if (markup == null || this.id == null) {
      return markup;
    }
    String base = "/images/" + this.id;
    return IMAGE_BASE_TOKEN.matcher(markup).replaceAll(Matcher.quoteReplacement(base));
  }

  public String getClientGraph() {
    if (this.fileMode) {
      String returnClientGraph = "";
      File clientGraphFile = devPath("client-graph.js");
      if (clientGraphFile != null) {
        try {
          returnClientGraph = FileUtils.readFileToString(clientGraphFile);
        } catch (IOException ioe) {
          Logger.error("Error reading client-graph.js file from the dev directory, check your permissions.");
          Logger.debug(ioe.getMessage());;
        }
      }
      return returnClientGraph;
    } else {
      return this.clientGraph;
    }
  }

  public List<Step> getSteps() {
    if (this.fileMode) {
      ArrayList<Step> returnSteps = new ArrayList<>();
      File stepsDirectory = devPath("steps");
      if (stepsDirectory != null) {
        try {
          returnSteps = ExperimentController.getStepsFromDirectory(stepsDirectory);
        } catch (IOException ioe) {
          Logger.error("Error reading Steps from " + stepsDirectory + ", check your permissions.");
          Logger.debug(ioe.getMessage());;
        }
      }
      return returnSteps;
    } else {
      return this.steps;
    }
  }

  public void addStep(Step step) {
    this.steps.add(step);
  }

  public void removeSteps() {
    Iterator<Step> iter = this.steps.iterator();
    while (iter.hasNext()) {
      Step s = iter.next();
      iter.remove();
      s.delete();
    }
    this.update();
  }

  public void toggleFileMode(User user) {
    // devPath() guards against a malicious experiment name escaping the dev directory — important
    // here because this method deletes and recreates experimentDirectory during import/export.
    File experimentDirectory = devPath();
    if (experimentDirectory == null) {
      // devPath() already logged the traversal refusal; make the abandoned toggle explicit so an
      // operator can see why fileMode did not change rather than it silently doing nothing.
      Logger.error("toggleFileMode aborted for experiment " + this.id + " (name='" + this.name
          + "'): its dev directory escapes the dev root, so fileMode was left unchanged.");
      return;
    }
    try {
      if (this.fileMode) {
        // Turning fileMode off, let's import the files into the current experiment
        ExperimentController.importExperimentFromDirectory(this.id, user, experimentDirectory);
      } else {
        // Turning fileMode on, let's export the experiment into the appropriate directory
        ExperimentController.exportExperimentToDirectory(this.id, experimentDirectory);
      }
      this.setFileMode(!this.fileMode);
      this.save();
    } catch (IOException ioe) {
      Logger.error("Unable to access " + experimentDirectory + ", check your file permissions.", ioe);
      // if (Logger.canLog(Logger.DEBUG)) {
      //   ioe.printStackTrace(Logger);
      // }
    }
  }

  public String parametersToCsv() {
    CSVFormat format = CSVFormat.DEFAULT.withHeader("Name", "Type", "Min.", "Max.", "Default", "Short Description");
    StringBuilder stringBuilder = new StringBuilder();
    try {
      CSVPrinter csvPrinter = new CSVPrinter(stringBuilder, format);
      for (Parameter param : getParameters()) {
        csvPrinter.printRecord(param.name, param.type, param.minVal, param.maxVal, param.defaultVal, param.description);
      }
    } catch (IOException ioe) {}
    return stringBuilder.toString();
  }

  public void export() throws IOException {
    File experimentDirectory = new File(Play.application().path().toString() + "/experiments/" + this.name);
    FileUtils.writeStringToFile(new File(experimentDirectory, "style.css"), this.getStyle());
    FileUtils.writeStringToFile(new File(experimentDirectory, "client.html"), this.getClientHtml());
    FileUtils.writeStringToFile(new File(experimentDirectory, "client-graph.js"), this.getClientGraph());

    File stepsDirectory = new File(experimentDirectory, "/Steps");
    for (Step step : this.getSteps()) {
      FileUtils.writeStringToFile(new File(stepsDirectory, step.name.concat(".groovy")), step.source);
    }

    File contentDirectory = new File(experimentDirectory, "/Content");
    for (Content c : this.getContent()) {
      // Write to a subdirectory based on the language of the Content or 'en' if language is undefined
      for (Translation t : c.translations) {
        String language = (t.language == null) ? "en" : t.language.code;
        File languageDirectory = new File(contentDirectory, "/" + language);
        FileUtils.writeStringToFile(new File(languageDirectory, c.name.concat(".html")), t.html);
      }
    }

    String ls = System.getProperty("line.separator");
    File parametersFile = new File(experimentDirectory, "parameters.csv");
    FileUtils.writeStringToFile(parametersFile, "Name,Type,Min.,Max.,Default,Short Description" + ls);
    for (Parameter param : this.getParameters()) {
      FileUtils.writeStringToFile(parametersFile, param.name + "," + param.type + "," + param.minVal + "," + param.maxVal + "," + param.defaultVal + "," + param.description + ls, true);
    }

    File imagesDirectory = new File(experimentDirectory, "/Images");
    for (Image image : this.getImages()) {
      FileUtils.writeByteArrayToFile(new File(imagesDirectory, image.fileName), image.file);
    }
  }

  public static String defaultClientHTML() {
    String contents = "";
    try {
      InputStream defaultClientHtml = Play.application().resourceAsStream("defaults/client-html.html");
      if (defaultClientHtml == null) defaultClientHtml = Play.application().resourceAsStream("defaults/default-client-html.html");

      if (defaultClientHtml == null) {
        Logger.error("Couldn't find the conf/defaults/default-client-html.html file.");
      } else {
        contents = IOUtils.toString(defaultClientHtml);
      }
    } catch(IOException e){
      Logger.error("Error reading the conf/defaults/client-html.html file.");
    }
    return contents;
  }

  public static String defaultClientGraph() {
    String contents = "";
    try {
      InputStream defaultClientGraph = Play.application().resourceAsStream("defaults/client-graph.js");
      if (defaultClientGraph == null) defaultClientGraph = Play.application().resourceAsStream("defaults/default-client-graph.js");

      if (defaultClientGraph == null) {
        Logger.error("Couldn't find the conf/defaults/default-client-graph.js file.");
      } else {
        contents = IOUtils.toString(defaultClientGraph);
      }
    } catch(IOException e){
      Logger.error("Error reading the conf/defaults/default-client-graph.js file.");
    }
    return contents;
  }

  public static Step generateOnJoinStep() {
    Step onJoin = new Step();
    onJoin.name = "OnJoinStep";
    onJoin.source = "onJoinStep = stepFactory.createNoUserActionStep()\n" +
        "\n" +
        "onJoinStep.run = { playerId->\n" +
        "  println \"onJoinStep.run\"\n" +
        "  def player = g.getVertex(playerId)\n" +
        "}" +
        "\n" +
        "onJoinStep.done = {\n" +
        "  println \"onJoinStep.done\"\n" +
        "}";

    return onJoin;
  }

  public static Step generateOnLeaveStep() {
    Step onLeave = new Step();
    onLeave.name = "OnLeaveStep";
    onLeave.source = "onLeaveStep = stepFactory.createNoUserActionStep()\n" +
        "\n" +
        "onLeaveStep.run = {\n" +
        "  println \"onLeaveStep.run\"\n" +
        "}" +
        "\n" +
        "onLeaveStep.done = {\n" +
        "  println \"onLeaveStep.done\"\n" +
        "}";
    return onLeave;
  }

  public static Step generateInitStep() {
    Step init = new Step();
    init.name = "InitStep";
    init.source = "initStep = stepFactory.createStep(\"InitStep\")\n" +
        "\n" +
        "initStep.run = {\n" +
        "  println \"initStep.run\"\n" +
        "}" +
        "\n" +
        "initStep.done = {\n" +
        "  println \"initStep.done\"\n" +
        "}";
    return init;
  }

  @Override
  public void delete() {
    Ebean.createSqlUpdate("delete from experiments_languages where experiments_id = :experimentId")
        .setParameter("experimentId", this.id)
        .execute();

    for (Step s : steps) {
      s.delete();
    }
    for (Content c : content) {
      c.delete();
    }
    for (Parameter p : parameters) {
      p.delete();
    }
    for (ExperimentInstance ei : instances) {
      ei.delete();
    }
    for (Image i : images) {
      i.delete();
    }
    super.delete();
  }

  public void setStyle(String style) {
    this.style = style;
  }

  public void setClientHtml(String clientHtml) {
    this.clientHtml = clientHtml;
  }

  public void setClientGraph(String clientGraph) {
    this.clientGraph = clientGraph;
  }

  public Content getExperimentContent(Long id) {
    for (Content c : content) {
      if (c.id.equals(id))
        return c;
    }
    return null;
  }

  public Content getContentByName(String name) {
    for (Content c : content) {
      if (c.name.equals(name))
        return c;
    }
    return null;
  }

  public Parameter getParameterByName(String name) {
    for (Parameter p : parameters) {
      if (p.name.equals(name))
        return p;
    }
    return null;
  }

  public List<Parameter> getParameters() {
    if (this.fileMode) {
      ArrayList<Parameter> returnParameters = new ArrayList<>();
      File parameterFile = devPath("parameters.csv");
      if (parameterFile != null) {
        try {
          returnParameters = ExperimentController.getParametersFromFile(parameterFile);
        } catch (IOException ioe) {
          Logger.error("Error reading " + parameterFile + " from the dev directory, check your permissions.");
          Logger.debug(ioe.getMessage());;
        }
      }
      return returnParameters;

    }
    return this.parameters;
  }

  public void removeParameters() {
    Iterator<Parameter> iter = this.parameters.iterator();
    while (iter.hasNext()) {
      Parameter p = iter.next();
      iter.remove();
      p.delete();
    }
    this.update();
  }

  public boolean hasOnJoinStep() {
    return getOnJoinStep() != null;
  }

  public boolean hasOnLeaveStep() {
    return getOnLeaveStep() != null;
  }

  public Step getOnJoinStep() {
    for (Step step : this.getSteps()) {
      if (ON_JOIN_STEP_NAME.equalsIgnoreCase(step.name)) {
        return step;
      }
    }
    return null;
  }

  public Step getOnLeaveStep() {
    for (Step step : this.getSteps()) {
      if (ON_LEAVE_STEP_NAME.equalsIgnoreCase(step.name)) {
        return step;
      }
    }
    return null;
  }

  @JsonValue
  public ObjectNode toJson() {
    ObjectNode experiment = Json.newObject();

    experiment.put("id", id);
    experiment.put("name", name);
    experiment.put("uid", uid);
    experiment.put("fileMode", fileMode);

    ArrayNode jsonSteps = experiment.putArray("steps");
    for (Step s : getSteps()) {
      jsonSteps.add(s.toJson());
    }

    ArrayNode jsonLanguages = experiment.putArray("languages");
    for (Language l : languages) {
      jsonLanguages.add(l.toJson());
    }

    ArrayNode jsonContent = experiment.putArray("content");
    for (Content c : getContent()) {
      jsonContent.add(c.toJson());
    }

    ArrayNode jsonParameters = experiment.putArray("parameters");
    for (Parameter p : getParameters()) {
      jsonParameters.add(p.toJson());
    }

    ArrayNode jsonInstances = experiment.putArray("instances");
    for (ExperimentInstance ei : instances) {
      // Only return the name and ID of the instances
      // TODO: Perhaps add the Date/Time of the instance here as well
      jsonInstances.add(ei.toJsonStub());
    }

    ArrayNode jsonImages = experiment.putArray("images");
    for (Image i : getImages()) {
      jsonImages.add(i.toJson());
    }

    experiment.put("style", getStyle());
    experiment.put("clientGraphHash", getClientGraph().hashCode());
    experiment.put("clientHtmlHash", getClientHtml().hashCode());

    return experiment;
  }

  public String toString() {
    return "Experiment(" + id + ")";
  }
}

