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
@Table(name = "experiment_view_content")
public class ExperimentViewContent extends Model {
  @Id
  public Long id;
  public String name;
  public String fileName;
  public String type;
  public String content;
  public Integer loadOrder;

  @JsonIgnore
  @ManyToOne
  public ExperimentView experimentView;

  @JsonIgnore
  public static Finder<String, ExperimentViewContent> find = new Finder(String.class, ExperimentViewContent.class);

  public static List<ExperimentViewContent> findAll() {
    return find.all();
  }

  public static ExperimentViewContent findById(Long id) {
    return find.where().eq("id", id).findUnique();
  }

  public ExperimentViewContent() {}

  public ExperimentViewContent(ExperimentViewContent evc) {
    this.name = evc.name;
    this.fileName = evc.fileName;
    this.type = evc.type;
    this.content = evc.content;
    this.loadOrder = evc.loadOrder;
    this.experimentView = evc.experimentView;
  }

  public ObjectNode toJson() {
    ObjectNode customFile = Json.newObject();
    customFile.put("id", id);
    customFile.put("name", name);
    customFile.put("fileName", fileName);
    customFile.put("type", type);
    customFile.put("content", content); // TODO: only return script when tab is clicked on
    customFile.put("loadOrder", loadOrder); // TODO: only return script when tab is clicked on
    return customFile;
  }

  public String toString() {
    return "ExperimentViewContent(" + id + ")";
  }
}
