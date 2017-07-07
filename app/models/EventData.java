package models;

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
@Table(name = "event_data")
public class EventData extends Model {
  @Id
  public Long id;

  @Constraints.Required
  @Formats.NonEmpty
  public String name;

  public String value;

  @ManyToOne
  public Event event;

  public static Model.Finder<Long, EventData> find = new Model.Finder(Long.class, EventData.class);

  public static List<EventData> findAll() {
    return find.all();
  }

  public String valueToCSV() {
    return "\"" + value.replace("\"", "\"\"") + "\"";
  }

  public ObjectNode toJson() {
    ObjectNode eventData = Json.newObject();
    eventData.put("id", id);
    eventData.put("name", name);
    eventData.put("value", value);
    return eventData;
  }

  public String toString() {
    return "EventData(" + id + ")";
  }
}
