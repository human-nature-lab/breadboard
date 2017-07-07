package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.libs.Json;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.List;

@Entity
@Table(name = "data")
public class Data extends Model {
  @Id
  public Long id;

  @Constraints.Required
  @Formats.NonEmpty
  public String name;

  public String value;

  @ManyToOne
  @JsonIgnore
  public ExperimentInstance experimentInstance;

  @JsonIgnore
  public static Model.Finder<Long, Data> find = new Model.Finder(Long.class, Data.class);

  public static List<Data> findAll() {
    return find.all();
  }

  public ObjectNode toJson() {
    ObjectNode data = Json.newObject();
    data.put("id", id);
    data.put("name", name);
    data.put("value", value);
    return data;
  }

  public String toString() {
    return "Data(" + id + ")";
  }
}
