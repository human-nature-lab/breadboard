package models;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonValue;
import play.Logger;
import play.libs.Json;

import org.codehaus.jackson.node.ObjectNode;

import java.util.*;
import javax.persistence.*;

import play.db.ebean.*;
import play.data.format.*;
import play.data.validation.*;

@Entity
@Table(name="users")
public class User extends Model
{
    @Id
    @Constraints.Required
    @Formats.NonEmpty
    public String email;

    @Constraints.Required
    public String name;

    @Constraints.Required
    public String password;

    public String uid;

    /*
     * Currently role can be admin or amt_admin, should expand this to include experiment runners vs. 
     * experiment designers.
     */
    public String role;

    @ManyToMany
    public List<Experiment> ownedExperiments = new ArrayList<Experiment>();

    @OneToOne
    public Experiment selectedExperiment;

    @Column(columnDefinition="text")
    public String currentScript;

    public Long experimentInstanceId = -1L;

    @JsonIgnore
    public static Model.Finder<String, User> find = new Model.Finder(String.class, User.class);

    public static List<User> findAll()
    {
        return find.all();
    }

    public static User findByEmail(String email)
    {
        return find.where().eq("email", email).findUnique();
    }

    public static User findByUID(String uid)
    {
        return find.where().eq("uid", uid).findUnique();
    }

    public static User authenticate(String email, String password)
    {
    	Logger.debug("authenticate(" + email + ", " + password + ")");
        return find.where()
            .eq("email", email)
            .eq("password", password)
            .findUnique();
    }

    public void setExperimentInstanceId(Long experimentInstanceId)
    {
        this.experimentInstanceId = experimentInstanceId;
    }
    
    public void setSelectedExperiment(Experiment selectedExperiment)
    {
        this.selectedExperiment = selectedExperiment;
    }

    public Experiment getExperiment()
    {
        return selectedExperiment;
    }

    public Experiment getExperimentByName(String name)
    {
        for (Experiment e : ownedExperiments)
        {
            if (e.name.equals(name))
                return e;
        }   
        return null;
    }

    @JsonValue
    public ObjectNode toJson()
    {
        ObjectNode breadboard = Json.newObject();
        ObjectNode user = Json.newObject();

        ArrayList<String> experimentNames = new ArrayList<String>();

        for (Experiment experiment : ownedExperiments)
        {
            experimentNames.add(experiment.name);
        }

        user.put("email", this.email);

        user.put("experiments", Json.toJson(experimentNames));

        if (selectedExperiment != null)
        {
            user.put("selectedExperiment", selectedExperiment.name);
        }
        else
        {
            Logger.debug("selectedExperiment == null?");
        }

        user.put("currentScript", currentScript);

        breadboard.put("user", user);

        if (selectedExperiment != null)
        {
            breadboard.put("experiment", selectedExperiment.toJson());
        }

        if (experimentInstanceId == Experiment.TEST_INSTANCE_ID) {
            breadboard.put("experimentInstance", selectedExperiment.getTestInstance().toJson());
        }
        else if (experimentInstanceId != -1L && experimentInstanceId != null)
        {
            ExperimentInstance ei = ExperimentInstance.findById(experimentInstanceId);
            Logger.debug("experimentInstanceId = " + experimentInstanceId.toString() + (ei == null ? " is null" : ""));
            if (ei != null) {
                breadboard.put("experimentInstance", ei.toJson());
            } else {
            	experimentInstanceId = -1L;
            	breadboard.put("experimentInstance", "");
            }
        }
        else
        {
            breadboard.put("experimentInstance", "");
        }

        return breadboard;
    }

    public String toString()
    {
        return "User(" + email + ")";
    }
}
