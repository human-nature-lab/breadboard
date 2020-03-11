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
import org.apache.commons.lang3.StringUtils;
import play.Logger;
import play.Play;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.libs.Json;

import javax.persistence.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

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
  private List<Step> steps = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL)
  public List<Content> content = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL)
  public List<Parameter> parameters = new ArrayList<>();

  @OneToMany(cascade = CascadeType.ALL)
  public List<ExperimentView> experimentViews = new ArrayList<>();

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

    for (ExperimentView experimentView : experiment.getExperimentViews()) {
      this.experimentViews.add(new ExperimentView(experimentView));
    }
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

  public void setFileMode(Boolean fileMode) {
    this.fileMode = fileMode;
  }

  public List<Image> getImages() {
    if (this.fileMode != null && this.fileMode) {
      ArrayList<Image> returnImages = new ArrayList<>();
      File imagesDirectory = new File(Play.application().path().toString() + "/dev/" + getDirectoryName() + "/Images");
      try {
        returnImages = ExperimentController.getImagesFromDirectory(imagesDirectory);
      } catch (IOException ioe) {
        Logger.error("Error reading images from the dev directory, check your permissions.");
      }
      return returnImages;
    }
    return this.images;
  }

  public List<Content> getContent() {
    if (this.fileMode != null && this.fileMode) {
      ArrayList<Content> returnContent = new ArrayList<>();
      File contentDirectory = new File(Play.application().path().toString() + "/dev/" + getDirectoryName() + "/Content");
      try {
        returnContent = ExperimentController.getContentFromDirectory(contentDirectory);
      } catch (IOException ioe) {
        Logger.error("Error reading steps file from the dev directory, check your permissions.");
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

  public List<Step> getSteps() {
    if (this.fileMode != null && this.fileMode) {
      ArrayList<Step> returnSteps = new ArrayList<>();
      File stepsDirectory = new File(Play.application().path().toString() + "/dev/" + getDirectoryName() + "/steps");
      try {
        returnSteps = ExperimentController.getStepsFromDirectory(stepsDirectory);
      } catch (IOException ioe) {
        Logger.error("Error reading steps file from the dev directory, check your permissions.");
      }
      return returnSteps;
    } else {
      return this.steps;
    }
  }

  public List<ExperimentView> getExperimentViews() {
    if (this.fileMode != null && this.fileMode) {
      ArrayList<ExperimentView> returnExperimentViews = new ArrayList<>();
      File experimentViewsDirectory = new File(Play.application().path().toString() + "/dev/" + getDirectoryName() + "/views");
      try {
        returnExperimentViews = ExperimentController.getExperimentViewsFromDirectory(experimentViewsDirectory);
      } catch (IOException ioe) {
        Logger.error("Error reading steps file from the dev directory, check your permissions.");
      }
      return returnExperimentViews;
    } else {
      return this.experimentViews;
    }
  }

  public ExperimentView getExperimentView(String view) {
    List<ExperimentView> views = this.getExperimentViews();
    for (ExperimentView v : views) {
      if (v.view.equals(view)) {
        return v;
      }
    }
    return null;
  }

  public void addStep(Step step) {
    this.steps.add(step);
  }

  public void addExperimentView(ExperimentView ev) {
    this.experimentViews.add(ev);
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
    try {
      File experimentDirectory = new File(Play.application().path().toString() + "/dev/" + getDirectoryName());
      if (this.fileMode) {
        // Turning fileMode off, let's import the files into the current experiment
        ExperimentController.importExperimentFromDirectory(this.id, user, experimentDirectory);
      } else {
        // Turning fileMode on, let's export the experiment into the appropriate directory
        ExperimentController.exportExperimentToDirectory(this.id, experimentDirectory);
      }
      this.setFileMode(!this.fileMode);
      this.save();
    } catch (IOException io) {
      Logger.error("Unable to access the experiment directory, check your file permissions.");
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

    File viewsDirectory = new File(experimentDirectory, "Views");
    for (ExperimentView experimentView : this.getExperimentViews()) {
      FileUtils.writeStringToFile(new File(viewsDirectory, experimentView.fileName), experimentView.content);
    }

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
    if (this.fileMode != null && this.fileMode) {
      ArrayList<Parameter> returnParameters = new ArrayList<>();
      File parameterFile = new File(Play.application().path().toString() + "/dev/" + getDirectoryName() + "/parameters.csv");
      try {
        returnParameters = ExperimentController.getParametersFromFile(parameterFile);
      } catch (IOException ioe) {
        Logger.error("Error reading steps file from the dev directory, check your permissions.");
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

    ArrayNode jsonViews = experiment.putArray("views");
    for (ExperimentView ev : this.getExperimentViews()) {
      jsonViews.add(ev.toJson());
    }

    return experiment;
  }

  public String toString() {
    return "Experiment(" + id + ")";
  }
}

