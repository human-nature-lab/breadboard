package controllers;

import play.*;
import play.mvc.*;
import play.data.Form;
import play.data.DynamicForm;
import play.libs.F.*;
import play.libs.*;

import models.*;
import views.html.*;

import java.math.BigDecimal;
import java.util.*;

import org.w3c.dom.Document; 

import org.apache.commons.lang3.StringUtils;

public class AMTAdmin extends Controller {

    public static Result login() {
        return ok( login.render(Form.form(Application.Login.class)) );
    }

    public static Result authenticate() {
        Form<Application.Login> loginForm = Form.form(Application.Login.class).bindFromRequest();

        if(loginForm.hasErrors()) {
            return badRequest(login.render(loginForm));
        } else {
            String email = loginForm.get().email;

            session("email", email);

            return redirect( routes.AMTAdmin.index() );
        }
    }

    public static Result logout() {
        session().clear();
        flash("success", "You've been logged out");
        return redirect( routes.AMTAdmin.login() );
    }

    public static Result index() {
        return ok( amtAdmin.render() );
    }

    public static Result createDummyHit() {
        DynamicForm requestData = new DynamicForm().bindFromRequest();
        String amtIds = requestData.get("amt_ids");
        String reason = requestData.get("reason");
        String stringReward = requestData.get("reward");
        Boolean sandbox = false;
        if (requestData.get("sandbox") != null && requestData.get("sandbox").equals("on")) {
            sandbox = true;
        }

        BigDecimal reward;
        try {
            reward = new BigDecimal(stringReward);
        } catch (NumberFormatException nfe) {
            flash("error", "Unable to parse reward into valid dollar value.");
            return redirect( routes.AMTAdmin.index() );
        }

        String[] amtIdsArray = amtIds.split("[,\\s]+");

        String timestamp = new Date().getTime() + "";

        ArrayList<String> successIds = new ArrayList<String>();
        for (int i = 0; i < amtIdsArray.length; i++) {
            F.Promise<WS.Response> createQualificationResponse = controllers.MechanicalTurk.createQualification(timestamp + "_" + amtIdsArray[i], reason, false, sandbox);
            if (createQualificationResponse != null) {
                String responseBody = createQualificationResponse.get().getBody();
                Logger.debug("createQualificationResponse: " + responseBody);
                Document dom = XML.fromString(responseBody);
                if(dom != null) {
                    String isValid = XPath.selectText("//IsValid", dom); 
                    if (isValid.equals("True")) {
                        String qualificationTypeId = XPath.selectText("//QualificationTypeId", dom); 
                        Logger.debug("qualificationTypeId = " + qualificationTypeId);
                        F.Promise<WS.Response> assignQualificationResponse = controllers.MechanicalTurk.assignQualification(qualificationTypeId, amtIdsArray[i], "1", sandbox);
                        if (assignQualificationResponse != null) {
                            responseBody = assignQualificationResponse.get().getBody();
                            Logger.debug("assignQualificationResponse: " + responseBody);
                            dom = XML.fromString(responseBody);
                            if(dom != null) {
                                isValid = XPath.selectText("//IsValid", dom); 
                                if (isValid.equals("True")) {
                                    String hitTitle = "HIT for " + amtIdsArray[i] + " (Reason: " + reason.trim();
                                    hitTitle = StringUtils.abbreviate(hitTitle, 127);
                                    hitTitle += ")";

                                    //String url = (sandbox) ? "http://54.225.223.34:9000/dummyHit?sandbox=true" : "http://54.225.223.34:9000/dummyHit";
                                    String rootURL = play.Play.application().configuration().getString("breadboard.rootUrl");
                                    String url = (sandbox) ? rootURL + "/dummyHit?sandbox=true" : rootURL + "/dummyHit";

                                    QualificationRequirement qualificationRequirement = new QualificationRequirement();
                                    qualificationRequirement.qualificationTypeId = qualificationTypeId;
                                    qualificationRequirement.comparator = "Exists";
                                    qualificationRequirement.integerValue = "1";
                                    try {
                                        F.Promise<WS.Response> createAMTHitResponse = controllers.MechanicalTurk.createAMTHit(
                                            hitTitle,
                                            reason,
                                            url,
                                            600,
                                            reward,
                                            31536000,
                                            1,
                                            sandbox,
                                            qualificationRequirement);

                                        responseBody = createAMTHitResponse.get().getBody();
                                        Logger.debug("createAMTHitResponse: " + responseBody);
                                        dom = XML.fromString(responseBody);
                                        if(dom != null) {
                                            isValid = XPath.selectText("//IsValid", dom); 
                                            if (isValid.equals("True")) {
                                                successIds.add(amtIdsArray[i]);
                                            }
                                        }
                                    } catch (java.io.UnsupportedEncodingException uee) {
                                        Logger.debug("Caught UnsupportedEncodingException: " + uee.getMessage());
                                    }
                                } else {
                                    Logger.debug("assignQualificationResponse IsValid != True");    
                                }
                            } else {
                                Logger.debug("assignQualificationResponse dom == null");
                            }
                        } else {
                            Logger.debug("assignQualificationResponse == null");
                        }
                    } else {
                        Logger.debug("createQualificationResponse IsValid != True");    
                    }
                } else {
                    Logger.debug("createQualificationResponse dom == null");
                }
            }
        }

        String amtIdsLog = "";
        for (String s : successIds) {
            amtIdsLog += s + " ";
        }

        /* Just for testing. 
        try {
            Thread.sleep(1000); 
        } catch (java.lang.InterruptedException ie) {}
        */

        flash("success", "<p><strong>Succeeded in creating " + successIds.size() + "/" + amtIdsArray.length + " dummy HITs.</strong></p>  Dummy HITs created:<ul>" + "<li>amtIds: " + amtIdsLog + "</li><li>reason: " + reason + "</li><li>reward: " + reward.toString() + "</li><li>sandbox: " + sandbox.toString() + "</li></ul>" ); 
        return redirect( routes.AMTAdmin.index() );
    }
}
