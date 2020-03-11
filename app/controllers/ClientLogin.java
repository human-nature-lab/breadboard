package controllers;

import com.avaje.ebean.Ebean;
import com.avaje.ebean.SqlQuery;
import com.avaje.ebean.SqlRow;
import com.fasterxml.jackson.databind.JsonNode;
import models.*;
import play.Logger;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.WebSocket;
import views.html.*;
import java.util.List;

public class ClientLogin extends Controller {
  public static class AMTLogin {
    public Long experimentId;
    public Long experimentInstanceId;
    public String hitId;
    public String assignmentId;
    public String workerId;
    public String connectionSpeed;

    public String validate() {
      Logger.debug("validating experimentId: " + experimentId);
      Logger.debug("validating experimentInstanceId: " + experimentInstanceId);
      Logger.debug("validating hitId: " + hitId);
      Logger.debug("validating assignmentId: " + assignmentId);
      Logger.debug("validating workerId: " + workerId);
      Logger.debug("validating connectionSpeed: " + connectionSpeed);
      // TODO: save hitId with ExperimentInstance when creating it, then validate here.
      return null;
    }
  }

  public static class CLogin {
    public String id;
    public String password;
    public String experimentId;
    public String experimentInstanceId;

    public String validate() {
      Logger.info("Client Login, ID: " + id);

      // TODO: make password a groovy closure to allow for hash functions or custom passwords
      if (!ScriptBoard.checkPassword(password)) {
        return "Invalid user or password";
      }
      return null;
    }
  }

  public static Result login(String experimentId, String experimentInstanceId) {
    return ok(clientLogin.render(experimentId, experimentInstanceId, Form.form(CLogin.class)));
  }

  public static Result amtLogin(Long experimentId, Long experimentInstanceId, String hitId, String assignmentId, String workerId) {
    Experiment experiment = Experiment.findById(experimentId);
    ExperimentInstance experimentInstance = ExperimentInstance.findById(experimentInstanceId);
    AMTHit amtHit = experimentInstance.getHit();

    // The experiment or experiment instance does not exist or is stopped
    if (experimentInstance == null || experimentInstance.status != ExperimentInstance.Status.RUNNING) {
      ExperimentView amtInactiveHitView = experiment.getExperimentView("amt-inactive-hit");
      if (amtInactiveHitView == null) {
        return internalServerError("amt-inactive-hit not found.");
      }
      return ok(defaultTemplate.render(amtInactiveHitView));
    }

    // The HIT lifetime + Tutorial time timer has expired
    if (Boolean.TRUE.equals(experimentInstance.hasStarted) && (workerId == null || (!amtHit.hasWorker(workerId)))) {
      ExperimentView amtExperimentStartedView = experiment.getExperimentView("amt-experiment-started");
      if (amtExperimentStartedView == null) {
        return internalServerError("amt-experiment-started not found.");
      }
      return ok(defaultTemplate.render(amtExperimentStartedView));
    }

    if (experimentInstance.getHit() != null) {
      if (amtHit != null) {
        ExperimentView amtPreviousWorkerView = experiment.getExperimentView("amt-previous-worker");
        // Disallow participation if the worker has participated in any experiment in this copy of breadboard
        if ("any".equals(amtHit.disallowPrevious)) {
          int rowCount = AMTAssignment.findRowCountByWorkerId(workerId);
          if (rowCount > 0) {
            if (amtPreviousWorkerView == null) {
              return internalServerError("amt-experiment-started not found.");
            }
            return ok(defaultTemplate.render(amtPreviousWorkerView));
          }
        } // if ("any".equals(amtHit.disallowPrevious)) {

        // Disallow participation if the worker has participated in an experiment of this type in this copy of breadboard
        if ("type".equals(amtHit.disallowPrevious)) {
          String sql
              = " select a.worker_id "
              + " from amt_assignments a "
              + " join amt_hits h on a.amt_hit_id = h.id"
              + " join experiment_instances i on h.experiment_instance_id = i.id"
              + " join experiments e on i.experiment_id = e.id"
              + " where e.id = :eid"
              + " and a.worker_id = :wid"
              + " and a.assignment_completed = 1";

          SqlQuery sqlQuery = Ebean.createSqlQuery(sql);
          sqlQuery.setParameter("eid", experimentId);
          sqlQuery.setParameter("wid", workerId);

          List<SqlRow> list = sqlQuery.findList();

          if (list.size() > 0) {
            if (amtPreviousWorkerView == null) {
              return internalServerError("amt-experiment-started not found.");
            }
            return ok(defaultTemplate.render(amtPreviousWorkerView));
          }
        } // if ("type".equals(amtHit.disallowPrevious)) {
      } // if (amtHit != null) {
    } // if (experimentInstance.getHit() != null) {
    ExperimentView clientView = experiment.getExperimentView("client");
    if (clientView == null) {
      return internalServerError("client not found.");
    }
    return ok(defaultTemplate.render(clientView));
  }

  public static Result dummyHit(String assignmentId, String sandboxString) {
    boolean sandbox = false;
    if (sandboxString != null && sandboxString.equals("true")) {
      sandbox = true;
    }
    return ok(amtDummy.render(assignmentId, sandbox));
  }

  public static Result amtAuthenticate(Long experimentId, Long experimentInstanceId, String hitId, String assignmentId, String workerId) {
    //Logger.info("Got to amtAuthenticate.");
    Form<AMTLogin> loginForm = Form.form(AMTLogin.class).bindFromRequest();
    String connectionSpeed = loginForm.get().connectionSpeed;

    ExperimentInstance experimentInstance = ExperimentInstance.findById(experimentInstanceId);
    Experiment experiment = Experiment.findById(experimentId);
    if (experimentInstance == null || experiment == null || experimentInstance.status != ExperimentInstance.Status.RUNNING) {
      return notFound();
    }

    AMTHit amtHit = null;
    if (experimentInstance.getHit() != null) {
      amtHit = experimentInstance.getHit();
    }

    if (amtHit == null) {
      return notFound();
    }

    if (loginForm.hasErrors()) {
      Logger.debug("loginForm.hasErrors()");
      ExperimentView amtLoginView = experiment.getExperimentView("amt-login");
      if (amtLoginView == null) {
        return internalServerError("amt-login not found.");
      }
      return ok(defaultTemplate.render(amtLoginView));
    } else {
      // If the game has already started, they can't join.
      if (Boolean.TRUE.equals(ExperimentInstance.findById(experimentInstanceId).hasStarted) && (workerId == null || (!amtHit.hasWorker(workerId)))) {
        Logger.debug("Got to amtAuthenticate -> amtGameStarted, workerId = " + workerId + ", AMTWorker.countByWorkerId(workerId) = " + AMTWorker.countByWorkerId(workerId));
        ExperimentView amtExperimentStarted = experiment.getExperimentView("amt-experiment-started");
        if (amtExperimentStarted == null) {
          return internalServerError("amt-experiment-started not found.");
        }
        return ok(defaultTemplate.render(amtExperimentStarted));
      }

      Logger.debug("! loginForm.hasErrors()");
      String clientId = assignmentId;
      if (!amtHit.hasWorker(workerId)) {
        AMTWorker amtWorker = new AMTWorker();
        amtWorker.workerId = workerId;
        amtHit.amtWorkers.add(amtWorker);
        amtHit.save();
      }
      return redirect(routes.ClientController.index(experimentId.toString(), experimentInstanceId.toString(), clientId, connectionSpeed));
    }
  }

  public static Result authenticate(String experimentId, String experimentInstanceId) {
    Form<CLogin> loginForm = Form.form(CLogin.class).bindFromRequest();

    if (loginForm.hasErrors()) {
      Logger.debug("loginForm.hasErrors()");
      return badRequest(clientLogin.render(experimentId, experimentInstanceId, loginForm));
    } else {
      Logger.debug("! loginForm.hasErrors()");
      String clientId = loginForm.get().id;
      return redirect(routes.ClientController.index(experimentId, experimentInstanceId, clientId, null));
    }
  }

  /**
   * Handle the script websocket.
   */
  public static WebSocket<JsonNode> connect() {
    return new WebSocket<JsonNode>() {
      // Called when the Websocket Handshake is done.
      public void onReady(final WebSocket.In<JsonNode> in, final WebSocket.Out<JsonNode> out) {
        try {
          //TODO
          //ScriptBoard.connect(in, out);
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
    };
  }

}
