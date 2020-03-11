package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.db.ebean.Model;
import play.libs.Json;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.List;

@Entity
@Table(name = "experiment_view_script")
public class ExperimentViewScript extends Model {
  @Id
  public Long id;
  public String name;
  public String fileName;
  public String script;

  @JsonIgnore
  @ManyToOne
  public ExperimentView experimentView;

  @JsonIgnore
  public static Finder<String, ExperimentViewScript> find = new Finder(String.class, ExperimentViewScript.class);

  public static List<ExperimentViewScript> findAll() {
    return find.all();
  }

  public static ExperimentViewScript findById(Long id) {
    return find.where().eq("id", id).findUnique();
  }

  public ExperimentViewScript() {}

  public ExperimentViewScript(ExperimentViewScript evs) {
    this.name = evs.name;
    this.fileName = evs.fileName;
    this.script = evs.script;
    this.experimentView = evs.experimentView;
  }

  public ObjectNode toJson() {
    ObjectNode customFile = Json.newObject();
    customFile.put("id", id);
    customFile.put("name", name);
    customFile.put("fileName", fileName);
    customFile.put("script", script); // TODO: only return script when tab is clicked on
    return customFile;
  }

  public String toString() {
    return "ExperimentViewScript(" + id + ")";
  }
}
