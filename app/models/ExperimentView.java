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
  @OneToMany(cascade = CascadeType.ALL)
  public List<ExperimentViewContent> content = new ArrayList<>();

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
    for (ExperimentViewContent c : ev.content) {
      this.content.add(new ExperimentViewContent(c));
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

    if (ev.content != null) {
      for (ExperimentViewContent evc : ev.content) {
        if (evc.fileName != null && evc.content == null) {
          File scriptFile = new File(dir, evc.fileName);
          if (!scriptFile.exists()) {
            throw new IOException("File name ".concat(evc.fileName).concat(" does not exist."));
          }
          evc.content = FileUtils.readFileToString(scriptFile);
        }
      }
    }

    return ev;
  }

  public List<ExperimentViewContent> getHeadDependencies() {
    return ExperimentViewContent.find
        .where()
        .eq("experiment_view_id", this.id)
        .eq("type", "head-dependency")
        .orderBy("load_order")
        .findList();
  }

  public List<ExperimentViewContent> getBodyDependencies() {
    return ExperimentViewContent.find
        .where()
        .eq("experiment_view_id", this.id)
        .eq("type", "body-dependency")
        .orderBy("load_order")
        .findList();
  }

  public List<ExperimentViewContent> getTemplates() {
    return ExperimentViewContent.find
        .where()
        .eq("experiment_view_id", this.id)
        .eq("type", "template")
        .orderBy("load_order")
        .findList();
  }

  public List<ExperimentViewContent> getScripts() {
    return ExperimentViewContent.find
        .where()
        .eq("experiment_view_id", this.id)
        .eq("type", "script")
        .orderBy("load_order")
        .findList();
  }

  public List<ExperimentViewContent> getContent() {
    return ExperimentViewContent.find
        .where()
        .eq("experiment_view_id", this.id)
        .eq("type", "content")
        .orderBy("load_order")
        .findList();
  }

  public List<ExperimentViewContent> getStyle() {
    return ExperimentViewContent.find
        .where()
        .eq("experiment_view_id", this.id)
        .eq("type", "style")
        .orderBy("load_order")
        .findList();
  }

  public ObjectNode toJson() {
    ObjectNode experimentView = Json.newObject();
    experimentView.put("id", id);
    experimentView.put("view", view);
    ArrayNode jsonHeadDependencies = experimentView.putArray("head-dependencies");
    for (ExperimentViewContent dependency : getHeadDependencies()) {
      jsonHeadDependencies.add(dependency.toJson());
    }
    ArrayNode jsonBodyDependencies = experimentView.putArray("body-dependencies");
    for (ExperimentViewContent dependency : getBodyDependencies()) {
      jsonBodyDependencies.add(dependency.toJson());
    }
    ArrayNode jsonScripts = experimentView.putArray("scripts");
    for (ExperimentViewContent script : getScripts()) {
      jsonScripts.add(script.toJson());
    }
    ArrayNode jsonTemplates = experimentView.putArray("templates");
    for (ExperimentViewContent template : getTemplates()) {
      jsonTemplates.add(template.toJson());
    }
    ArrayNode jsonContent = experimentView.putArray("content");
    for (ExperimentViewContent content : getContent()) {
      jsonContent.add(content.toJson());
    }
    ArrayNode jsonStyle = experimentView.putArray("style");
    for (ExperimentViewContent style : getStyle()) {
      jsonStyle.add(style.toJson());
    }
    return experimentView;
  }

  public String toString() {
    return "ExperimentView(" + id + ")";
  }
}
