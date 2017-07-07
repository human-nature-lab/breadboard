package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import play.Logger;
import play.libs.Json;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.*;
import javax.persistence.*;

import play.db.ebean.*;
import play.data.format.*;
import play.data.validation.*;

import com.avaje.ebean.*;

@Entity
@Table(name="data")
public class Data extends Model
{
	@Id
	public Long id;

	@Constraints.Required
	@Formats.NonEmpty
	public String name;

	public String value;

    @ManyToOne
	@JsonIgnore
    public ExperimentInstance experimentInstance;

	@JsonIgnore
	public static Model.Finder<Long, Data> find = new Model.Finder(Long.class, Data.class);

	public static List<Data> findAll()
	{
		return find.all();
	}

    public ObjectNode toJson()
    {
        ObjectNode data = Json.newObject();
        data.put("id", id);
        data.put("name", name);
        data.put("value", value);
        return data;
    }

	public String toString()
	{
		return "Data(" + id + ")";
	}
}
