package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.libs.Json;

import javax.persistence.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Table(name = "events")
public class Event extends Model {
  @Id
  public Long id;

  @Constraints.Required
  @Formats.NonEmpty
  public Date datetime;

  @Constraints.Required
  @Formats.NonEmpty
  public String name;

  @JsonIgnore
  @ManyToOne
  public ExperimentInstance experimentInstance;

  @OneToMany(cascade = CascadeType.ALL, mappedBy = "event")
  public List<EventData> eventData;

  @JsonIgnore
  public static Model.Finder<Long, Event> find = new Model.Finder(Long.class, Event.class);

  public Event() {
    datetime = new Date();
    eventData = new ArrayList<EventData>();
  }

  public static List<Event> findAll() {
    return find.all();
  }

  @Override
  public void delete() {
    for (EventData ed : eventData) {
      ed.delete();
    }
    eventData.clear();
    super.delete();
  }

  public void addEventData(EventData eventData) {
    eventData.event = this;
    this.eventData.add(eventData);
  }

  public ObjectNode toJson() {
    ObjectNode event = Json.newObject();
    event.put("id", id);
    event.put("name", name);
    event.put("datetime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,S").format(datetime));

    ArrayNode jsonEventData = event.putArray("eventData");
    for (EventData ed : eventData) {
      jsonEventData.add(ed.toJson());
    }

    return event;
  }

  public String toString() {
    return "Event(" + id + ")";
  }
}
