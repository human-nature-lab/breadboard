package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
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
@Table(name="steps")
public class Step extends Model
{
	@Id
	public Long id;

	@Constraints.Required
	@Formats.NonEmpty
	public String name;

    @Column(columnDefinition="text")
	public String source;

	@JsonIgnore
	public static Model.Finder<Long, Step> find = new Model.Finder(Long.class, Step.class);

	public static List<Step> findAll()
	{
		return find.all();
	}

    public Step() {
    }

    public Step(Step step) {
        this.name = step.name;
        this.source = step.source;
    }

    public void setSource(String source)
	{
	    this.source = source;
	}

	public static Step findByName(String name)
	{
		return find.where().eq("name", name).findUnique();
	}

	@JsonValue
    public ObjectNode toJson()
    {
        ObjectNode step = Json.newObject();
        step.put("id", id);
        step.put("name", name);
        step.put("source", source);
        return step;
    }

	public String toString()
	{
		return "Step(" + id + ")";
	}
}
