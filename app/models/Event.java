package models;

import org.codehaus.jackson.annotate.JsonIgnore;
import play.Logger;
import play.libs.Json;

import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.node.ArrayNode;

import java.text.SimpleDateFormat;
import java.util.*;
import javax.persistence.*;

import play.db.ebean.*;
import play.data.format.*;
import play.data.validation.*;

import com.avaje.ebean.*;

@Entity
@Table(name="events")
public class Event extends Model
{
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

    @OneToMany(cascade=CascadeType.ALL, mappedBy = "event")
	public List<EventData> eventData;

    @JsonIgnore
	public static Model.Finder<Long, Event> find = new Model.Finder(Long.class, Event.class);

    public Event() {
        datetime = new Date();
        eventData = new ArrayList<EventData>();
    }

	public static List<Event> findAll()
	{
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

    public ObjectNode toJson()
    {
        ObjectNode event = Json.newObject();
        event.put("id", id);
        event.put("name", name);
        event.put("datetime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,S").format(datetime));

        ArrayNode jsonEventData = event.putArray("eventData"); 
        for (EventData ed : eventData)
        {
            jsonEventData.add(ed.toJson());
        }

        return event;
    }

	public String toString()
	{
		return "Event(" + id + ")";
	}
}
