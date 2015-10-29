package models;

import play.Logger;
import play.libs.Json;

import org.codehaus.jackson.node.ObjectNode;

import java.util.*;
import javax.persistence.*;

import play.db.ebean.*;
import play.data.format.*;
import play.data.validation.*;

import com.avaje.ebean.*;

@Entity
@Table(name="event_data")
public class EventData extends Model
{
	@Id
	public Long id;

	@Constraints.Required
	@Formats.NonEmpty
	public String name;

	public String value;

	@ManyToOne
	public Event event;

	public static Model.Finder<Long, EventData> find = new Model.Finder(Long.class, EventData.class);

	public static List<EventData> findAll()
	{
		return find.all();
	}

	public String valueToCSV()
	{
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

    public ObjectNode toJson()
    {
        ObjectNode eventData = Json.newObject();
        eventData.put("id", id);
        eventData.put("name", name);
        eventData.put("value", value);
        return eventData;
    }

	public String toString()
	{
		return "EventData(" + id + ")";
	}
}
