package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.libs.Json;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "content")
public class Content extends Model {
  @Id
  public Long id;

  @Constraints.Required
  @Formats.NonEmpty
  public String name;

  @OneToMany(mappedBy="content", cascade = CascadeType.ALL)
  public List<Translation> translations = new ArrayList<>();

  // Removed in v2.3.0 kept for migration purposes
  public String html;

  @JsonIgnore
  public static Model.Finder<Long, Content> find = new Model.Finder(Long.class, Content.class);

  public static List<Content> findAll() {
    return find.all();
  }

  @JsonIgnore
  public static Content findById(Long id) {
    return find.where().eq("id", id).findUnique();
  }

  public static Content findByName(String name) {
    return find.where().eq("name", name).findUnique();
  }

  public Content() {
  }

  public Content(Content c) {
    this.name = c.name;
    for (Translation t : c.translations) {
      this.translations.add(new Translation(t));
    }
  }

  public String toString() {
    return "Content(" + name + ")";
  }

  public ObjectNode toJson() {
    ObjectNode content = Json.newObject();
    content.put("id", id);
    content.put("name", name);
    ArrayNode jsonTranslations = content.putArray("translations");
    for (Translation t : translations) {
      jsonTranslations.add(t.toJson());
    }
    return content;
  }

}
