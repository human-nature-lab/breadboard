package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.db.ebean.Model;
import play.libs.Json;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.List;

@Entity
@Table(name = "amt_assignments")
public class AMTAssignment extends Model {
  @Id
  public Long id;

  public String assignmentId;
  public String workerId;
  public String assignmentStatus;
  public String autoApprovalTime;
  public String acceptTime;
  public String submitTime;
  public String answer;
  public String score;
  public String reason;
  public String completion;

  public Boolean assignmentCompleted;
  public Boolean bonusGranted;
  public Boolean workerBlocked;
  public Boolean qualificationAssigned;

  @JsonIgnore
  @ManyToOne
  public AMTHit amtHit;

  @JsonIgnore
  public static Model.Finder<Long, AMTAssignment> find = new Model.Finder(Long.class, AMTAssignment.class);

  public static List<AMTAssignment> findAll() {
    return find.all();
  }

  public static AMTAssignment findByAssignmentId(String assignmentId) {
    return find.where().eq("assignmentId", assignmentId).findUnique();
  }

  public static int findRowCountByWorkerId(String workerId) {
    return find.where().eq("workerId", workerId).eq("assignmentCompleted", true).findRowCount();
  }

  public AMTAssignment() {
  }

  public ObjectNode toJson() {
    ObjectNode amtAssignment = Json.newObject();

    amtAssignment.put("id", id);
    amtAssignment.put("assignmentId", assignmentId);
    amtAssignment.put("workerId", workerId);
    amtAssignment.put("assignmentStatus", assignmentStatus);
    amtAssignment.put("autoApprovalTime", autoApprovalTime);
    amtAssignment.put("acceptTime", acceptTime);
    amtAssignment.put("submitTime", submitTime);
    amtAssignment.put("answer", answer);
    amtAssignment.put("score", score);
    amtAssignment.put("reason", reason);
    amtAssignment.put("completion", completion);
    amtAssignment.put("assignmentCompleted", assignmentCompleted);
    amtAssignment.put("bonusGranted", bonusGranted);
    amtAssignment.put("workerBlocked", workerBlocked);
    amtAssignment.put("qualificationAssigned", qualificationAssigned);

    return amtAssignment;
  }

  public String toString() {
    return "AMTAssignment(" + id + ")";
  }
}
