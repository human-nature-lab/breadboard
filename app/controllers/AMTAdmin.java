package controllers;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.mturk.AmazonMTurk;
import com.amazonaws.services.mturk.AmazonMTurkClientBuilder;
import com.amazonaws.services.mturk.model.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import play.Play;
import play.Logger;
import play.data.DynamicForm;
import play.data.Form;
import play.libs.*;
import play.mvc.Controller;
import play.mvc.Result;
import views.html.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;

public class AMTAdmin extends Controller {
  private static final String PRODUCTION_ENDPOINT = "mturk-requester.us-east-1.amazonaws.com";
  private static final String SANDBOX_ENDPOINT = "mturk-requester-sandbox.us-east-1.amazonaws.com";
  private static final String SIGNING_REGION = "us-east-1";
  private static final String SECRET_KEY = Play.application().configuration().getString("amt.secretKey");
  private static final String ACCESS_KEY = Play.application().configuration().getString("amt.accessKey");

  public static Result login() {
    return ok(login.render(Form.form(Application.Login.class)));
  }

  public static Result authenticate() {
    Form<Application.Login> loginForm = Form.form(Application.Login.class).bindFromRequest();

    if (loginForm.hasErrors()) {
      return badRequest(login.render(loginForm));
    } else {
      String email = loginForm.get().email;

      session("email", email);

      return redirect(routes.AMTAdmin.index());
    }
  }

  public static Result logout() {
    session().clear();
    flash("success", "You've been logged out");
    return redirect(routes.AMTAdmin.login());
  }

  public static Result index() {
    return ok(amtAdmin.render());
  }

  public static Result getAccountBalance(Boolean sandbox) {
    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();
      GetAccountBalanceRequest getAccountBalanceRequest= new GetAccountBalanceRequest();
      GetAccountBalanceResult getAccountBalanceResult = mTurk.getAccountBalance(getAccountBalanceRequest);
      String availableBalance = getAccountBalanceResult.getAvailableBalance();
      String onHoldBalance = getAccountBalanceResult.getOnHoldBalance();
      ObjectNode returnJson = Json.newObject();
      returnJson.put("availableBalance", availableBalance);
      returnJson.put("onHoldBalance", onHoldBalance);
      return ok(returnJson);
    } catch (AmazonServiceException ase) {
      return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  public static Result listHITs(String nextToken, Integer maxResults, Boolean sandbox) {
    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();
      ListHITsRequest listHITsRequest = new ListHITsRequest().withMaxResults((maxResults == null) ? 20 : maxResults);
      if (nextToken != null) listHITsRequest.setNextToken(nextToken);
      ListHITsResult hitResults = mTurk.listHITs(listHITsRequest);
      List<HIT> hits = hitResults.getHITs();
      String next = hitResults.getNextToken();
      ObjectNode returnJson = Json.newObject();
      returnJson.put("hits", Json.toJson(hits));
      returnJson.put("nextToken", next);
      return ok(returnJson);
    } catch (AmazonServiceException ase) {
      return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  public static Result listAssignmentsForHIT(String hitId, Integer maxResults, String nextToken, Boolean sandbox) {
    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();
      ListAssignmentsForHITRequest listAssignmentsForHITRequest = new ListAssignmentsForHITRequest().withMaxResults(maxResults).withHITId(hitId);
      if (nextToken != null) listAssignmentsForHITRequest.setNextToken(nextToken);
      ListAssignmentsForHITResult assignmentResults = mTurk.listAssignmentsForHIT(listAssignmentsForHITRequest);
      List<Assignment> assignments = assignmentResults.getAssignments();
      String token = assignmentResults.getNextToken();
      ObjectNode returnJson = Json.newObject();
      returnJson.put("assignments", Json.toJson(assignments));
      returnJson.put("nextToken", token);
      return ok(returnJson);
    } catch (AmazonServiceException ase) {
      return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  public static Result listBonusPaymentsForHIT(String hitId, Integer maxResults, String nextToken, Boolean sandbox) {
    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();
      ListBonusPaymentsRequest listBonusPaymentsRequest = new ListBonusPaymentsRequest().withMaxResults(maxResults).withHITId(hitId);
      if (nextToken != null) listBonusPaymentsRequest.setNextToken(nextToken);
      ListBonusPaymentsResult bonusPaymentsResults = mTurk.listBonusPayments(listBonusPaymentsRequest);
      List<BonusPayment> bonusPayments = bonusPaymentsResults.getBonusPayments();
      String token = bonusPaymentsResults.getNextToken();
      ObjectNode returnJson = Json.newObject();
      returnJson.put("bonusPayments", Json.toJson(bonusPayments));
      returnJson.put("nextToken", token);
      return ok(returnJson);
    } catch (AmazonServiceException ase) {
      return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  public static Result approveAssignment(String assignmentId, Boolean sandbox) {
    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();
      ApproveAssignmentRequest approveAssignmentRequest= new ApproveAssignmentRequest().withAssignmentId(assignmentId);
      mTurk.approveAssignment(approveAssignmentRequest);
      return ok();
    } catch (AmazonServiceException ase) {
      return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  public static Result rejectAssignment(String assignmentId, Boolean sandbox) {
    String requesterFeedback = null;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      requesterFeedback = json.findPath("requesterFeedback").textValue();
    }

    if (requesterFeedback == null) {
      return badRequest("Please provide requester feedback.");
    }
    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();
      RejectAssignmentRequest rejectAssignmentRequest= new RejectAssignmentRequest()
          .withAssignmentId(assignmentId)
          .withRequesterFeedback(requesterFeedback);
      mTurk.rejectAssignment(rejectAssignmentRequest);
      return ok();
    } catch (AmazonServiceException ase) {
      return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  public static Result sendBonus(String assignmentId, Boolean sandbox) {
    String bonusAmount = null;
    String reason = null;
    String workerId = null;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      bonusAmount = json.findPath("bonusAmount").textValue();
      reason = json.findPath("reason").textValue();
      workerId = json.findPath("workerId").textValue();
    }

    if (bonusAmount == null || reason == null || workerId == null) {
      return badRequest("Please provide workerId, bonusAmount, and reason.");
    }
    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();
      SendBonusRequest sendBonusRequest= new SendBonusRequest().withAssignmentId(assignmentId).withBonusAmount(bonusAmount).withReason(reason).withWorkerId(workerId);
      mTurk.sendBonus(sendBonusRequest);
      return ok();
    } catch (AmazonServiceException ase) {
      return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  public static Result createDummyHit(Boolean sandbox) {
    String workerId = null;
    String reason = null;
    String reward = null;
    String paymentHitHtml = getDummyHitHTML();
    if (paymentHitHtml == null) {
      return badRequest("Unable to read 'payment-hit.html' file in conf directory.");
    }
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      workerId = json.findPath("workerId").textValue();
      reason = json.findPath("reason").textValue();
      reward = json.findPath("reward").textValue();
    }

    if (workerId == null || reason == null || reward == null) {
      return badRequest("Please provide workerId, reason, and reward.");
    }

    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();

      // Create a qualification for the worker
      String qualificationName = new Date().getTime() + "_" + workerId;
      CreateQualificationTypeRequest createQualificationTypeRequest = new CreateQualificationTypeRequest()
          .withName(qualificationName)
          .withDescription(reason)
          .withAutoGranted(true)
          .withAutoGrantedValue(1)
          .withQualificationTypeStatus(QualificationTypeStatus.Active);
      CreateQualificationTypeResult createQualificationTypeResult = mTurk.createQualificationType(createQualificationTypeRequest);

      AssociateQualificationWithWorkerRequest associateQualificationWithWorkerRequest = new AssociateQualificationWithWorkerRequest()
          .withQualificationTypeId(createQualificationTypeResult.getQualificationType().getQualificationTypeId())
          .withWorkerId(workerId);

      mTurk.associateQualificationWithWorker(associateQualificationWithWorkerRequest);

      QualificationRequirement qualificationRequirement = new QualificationRequirement()
          .withQualificationTypeId(createQualificationTypeResult.getQualificationType().getQualificationTypeId())
          .withRequiredToPreview(true)
          .withComparator("EqualTo")
          .withIntegerValues(1);

      // Create Dummy/Payment HIT
      CreateHITRequest createHITRequest = new CreateHITRequest()
          .withTitle("HIT for " + workerId)
          .withDescription(reason)
          .withMaxAssignments(1)
          .withQuestion(paymentHitHtml)
          .withQualificationRequirements(qualificationRequirement)
          .withLifetimeInSeconds(31536000L)
          .withAssignmentDurationInSeconds(5400L)
          .withKeywords(workerId)
          .withReward(reward);

      mTurk.createHIT(createHITRequest);

      return ok();
    } catch (AmazonServiceException ase) {
      return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  private static String getDummyHitHTML() {
    String returnString = null;
    try {
      returnString = "<HTMLQuestion xmlns=\"http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2011-11-11/HTMLQuestion.xsd\">\n" +
                     "  <HTMLContent><![CDATA[" +
                     IOUtils.toString(Play.application().resourceAsStream("payment-hit.html")) +
                     "]]>\n" +
                     "  </HTMLContent>\n" +
                     "  <FrameHeight>600</FrameHeight>\n" +
                     "</HTMLQuestion>";
    } catch (IOException ioe) { }
    return returnString;
  }

  /*
  public static Result createDummyHitOld() {
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
      return redirect(routes.AMTAdmin.index());
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
        if (dom != null) {
          String isValid = XPath.selectText("//IsValid", dom);
          if (isValid.equals("True")) {
            String qualificationTypeId = XPath.selectText("//QualificationTypeId", dom);
            Logger.debug("qualificationTypeId = " + qualificationTypeId);
            F.Promise<WS.Response> assignQualificationResponse = controllers.MechanicalTurk.assignQualification(qualificationTypeId, amtIdsArray[i], "1", sandbox);
            if (assignQualificationResponse != null) {
              responseBody = assignQualificationResponse.get().getBody();
              Logger.debug("assignQualificationResponse: " + responseBody);
              dom = XML.fromString(responseBody);
              if (dom != null) {
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
                    if (dom != null) {
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

    flash("success", "<p><strong>Succeeded in creating " + successIds.size() + "/" + amtIdsArray.length + " dummy HITs.</strong></p>  Dummy HITs created:<ul>" + "<li>amtIds: " + amtIdsLog + "</li><li>reason: " + reason + "</li><li>reward: " + reward.toString() + "</li><li>sandbox: " + sandbox.toString() + "</li></ul>");
    return redirect(routes.AMTAdmin.index());
  }
  */
}
