package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.db.ebean.Model;
import play.libs.Json;

import javax.persistence.*;
import java.util.List;

@Entity
@Table(name = "translations")
public class Translation extends Model {
  @Id
  public Long id;

  public String html;

  @ManyToOne
  @JsonIgnore
  public Content content;

  @ManyToOne
  @JoinColumn(name = "languages_id")
  public Language language;

  @JsonIgnore
  public static Finder<Long, Translation> find = new Finder(Long.class, Translation.class);

  public static List<Translation> findAll() {
    return find.all();
  }

  public ObjectNode toJson() {
    ObjectNode translation = Json.newObject();
    translation.put("id", id);
    translation.put("html", html);
    translation.put("language", language.toJson());
    return translation;
  }

  public String toString() {
    return "Translation(" + id + ")";
  }
}
