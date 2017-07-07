package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.libs.Json;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.List;

@Entity
@Table(name = "content")
public class Content extends Model {
  @Id
  public Long id;

  @Constraints.Required
  @Formats.NonEmpty
  public String name;

  @Constraints.Required
  @Column(columnDefinition = "text")
  public String html;

  @JsonIgnore
  public static Model.Finder<Long, Content> find = new Model.Finder(Long.class, Content.class);

  public static List<Content> findAll() {
    return find.all();
  }

  public Content() {
  }

  public Content(Content content) {
    this.name = content.name;
    this.html = content.html;
  }

  public String toString() {
    return "Content(" + name + ")";
  }

  public void setHtml(String html) {
    this.html = html;
  }

  public ObjectNode toJson() {
    ObjectNode content = Json.newObject();
    content.put("id", id);
    content.put("name", name);
    content.put("html", html);
    return content;
  }

  public static Content findByName(String name) {
    return find.where().eq("name", name).findUnique();
  }
}
