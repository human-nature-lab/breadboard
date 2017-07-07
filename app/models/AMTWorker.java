package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import play.Logger;
import play.libs.Json;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.*;
import javax.persistence.*;
import java.text.SimpleDateFormat;

import play.db.ebean.*;
import play.data.format.*;
import play.data.validation.*;

import com.avaje.ebean.*;

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
