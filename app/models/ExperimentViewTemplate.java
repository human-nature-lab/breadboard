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
@Table(name = "experiment_view_template")
public class ExperimentViewTemplate extends Model {
  @Id
  public Long id;
  public String name;
  public String fileName;
  public String content;

  @JsonIgnore
  @ManyToOne
  public ExperimentView experimentView;

  @JsonIgnore
  public static Finder<String, ExperimentViewTemplate> find = new Finder(String.class, ExperimentViewTemplate.class);

  public static List<ExperimentViewTemplate> findAll() {
    return find.all();
  }

  public static ExperimentViewTemplate findById(Long id) {
    return find.where().eq("id", id).findUnique();
  }

  public ExperimentViewTemplate() {}

  public ExperimentViewTemplate(ExperimentViewTemplate evs) {
    this.name = evs.name;
    this.fileName = evs.fileName;
    this.content = evs.content;
    this.experimentView = evs.experimentView;
  }

  public ObjectNode toJson() {
    ObjectNode customFile = Json.newObject();
    customFile.put("id", id);
    customFile.put("name", name);
    customFile.put("fileName", fileName);
    customFile.put("content", content); // TODO: only return content when tab is clicked on
    return customFile;
  }

  public String toString() {
    return "ExperimentViewTemplate(" + id + ")";
  }
}
