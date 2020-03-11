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
@Table(name = "experiment_view_dependency")
public class ExperimentViewDependency extends Model {
  @Id
  public Long id;
  public String content;
  public String position;

  @JsonIgnore
  @ManyToOne
  public ExperimentView experimentView;

  @JsonIgnore
  public static Finder<String, ExperimentViewDependency> find = new Finder(String.class, ExperimentViewDependency.class);

  public static List<ExperimentViewDependency> findAll() {
    return find.all();
  }

  public static ExperimentViewDependency findById(Long id) {
    return find.where().eq("id", id).findUnique();
  }

  public ExperimentViewDependency() {}

  public ExperimentViewDependency(ExperimentViewDependency evs) {
    this.content = evs.content;
    this.experimentView = evs.experimentView;
    this.position = evs.position;
  }

  public ObjectNode toJson() {
    ObjectNode customFile = Json.newObject();
    customFile.put("id", id);
    customFile.put("content", content); // TODO: only return content when tab is clicked on
    customFile.put("position", position); // TODO: only return content when tab is clicked on
    return customFile;
  }

  public String toString() {
    return "ExperimentViewDependency(" + id + ")";
  }
}
