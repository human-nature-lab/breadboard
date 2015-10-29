package models;

import org.codehaus.jackson.annotate.JsonIgnore;
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
@Table(name="parameters")
public class Parameter extends Model
{
    @Id
    public Long id;

    @Constraints.Required
    @Formats.NonEmpty
    public String name;

    public String type;

    public String minVal;
    public String maxVal;

    public String defaultVal;

    public String description; 

    @JsonIgnore
    public static Model.Finder<String, Parameter> find = new Model.Finder(String.class, Parameter.class);

    public static List<Parameter> findAll()
    {
        return find.all();
    }

    public Parameter() {
    }

    public Parameter(Parameter parameter) {
        this.name = parameter.name;
        this.type = parameter.type;
        this.minVal = parameter.minVal;
        this.maxVal = parameter.maxVal;
        this.defaultVal = parameter.defaultVal;
        this.description = parameter.description;
    }

    public ObjectNode toJson()
    {
        ObjectNode parameter = Json.newObject();
        parameter.put("id", id);
        parameter.put("name", name);
        parameter.put("type", type);
        parameter.put("minVal", minVal);
        parameter.put("maxVal", maxVal);
        parameter.put("defaultVal", defaultVal);
        parameter.put("description", description);
        return parameter;
    }

    public String toString()
    {
        return "Parameter(" + id + ")";
    }
}
