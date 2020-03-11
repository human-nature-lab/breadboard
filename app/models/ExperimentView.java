package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.io.FileUtils;
import play.db.ebean.Model;
import play.libs.Json;

import javax.persistence.*;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "experiment_view")
public class ExperimentView extends Model {
  @Id
  public Long id;
  public String view;
  public String title;
  public String fileName;
  public String content;
  public String style;
  @OneToMany(cascade = CascadeType.ALL)
  public List<ExperimentViewDependency> dependencies = new ArrayList<>();
  @OneToMany(cascade = CascadeType.ALL)
  public List<ExperimentViewScript> scripts = new ArrayList<>();
  @OneToMany(cascade = CascadeType.ALL)
  public List<ExperimentViewTemplate> templates = new ArrayList<> ();

  @JsonIgnore
  @ManyToOne
  public Experiment experiment;

  @JsonIgnore
  public static Finder<String, ExperimentView> find = new Finder(String.class, ExperimentView.class);

  public static List<ExperimentView> findAll() {
    return find.all();
  }

  public static ExperimentView findById(Long id) {
    return find.where().eq("id", id).findUnique();
  }

  public ExperimentView() {}

  public ExperimentView(ExperimentView ev) {
    this.view = ev.view;
    this.title = ev.title;
    this.fileName = ev.fileName;
    for (ExperimentViewDependency d : ev.dependencies) {
      this.dependencies.add(new ExperimentViewDependency(d));
    }
    for (ExperimentViewScript s : ev.scripts) {
      this.scripts.add(new ExperimentViewScript(s));
    }
    for (ExperimentViewTemplate t : ev.templates) {
      this.templates.add(new ExperimentViewTemplate(t));
    }
  }

  public static ExperimentView getExperimentViewFromDirectory(File dir) throws IOException, JsonSyntaxException {
    String fileName = dir.getName().concat(".json");
    File jsonFile = new File(dir, fileName);
    if (!jsonFile.exists()) {
      throw new IOException("File name ".concat(fileName).concat(" does not exist."));
    }
    String json = FileUtils.readFileToString(jsonFile);

    ExperimentView evFromJson = new Gson().fromJson(json, ExperimentView.class);
    ExperimentView ev = new ExperimentView(evFromJson);

    if (ev.fileName != null && ev.content == null) {
      File contentFile = new File(dir, ev.fileName);
      if (!contentFile.exists()) {
        throw new IOException("File name ".concat(ev.fileName).concat(" does not exist."));
      }
      ev.content = FileUtils.readFileToString(contentFile);
    }

    if (ev.scripts != null) {
      for (ExperimentViewScript evs : ev.scripts) {
        if (evs.fileName != null && evs.script == null) {
          File scriptFile = new File(dir, evs.fileName);
          if (!scriptFile.exists()) {
            throw new IOException("File name ".concat(evs.fileName).concat(" does not exist."));
          }
          evs.script = FileUtils.readFileToString(scriptFile);
        }
      }
    }

    if (ev.templates != null) {
      for (ExperimentViewTemplate evt : ev.templates) {
        if (evt.fileName != null && evt.content == null) {
          File templateFile = new File(dir, evt.fileName);
          if (!templateFile.exists()) {
            throw new IOException("File name ".concat(evt.fileName).concat(" does not exist."));
          }
          evt.content = FileUtils.readFileToString(templateFile);
        }
      }
    }

    return ev;
  }

  public List<ExperimentViewDependency> getHeadDependencies() {
    return ExperimentViewDependency.find
        .where()
        .eq("position", "head")
        .findList();
  }

  public List<ExperimentViewDependency> getBodyDependencies() {
    return ExperimentViewDependency.find
        .where()
        .eq("position", "body")
        .findList();
  }

  public ObjectNode toJson() {
    ObjectNode experimentView = Json.newObject();
    experimentView.put("id", id);
    experimentView.put("view", view);
    experimentView.put("fileName", fileName);
    ArrayNode jsonDependencies = experimentView.putArray("dependencies");
    for (ExperimentViewDependency dependency : dependencies) {
      jsonDependencies.add(dependency.toJson());
    }
    ArrayNode jsonScripts = experimentView.putArray("scripts");
    for (ExperimentViewScript script : scripts) {
      jsonScripts.add(script.toJson());
    }
    ArrayNode jsonTemplates = experimentView.putArray("templates");
    for (ExperimentViewTemplate template : templates) {
      jsonTemplates.add(template.toJson());
    }
    experimentView.put("content", content);
    experimentView.put("style", style);
    return experimentView;
  }

  public String toString() {
    return "ExperimentView(" + id + ") \n" +
        "id: " + id + "\n" +
        "view: " + view + "\n" +
        "fileName: " + fileName + "\n" +
        "dependencies: " + dependencies.toString() + "\n" +
        "scripts: " + scripts.toString() + "\n" +
        "templates: " + templates.toString();
  }
}
