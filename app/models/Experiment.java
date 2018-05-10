package models;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.annotation.ConcurrencyMode;
import com.avaje.ebean.annotation.EntityConcurrencyMode;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import play.Logger;
import play.Play;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.libs.Json;

import javax.persistence.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
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
  public List<Step> steps = new ArrayList<Step>();

  @OneToMany(cascade = CascadeType.ALL)
  public List<Content> content = new ArrayList<Content>();

  @OneToMany(cascade = CascadeType.ALL)
  public List<Parameter> parameters = new ArrayList<Parameter>();

  @JsonIgnore
  public ContentFetcher contentFetcher = new ContentFetcher(this);

  @OneToMany(cascade = CascadeType.ALL)
  public List<ExperimentInstance> instances = new ArrayList<ExperimentInstance>();

  @OneToMany(cascade = CascadeType.ALL)
  public List<Image> images = new ArrayList<Image>();

  @ManyToMany
  @JoinTable(name = "experiments_languages")
  public List<Language> languages = new ArrayList<Language>();

  public static final Long TEST_INSTANCE_ID = 0L;
  public ExperimentInstance TEST_INSTANCE = null;

  // The AMT QualificationTypeId for the Previous Worker qualification specific to this experiment type.
  public String qualificationTypeId;

  // QualificationTypeId in the AMT Sandbox
  public String qualificationTypeIdSandbox;

  public static final String ON_JOIN_STEP_NAME = "OnJoinStep";
  public static final String ON_LEAVE_STEP_NAME = "OnLeaveStep";

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
    this.style = experiment.style;
    this.clientHtml = experiment.clientHtml;
    this.clientGraph = experiment.clientGraph;

    for (Step step : experiment.steps) {
      this.steps.add(new Step(step));
    }
    for (Content c : experiment.content) {
      this.content.add(new Content(c));
    }
    for (Parameter param : experiment.parameters) {
      this.parameters.add(new Parameter(param));
    }
    for (Image image : experiment.images) {
      this.images.add(new Image(image));
    }
    for(Language language : experiment.languages) {
      this.languages.add(language);
    }
  }

  public void export() throws IOException {
    File experimentDirectory = new File(Play.application().path().toString() + "/experiments/" + this.name);
    FileUtils.writeStringToFile(new File(experimentDirectory, "style.css"), this.style);
    FileUtils.writeStringToFile(new File(experimentDirectory, "client.html"), this.clientHtml);
    FileUtils.writeStringToFile(new File(experimentDirectory, "client-graph.js"), this.clientGraph);

    File stepsDirectory = new File(experimentDirectory, "/Steps");
    for (Step step : this.steps) {
      FileUtils.writeStringToFile(new File(stepsDirectory, step.name.concat(".groovy")), step.source);
    }

    File contentDirectory = new File(experimentDirectory, "/Content");
    for (Content c : this.content) {
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
    for (Parameter param : this.parameters) {
      FileUtils.writeStringToFile(parametersFile, param.name + "," + param.type + "," + param.minVal + "," + param.maxVal + "," + param.defaultVal + "," + param.description + ls, true);
    }

    File imagesDirectory = new File(experimentDirectory, "/Images");
    for (Image image : this.images) {
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
    } catch(IOException e){}
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
    } catch(IOException e){}
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

  public Content getContent(Long id) {
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

  public Step getStep(Long id) {
    for (Step s : steps) {
      if (s.id.equals(id))
        return s;
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
    return parameters;
  }

  public boolean hasOnJoinStep() {
    return getOnJoinStep() != null;
  }

  public boolean hasOnLeaveStep() {
    return getOnLeaveStep() != null;
  }

  public Step getOnJoinStep() {
    for (Step step : steps) {
      if (ON_JOIN_STEP_NAME.equalsIgnoreCase(step.name)) {
        return step;
      }
    }
    return null;
  }

  public Step getOnLeaveStep() {
    for (Step step : steps) {
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

    ArrayNode jsonSteps = experiment.putArray("steps");
    for (Step s : steps) {
      jsonSteps.add(s.toJson());
    }

    ArrayNode jsonLanguages = experiment.putArray("languages");
    for (Language l : languages) {
      jsonLanguages.add(l.toJson());
    }

    ArrayNode jsonContent = experiment.putArray("content");
    for (Content c : content) {
      jsonContent.add(c.toJson());
    }

    ArrayNode jsonParameters = experiment.putArray("parameters");
    for (Parameter p : parameters) {
      jsonParameters.add(p.toJson());
    }

    ArrayNode jsonInstances = experiment.putArray("instances");
    for (ExperimentInstance ei : instances) {
      // Only return the name and ID of the instances
      // TODO: Perhaps add the Date/Time of the instance here as well
      jsonInstances.add(ei.toJsonStub());
    }

    ArrayNode jsonImages = experiment.putArray("images");
    for (Image i : images) {
      jsonImages.add(i.toJson());
    }

    experiment.put("style", style);
    /*
    experiment.put("clientHtml", clientHtml);
    experiment.put("clientGraph", clientGraph);
    */

    return experiment;
  }

  public String toString() {
    return "Experiment(" + id + ")";
  }
}
