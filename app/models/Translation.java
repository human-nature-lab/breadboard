package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.db.ebean.Model;
import play.libs.Json;

import javax.persistence.*;
import java.io.Serializable;
import java.util.List;

@Entity
@Table(name = "translations")
public class Translation extends Model implements Serializable {
  @Id
  public Long id;

  public String html;

  @ManyToOne
  @JsonIgnore
  public Content content;

  @ManyToOne
  @JoinColumn(name = "languages_id")
  @JsonProperty("language")
  public Language language;

  @JsonIgnore
  public static Finder<Long, Translation> find = new Finder(Long.class, Translation.class);

  @JsonIgnore
  public static List<Translation> findAll() {
    return find.all();
  }

  @JsonIgnore
  public static Translation findById(Long id) {
    return find.where().eq("id", id).findUnique();
  }

  public Translation() {}

  public Translation(Translation t) {
    this.html = t.html;
    this.language = t.language;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getHtml() {
    return html;
  }

  public void setHtml(String html) {
    this.html = html;
  }

  public Content getContent() {
    return content;
  }

  public void setContent(Content content) {
    this.content = content;
  }

  public Language getLanguage() {
    return language;
  }

  public void setLanguage(Language language) {
    this.language = language;
  }

  @JsonIgnore
  public ObjectNode toJson() {
    ObjectNode translation = Json.newObject();
    translation.put("id", id);
    translation.put("html", html);
    translation.put("language", language.toJson());
    return translation;
  }

  @JsonIgnore
  public String toString() {
    return "Translation(" + id + ")";
  }
}
