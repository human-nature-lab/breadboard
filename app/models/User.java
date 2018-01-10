package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mindrot.jbcrypt.BCrypt;
import play.Logger;
import play.data.format.Formats;
import play.data.validation.Constraints;
import play.db.ebean.Model;
import play.libs.Json;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User extends Model {

  @Id
  @Constraints.Required
  @Formats.NonEmpty
  public String email;

  @Constraints.Required
  public String name;

  @Constraints.Required
  public String password;

  public String uid;

  @OneToOne
  public Language defaultLanguage;

  /*
   * Currently role can be admin or amt_admin, should expand this to include experiment runners vs.
   * experiment designers.
   */
  public String role;

  @ManyToMany
  public List<Experiment> ownedExperiments = new ArrayList<Experiment>();

  @OneToOne
  public Experiment selectedExperiment;

  @Column(columnDefinition = "text")
  public String currentScript;

  public Long experimentInstanceId = -1L;

  @JsonIgnore
  public static Model.Finder<String, User> find = new Model.Finder(String.class, User.class);

  public static int findRowCount() {
    return find.findRowCount();
  }

  public static List<User> findAll() {
    return find.all();
  }

  public static User findByEmail(String email) {
    return find.where().eq("email", email).findUnique();
  }

  public static User findByUID(String uid) {
    return find.where().eq("uid", uid).findUnique();
  }

  public static User authenticate(String email, String password) {
    Logger.debug("authenticate(" + email + ")");
    User user = find.where()
        .eq("email", email)
        .findUnique();
    Logger.debug("user: " + user);
    if (user == null) {
      return null;
    }
    if (BCrypt.checkpw(password, user.password)) {
      return user;
    } else {
      return null;
    }
  }

  public void setExperimentInstanceId(Long experimentInstanceId) {
    this.experimentInstanceId = experimentInstanceId;
  }

  public void setSelectedExperiment(Experiment selectedExperiment) {
    this.selectedExperiment = selectedExperiment;
  }

  public Experiment getExperiment() {
    return selectedExperiment;
  }

  public Experiment getExperimentByName(String name) {
    for (Experiment e : ownedExperiments) {
      if (e.name.equals(name))
        return e;
    }
    return null;
  }

  @JsonValue
  public ObjectNode toJson() {
    ObjectNode breadboard = Json.newObject();
    ObjectNode user = Json.newObject();

    ArrayList<String> experimentNames = new ArrayList<String>();

    /*
    for (Experiment experiment : ownedExperiments) {
      experimentNames.add(experiment.name);
    }
    */

    user.put("email", this.email);

    user.put("defaultLanguage", this.defaultLanguage.toJson());

    //user.put("experiments", Json.toJson(experimentNames));
    ArrayNode experiments = user.putArray("experiments");
    for (Experiment experiment: ownedExperiments) {
      experiments.add(experiment.toJson());
    }

    if (selectedExperiment != null) {
      user.put("selectedExperiment", selectedExperiment.name);
    } else {
      Logger.debug("selectedExperiment == null?");
    }

    user.put("currentScript", currentScript);

    breadboard.put("user", user);

    if (selectedExperiment != null) {
      breadboard.put("experiment", selectedExperiment.toJson());
    }

    if (experimentInstanceId == Experiment.TEST_INSTANCE_ID) {
      breadboard.put("experimentInstance", selectedExperiment.getTestInstance().toJson());
    } else if (experimentInstanceId != -1L && experimentInstanceId != null) {
      ExperimentInstance ei = ExperimentInstance.findById(experimentInstanceId);
      Logger.debug("experimentInstanceId = " + experimentInstanceId.toString() + (ei == null ? " is null" : ""));
      if (ei != null) {
        breadboard.put("experimentInstance", ei.toJson());
      } else {
        experimentInstanceId = -1L;
        breadboard.put("experimentInstance", "");
      }
    } else {
      breadboard.put("experimentInstance", "");
    }

    return breadboard;
  }

  public String toString() {
    return "User(" + email + ")";
  }
}
