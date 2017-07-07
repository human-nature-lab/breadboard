package controllers;

import play.*;
import play.mvc.*;
import play.data.*;
import play.libs.*;
import play.libs.F.*;
import play.Logger;

import views.html.*;

import com.fasterxml.jackson.databind.*;

import com.avaje.ebean.*;

import java.util.*;

import models.*;

public class ClientLogin extends Controller 
{
	public static class AMTLogin
	{
		public Long experimentId;
		public Long experimentInstanceId;
		public String hitId;
		public String assignmentId;
		public String workerId;
		public String connectionSpeed;

		public String validate()
		{
			Logger.info("validating experimentId: " + experimentId);			
			Logger.info("validating experimentInstanceId: " + experimentInstanceId);			
			Logger.info("validating hitId: " + hitId);			
			Logger.info("validating assignmentId: " + assignmentId);			
			Logger.info("validating workerId: " + workerId);			
			Logger.info("validating connectionSpeed: " + connectionSpeed);			
			// TODO: save hitId with ExperimentInstance when creating it, then validate here.
			return null;
		}
	}

	public static class CLogin
	{
		public String id;
		public String password;
		public String experimentId;
		public String experimentInstanceId;

		public String validate()
		{
			Logger.info("Client Login, ID: " + id);

			// TODO: make password a groovy closure to allow for hash functions or custom passwords
			if (! ScriptBoard.checkPassword(password)) {
				return "Invalid user or password";
			}
			return null;
		}
	}

	public static Result login(String experimentId, String experimentInstanceId)
	{
		return ok( clientLogin.render(experimentId, experimentInstanceId, Form.form(CLogin.class)) );
	}

	public static Result amtLogin(Long experimentId, Long experimentInstanceId, String hitId, String assignmentId, String workerId)
	{
	  //Logger.info("Got to amtLogin, workerId = " + workerId + ", AMTWorker.countByWorkerId(workerId) = " + AMTWorker.countByWorkerId(workerId)); 
		Experiment experiment = Experiment.findById(experimentId);
		ExperimentInstance experimentInstance = ExperimentInstance.findById(experimentInstanceId);
		AMTHit amtHit = experimentInstance.getHit();

		if (experiment == null || experimentInstance == null || experimentInstance.status != ExperimentInstance.Status.RUNNING) {
			return ok( amtError.render() );
		}

		if (Boolean.TRUE.equals(experimentInstance.hasStarted) && (workerId == null || (! amtHit.hasWorker(workerId)))) {
	    //Logger.info("Got to amtLogin -> amtGameStarted, workerId = " + workerId + ", AMTWorker.countByWorkerId(workerId) = " + AMTWorker.countByWorkerId(workerId)); 
		  return ok ( amtGameStarted.render() );
		}

		if (experimentInstance.getHit() != null) {
			if (amtHit != null) {
				if (amtHit.isExtended()) {
					return ok( amtExtended.render(Experiment.findById(experimentId), ExperimentInstance.findById(experimentInstanceId), hitId, assignmentId, workerId, Form.form(AMTLogin.class)) );
				} 
        
        if ("any".equals(amtHit.disallowPrevious)) {
          int rowCount = AMTAssignment.findRowCountByWorkerId(workerId);
          if (rowCount > 0) {
            return ok(amtPreviousWorker.render());
          }
        } // if ("any".equals(amtHit.disallowPrevious)) {
          
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
            return ok(amtPreviousWorker.render());
          }
        } // if ("type".equals(amtHit.disallowPrevious)) {
			} // if (amtHit != null) {
		} // if (experimentInstance.getHit() != null) {
		return ok( amtClientLogin.render(Experiment.findById(experimentId), ExperimentInstance.findById(experimentInstanceId), hitId, assignmentId, workerId, Form.form(AMTLogin.class)) );
	}

	public static Result dummyHit(String assignmentId, String sandboxString)
	{
	    boolean sandbox = false;
	    if (sandboxString != null && sandboxString.equals("true")) {
	        sandbox = true;
	    }
        return ok( amtDummy.render(assignmentId, sandbox) );
	}

	public static Result amtAuthenticate(Long experimentId, Long experimentInstanceId, String hitId, String assignmentId, String workerId)
	{
	  //Logger.info("Got to amtAuthenticate."); 
		Form<AMTLogin> loginForm = Form.form(AMTLogin.class).bindFromRequest();
		String connectionSpeed = loginForm.get().connectionSpeed;

    ExperimentInstance experimentInstance = ExperimentInstance.findById(experimentInstanceId);
    Experiment experiment = Experiment.findById(experimentId);
    if (experimentInstance == null || experiment == null || experimentInstance.status != ExperimentInstance.Status.RUNNING) {
			return ok( amtError.render() );
    }

    AMTHit amtHit = null;
		if (experimentInstance.getHit() != null) {
			amtHit = experimentInstance.getHit();
		}

    if (amtHit == null) {
			return ok( amtError.render() );
    }

		if(loginForm.hasErrors()) {
      Logger.info("loginForm.hasErrors()");
      return badRequest( amtClientLogin.render(Experiment.findById(experimentId), experimentInstance, hitId, assignmentId, workerId, Form.form(AMTLogin.class)) );
		} else {
      // If the game has already started, they can't join.
      if (Boolean.TRUE.equals(ExperimentInstance.findById(experimentInstanceId).hasStarted) && (workerId == null || (! amtHit.hasWorker(workerId)))) {
	      Logger.info("Got to amtAuthenticate -> amtGameStarted, workerId = " + workerId + ", AMTWorker.countByWorkerId(workerId) = " + AMTWorker.countByWorkerId(workerId)); 
        return ok ( amtGameStarted.render() );
      }
    
      Logger.info("! loginForm.hasErrors()");
      String clientId = assignmentId;
      if (! amtHit.hasWorker(workerId)) {
        AMTWorker amtWorker = new AMTWorker();
        amtWorker.workerId = workerId;
        amtHit.amtWorkers.add(amtWorker);
        amtHit.save();
      }
			return redirect(routes.ClientController.index(experimentId.toString(), experimentInstanceId.toString(), clientId, connectionSpeed));
		}
	}

	public static Result authenticate(String experimentId, String experimentInstanceId)
	{
		Form<CLogin> loginForm = Form.form(CLogin.class).bindFromRequest();

		if(loginForm.hasErrors())
		{
		    Logger.info("loginForm.hasErrors()");
			return badRequest(clientLogin.render(experimentId, experimentInstanceId, loginForm));
		}
		else
		{
		    Logger.info("! loginForm.hasErrors()");
		    String clientId = loginForm.get().id;
			return redirect( routes.ClientController.index(experimentId, experimentInstanceId, clientId, null) );
		}
	}

    /**
    * Handle the script websocket.
    */
    public static WebSocket<JsonNode> connect() 
    {
        return new WebSocket<JsonNode>() 
        {
            // Called when the Websocket Handshake is done.
            public void onReady(final WebSocket.In<JsonNode> in, final WebSocket.Out<JsonNode> out)
            {
                try
                {
                    //TODO
                    //ScriptBoard.connect(in, out);
                }
                catch (Exception ex)
                {
                    ex.printStackTrace();
                }
            }
        };
    }

}
