package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.db.ebean.Model;
import play.libs.Json;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "languages")
public class Language extends Model {
  @Id
  public Long id;

  public String code;

  public String name;

  /*
  @JsonIgnore
  @ManyToMany
  @JoinTable(name = "experiments_languages")
  public List<Experiment> experiments = new ArrayList<Experiment>();
  */

  @JsonIgnore
  public static Finder<Long, Language> find = new Finder(Long.class, Language.class);

  public static List<Language> findAll() {
    return find.all();
  }

  public Language() {}

  public ObjectNode toJson() {
    ObjectNode language = Json.newObject();
    language.put("id", id);
    language.put("code", code);
    language.put("name", name);
    return language;
  }

  public String toString() {
    return "Language(" + id + ")";
  }
}
