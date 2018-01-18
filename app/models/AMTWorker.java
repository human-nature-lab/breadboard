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
@Table(name = "amt_workers")
public class AMTWorker extends Model {
  @Id
  public Long id;

  public String workerId;
  public String score;
  public String completion;

  @JsonIgnore
  @ManyToOne
  public AMTHit amtHit;

  @JsonIgnore
  public static Model.Finder<Long, AMTWorker> find = new Model.Finder(Long.class, AMTWorker.class);

  public static List<AMTWorker> findAll() {
    return find.all();
  }

  public static AMTWorker findByWorkerId(String workerId) {
    return find.where().eq("workerId", workerId).findUnique();
  }

  public static int countByWorkerId(String wid) {
    return find.where().eq("worker_id", wid).findRowCount();
  }

  public AMTWorker() {
  }

  public ObjectNode toJson() {
    ObjectNode amtWorker = Json.newObject();

    amtWorker.put("id", id);
    amtWorker.put("workerId", workerId);
    amtWorker.put("score", score);
    amtWorker.put("completion", completion);

    return amtWorker;
  }

  public String toString() {
    return "AMTWorker(" + id + ")";
  }
}
