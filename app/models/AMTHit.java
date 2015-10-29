package models;

import org.codehaus.jackson.annotate.JsonIgnore;
import play.Logger;
import play.libs.Json;

import org.codehaus.jackson.node.ObjectNode;
import org.codehaus.jackson.node.ArrayNode;

import java.util.*;
import javax.persistence.*;
import java.text.SimpleDateFormat;

import play.db.ebean.*;
import play.data.format.*;
import play.data.validation.*;

import com.avaje.ebean.*;

@Entity
@Table(name = "amt_hits")
public class AMTHit extends Model {
	@Version
	public int version;

    @Id
    public Long id;

    @Constraints.Required
    @Formats.NonEmpty
    public Date creationDate;

	public String requestId;
	public String isValid;
	public String hitId;
	public String title;
	public String description;
	public String lifetimeInSeconds;
    public String tutorialTime;
	public String maxAssignments;
	public String externalURL;
	public String reward;
	public String disallowPrevious;
	public Boolean sandbox;
	// Was this HIT extended in the case where the workers were unable to submit their assignments?
	private Boolean extended;

    @JsonIgnore
    @ManyToOne
	public ExperimentInstance experimentInstance;

	// TODO: Remove amtWorkers
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "amtHit")
    public List<AMTWorker> amtWorkers = new ArrayList<AMTWorker>();

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "amtHit")
    public List<AMTAssignment> amtAssignments = new ArrayList<AMTAssignment>();

    @JsonIgnore
    public static Model.Finder<Long, AMTHit> find = new Model.Finder(Long.class, AMTHit.class);

    public static List<AMTHit> findAll() {
        return find.all();
    }

    public static AMTHit findById(Long id) {
        return find.where().eq("id", id).findUnique();
    }

    public static AMTHit findByHitId(String hitId) {
        return find.where().eq("hit_id", hitId).findUnique();
    }

    public AMTHit() {
        this.creationDate = new Date();
        this.extended = false;
    }

    public Boolean isExtended() {
    	if (extended == null) {
    		return false;
    	}
    	return extended;
    }

    public Boolean hasWorker(String workerId) {
      for (AMTWorker w : amtWorkers) {
        if (w.workerId.equals(workerId)) {
          return true;
        }
      }
      return false;
    }

    public void setExtended(Boolean extended) {
    	this.extended = extended;
    }

    public ObjectNode toJson() {
        ObjectNode amtHit = Json.newObject();

        amtHit.put("id", id);
        amtHit.put("creationTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,S").format(creationDate));
        amtHit.put("requestId", requestId);
        amtHit.put("isValid", isValid);
        amtHit.put("hitId", hitId);
        amtHit.put("title", title);
        amtHit.put("description", description);
        amtHit.put("lifetimeInSeconds", lifetimeInSeconds);
        amtHit.put("tutorialTime", tutorialTime);
        amtHit.put("maxAssignments", maxAssignments);
        amtHit.put("externalURL", externalURL);
        amtHit.put("reward", reward);
        amtHit.put("disallowPrevious", disallowPrevious);
        amtHit.put("sandbox", sandbox);
        amtHit.put("extended", extended);

        ArrayNode jsonAssignments = amtHit.putArray("assignments"); 
        for (AMTAssignment a : amtAssignments)
        {
            jsonAssignments.add(a.toJson());
        }

        return amtHit;
    }

    public AMTAssignment getAMTAssignmentById(String assignmentId) {
    	for (AMTAssignment a : amtAssignments) {
    		if (a.assignmentId.equals(assignmentId)) {
    			return a;
    		}
    	}
    	return null;
    }

    public String toString() {
        return "AMTHit(" + id + ")";
    }
}
