package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.libs.Json;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.List;

@Entity
@Table(name = "parameters")
public class Parameter extends Model {
  @Id
  public Long id;

  @Constraints.Required
  @Formats.NonEmpty
  public String name;

  public String type;

  public String minVal;
  public String maxVal;

  public String defaultVal;

  public String description;

  @JsonIgnore
  public static Model.Finder<String, Parameter> find = new Model.Finder(String.class, Parameter.class);

  public static List<Parameter> findAll() {
    return find.all();
  }

  public Parameter() {
  }

  public Parameter(Parameter parameter) {
    this.name = parameter.name;
    this.type = parameter.type;
    this.minVal = parameter.minVal;
    this.maxVal = parameter.maxVal;
    this.defaultVal = parameter.defaultVal;
    this.description = parameter.description;
  }

  public ObjectNode toJson() {
    ObjectNode parameter = Json.newObject();
    parameter.put("id", id);
    parameter.put("name", name);
    parameter.put("type", type);
    parameter.put("minVal", minVal);
    parameter.put("maxVal", maxVal);
    parameter.put("defaultVal", defaultVal);
    parameter.put("description", description);
    return parameter;
  }

  public String toString() {
    return "Parameter(" + id + ")";
  }
}
