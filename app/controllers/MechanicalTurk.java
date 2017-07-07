package controllers;

import org.w3c.dom.Document;
import play.Logger;
import play.Play;
import play.libs.F;
import play.libs.F.Promise;
import play.libs.WS;
import play.libs.WS.Response;
import play.libs.XML;
import play.libs.XPath;
import security.Signature;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

public class MechanicalTurk {

  // Default assignment duration to 90 minutes.
  private static final String ASSIGNMENT_DURATION_IN_SECONDS = "5400";

  // Please specify secret keys as AMT_SECRET_KEY and AMT_ACCESS_KEY environmental variables
  private static final String SECRET_KEY = Play.application().configuration().getString("amt.secretKey");
  private static final String ACCESS_KEY = Play.application().configuration().getString("amt.accessKey");

  public static final String SANDBOX_SERVICE_URL = "https://mechanicalturk.sandbox.amazonaws.com";
  public static final String SERVICE_URL = "https://mechanicalturk.amazonaws.com";

  public static String getServiceUrl(Boolean sandbox) {
    if (sandbox)
      return SANDBOX_SERVICE_URL;

    return SERVICE_URL;
  }


  public static Promise<Response> grantBonus(String workerId, String assignmentId, String bonus, Boolean sandbox) {
    WS.WSRequestHolder requestHolder = WS.url(getServiceUrl(sandbox));

    String service = "AWSMechanicalTurkRequester";
    String operation = "GrantBonus";

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    Date now = new Date();
    SimpleDateFormat ISO8601FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    String timestamp = ISO8601FORMAT.format(now) + 'Z';

    requestHolder.setQueryParameter("Service", service);
    requestHolder.setQueryParameter("Operation", operation);
    requestHolder.setQueryParameter("WorkerId", workerId);
    requestHolder.setQueryParameter("AssignmentId", assignmentId);
    requestHolder.setQueryParameter("BonusAmount.1.Amount", bonus);
    requestHolder.setQueryParameter("BonusAmount.1.CurrencyCode", "USD");
    requestHolder.setQueryParameter("Reason", "Final game score.");
    // This unique token will prevent multiple bonuses from being granted per assignment
    requestHolder.setQueryParameter("UniqueRequestToken", assignmentId);

    requestHolder.setQueryParameter("AWSAccessKeyId", ACCESS_KEY);
    requestHolder.setQueryParameter("Timestamp", timestamp);

    try {
      String signature = Signature.getSignature(service, operation, timestamp, SECRET_KEY);
      requestHolder.setQueryParameter("Signature", signature);
    } catch (java.security.SignatureException se) {
      Logger.error("Signature.getSignature threw a SignatureException: ".concat(se.toString()));
    }

    return requestHolder.get();
  }

  public static Promise<Response> approveAssignment(String assignmentId, Boolean sandbox) {
    WS.WSRequestHolder requestHolder = WS.url(getServiceUrl(sandbox));

    String service = "AWSMechanicalTurkRequester";
    String operation = "ApproveAssignment";

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    Date now = new Date();
    SimpleDateFormat ISO8601FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    String timestamp = ISO8601FORMAT.format(now) + 'Z';

    requestHolder.setQueryParameter("Service", service);
    requestHolder.setQueryParameter("Operation", operation);
    requestHolder.setQueryParameter("AssignmentId", assignmentId);

    requestHolder.setQueryParameter("AWSAccessKeyId", ACCESS_KEY);
    requestHolder.setQueryParameter("Timestamp", timestamp);

    try {
      String signature = Signature.getSignature(service, operation, timestamp, SECRET_KEY);
      requestHolder.setQueryParameter("Signature", signature);
    } catch (java.security.SignatureException se) {
      Logger.error("Signature.getSignature threw a SignatureException: ".concat(se.toString()));
    }

    return requestHolder.get();
  }

  public static Promise<Response> rejectAssignment(String assignmentId, Boolean sandbox) {
    WS.WSRequestHolder requestHolder = WS.url(getServiceUrl(sandbox));

    String service = "AWSMechanicalTurkRequester";
    String operation = "RejectAssignment";

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    Date now = new Date();
    SimpleDateFormat ISO8601FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    String timestamp = ISO8601FORMAT.format(now) + 'Z';

    requestHolder.setQueryParameter("Service", service);
    requestHolder.setQueryParameter("Operation", operation);
    requestHolder.setQueryParameter("AssignmentId", assignmentId);

    requestHolder.setQueryParameter("AWSAccessKeyId", ACCESS_KEY);
    requestHolder.setQueryParameter("Timestamp", timestamp);

    try {
      String signature = Signature.getSignature(service, operation, timestamp, SECRET_KEY);
      requestHolder.setQueryParameter("Signature", signature);
    } catch (java.security.SignatureException se) {
      Logger.error("Signature.getSignature threw a SignatureException: ".concat(se.toString()));
    }

    return requestHolder.get();
  }

  public static Promise<Response> blockWorker(String workerId, Boolean sandbox) {
    WS.WSRequestHolder requestHolder = WS.url(getServiceUrl(sandbox));

    String service = "AWSMechanicalTurkRequester";
    String operation = "BlockWorker";

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    Date now = new Date();
    SimpleDateFormat ISO8601FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    String timestamp = ISO8601FORMAT.format(now) + 'Z';

    requestHolder.setQueryParameter("Service", service);
    requestHolder.setQueryParameter("Operation", operation);
    requestHolder.setQueryParameter("WorkerId", workerId);
    requestHolder.setQueryParameter("Reason", "Already participated in a game.");

    requestHolder.setQueryParameter("AWSAccessKeyId", ACCESS_KEY);
    requestHolder.setQueryParameter("Timestamp", timestamp);

    try {
      String signature = Signature.getSignature(service, operation, timestamp, SECRET_KEY);
      requestHolder.setQueryParameter("Signature", signature);
    } catch (java.security.SignatureException se) {
      Logger.error("Signature.getSignature threw a SignatureException: ".concat(se.toString()));
    }

    return requestHolder.get();
  }

  public static Promise<Response> submitAssignment(String assignmentId, Boolean sandbox) {
    String url = "https://www.mturk.com/mturk/externalSubmit";

    if (sandbox) {
      url = "https://workersandbox.mturk.com/mturk/externalSubmit";
    }

    WS.WSRequestHolder requestHolder = WS.url(url);

    requestHolder.setQueryParameter("assignmentId", assignmentId);

    return requestHolder.get();
  }


  public static Promise<Response> extendHit(String hitId, Integer seconds, Boolean sandbox) {
    WS.WSRequestHolder requestHolder = WS.url(getServiceUrl(sandbox));

    String service = "AWSMechanicalTurkRequester";
    String operation = "ExtendHIT";

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    Date now = new Date();
    SimpleDateFormat ISO8601FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    String timestamp = ISO8601FORMAT.format(now) + 'Z';

    requestHolder.setQueryParameter("Service", service);
    requestHolder.setQueryParameter("Operation", operation);
    requestHolder.setQueryParameter("HITId", hitId);
    requestHolder.setQueryParameter("ExpirationIncrementInSeconds", seconds.toString());

    requestHolder.setQueryParameter("AWSAccessKeyId", ACCESS_KEY);
    requestHolder.setQueryParameter("Timestamp", timestamp);

    try {
      String signature = Signature.getSignature(service, operation, timestamp, SECRET_KEY);
      requestHolder.setQueryParameter("Signature", signature);
    } catch (java.security.SignatureException se) {
      Logger.error("Signature.getSignature threw a SignatureException: ".concat(se.toString()));
    }

    return requestHolder.get();
  }

  public static Promise<Response> getAssignmentsForHIT(String hitId, Boolean sandbox) {
    WS.WSRequestHolder requestHolder = WS.url(getServiceUrl(sandbox));

    String service = "AWSMechanicalTurkRequester";
    String operation = "GetAssignmentsForHIT";

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    Date now = new Date();
    SimpleDateFormat ISO8601FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    String timestamp = ISO8601FORMAT.format(now) + 'Z';

    requestHolder.setQueryParameter("Service", service);
    requestHolder.setQueryParameter("Operation", operation);
    requestHolder.setQueryParameter("HITId", hitId);
    requestHolder.setQueryParameter("PageSize", "100");

    requestHolder.setQueryParameter("AWSAccessKeyId", ACCESS_KEY);
    requestHolder.setQueryParameter("Timestamp", timestamp);

    try {
      String signature = Signature.getSignature(service, operation, timestamp, SECRET_KEY);
      requestHolder.setQueryParameter("Signature", signature);
    } catch (java.security.SignatureException se) {
      Logger.error("Signature.getSignature threw a SignatureException: ".concat(se.toString()));
    }

    return requestHolder.get();
  }

  public static Promise<Response> createQualification(String name, String description, Boolean autoGranted, Boolean sandbox) {
    WS.WSRequestHolder requestHolder = WS.url(getServiceUrl(sandbox));

    String service = "AWSMechanicalTurkRequester";
    String operation = "CreateQualificationType";

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    Date now = new Date();
    SimpleDateFormat ISO8601FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    String timestamp = ISO8601FORMAT.format(now) + 'Z';

    requestHolder.setQueryParameter("Service", service);
    requestHolder.setQueryParameter("Operation", operation);
    requestHolder.setQueryParameter("Name", name);
    requestHolder.setQueryParameter("Description", description);
    requestHolder.setQueryParameter("QualificationTypeStatus", "Active");
    if (autoGranted) {
      requestHolder.setQueryParameter("AutoGranted", "true");
      requestHolder.setQueryParameter("AutoGrantedValue", "0");
    }

    requestHolder.setQueryParameter("AWSAccessKeyId", ACCESS_KEY);
    requestHolder.setQueryParameter("Timestamp", timestamp);

    try {
      String signature = Signature.getSignature(service, operation, timestamp, SECRET_KEY);
      requestHolder.setQueryParameter("Signature", signature);
    } catch (java.security.SignatureException se) {
      Logger.error("Signature.getSignature threw a SignatureException: ".concat(se.toString()));
    }

    return requestHolder.get();
  }

  public static Promise<Response> assignQualification(String qualificationTypeId, String workerId, String integerValue, Boolean sandbox) {
    WS.WSRequestHolder requestHolder = WS.url(getServiceUrl(sandbox));

    String service = "AWSMechanicalTurkRequester";
    String operation = "AssignQualification";

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    Date now = new Date();
    SimpleDateFormat ISO8601FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    String timestamp = ISO8601FORMAT.format(now) + 'Z';

    requestHolder.setQueryParameter("Service", service);
    requestHolder.setQueryParameter("Operation", operation);

    requestHolder.setQueryParameter("QualificationTypeId", qualificationTypeId);
    requestHolder.setQueryParameter("WorkerId", workerId);

    requestHolder.setQueryParameter("AWSAccessKeyId", ACCESS_KEY);
    requestHolder.setQueryParameter("Timestamp", timestamp);

    requestHolder.setQueryParameter("IntegerValue", integerValue);

    try {
      String signature = Signature.getSignature(service, operation, timestamp, SECRET_KEY);
      requestHolder.setQueryParameter("Signature", signature);
    } catch (java.security.SignatureException se) {
      Logger.error("Signature.getSignature threw a SignatureException: ".concat(se.toString()));
    }

    return requestHolder.get();
  }

  public static Promise<Response> updateQualificationScore(String qualificationTypeId, String workerId, String integerValue, Boolean sandbox) {
    WS.WSRequestHolder requestHolder = WS.url(getServiceUrl(sandbox));

    String service = "AWSMechanicalTurkRequester";
    String operation = "UpdateQualificationScore";

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    Date now = new Date();
    SimpleDateFormat ISO8601FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    String timestamp = ISO8601FORMAT.format(now) + 'Z';

    requestHolder.setQueryParameter("Service", service);
    requestHolder.setQueryParameter("Operation", operation);

    requestHolder.setQueryParameter("QualificationTypeId", qualificationTypeId);
    requestHolder.setQueryParameter("SubjectId", workerId);
    requestHolder.setQueryParameter("IntegerValue", integerValue);

    requestHolder.setQueryParameter("AWSAccessKeyId", ACCESS_KEY);
    requestHolder.setQueryParameter("Timestamp", timestamp);

    try {
      String signature = Signature.getSignature(service, operation, timestamp, SECRET_KEY);
      requestHolder.setQueryParameter("Signature", signature);
    } catch (java.security.SignatureException se) {
      Logger.error("Signature.getSignature threw a SignatureException: ".concat(se.toString()));
    }

    return requestHolder.get();
  }

  public static boolean assignOrUpdateQualification(String qualificationTypeId, String workerId, String integerValue, Boolean sandbox) {
    boolean assigned = false;
    boolean updated = false;

    // Assign qualification
    F.Promise<WS.Response> assignResponse = controllers.MechanicalTurk.assignQualification(qualificationTypeId, workerId, integerValue, sandbox);
    if (assignResponse != null) {
      String responseBody = assignResponse.get().getBody();
      Logger.debug("response: " + responseBody);
      Document dom = XML.fromString(responseBody);
      if (dom != null) {
        String isValid = XPath.selectText("//IsValid", dom);
        if (isValid.equals("True")) {
          assigned = true;
        } //if (isValid.equals("True"))
      } //if (dom != null)
    } //if (response != null)

    if (!assigned) {
      // Update Qualification 
      F.Promise<WS.Response> updateResponse = controllers.MechanicalTurk.updateQualificationScore(qualificationTypeId, workerId, integerValue, sandbox);
      if (updateResponse != null) {
        String responseBody = updateResponse.get().getBody();
        Logger.debug("response: " + responseBody);
        Document dom = XML.fromString(responseBody);
        if (dom != null) {
          String isValid = XPath.selectText("//IsValid", dom);
          if (isValid.equals("True")) {
            updated = true;
          } //if (isValid.equals("True"))
        } //if (dom != null)
      } //if (response != null)
    }

    return (assigned || updated);
  }

  public static Promise<Response> createAMTHit(
      String title,
      String description,
      String externalURL,
      Integer frameHeight,
      BigDecimal reward,
      Integer lifetimeInSeconds,
      Integer maxAssignments,
      Boolean sandbox,
      models.QualificationRequirement qualificationRequirement) throws UnsupportedEncodingException {
    //Integer assignmentDurationInSeconds,
    //TODO: UniqueRequestToken: send on retry so duplicate HITs are not created

    Logger.debug("ACCESS_KEY = " + ACCESS_KEY);

    WS.WSRequestHolder requestHolder = WS.url(getServiceUrl(sandbox));

    String service = "AWSMechanicalTurkRequester";
    String operation = "CreateHIT";
    String question = String.format("<ExternalQuestion xmlns=\"http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2006-07-14/ExternalQuestion.xsd\"><ExternalURL>%s</ExternalURL><FrameHeight>%s</FrameHeight></ExternalQuestion>", externalURL, frameHeight);

    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    Date now = new Date();
    SimpleDateFormat ISO8601FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss");
    String timestamp = ISO8601FORMAT.format(now) + 'Z';

    requestHolder.setQueryParameter("Service", service);
    requestHolder.setQueryParameter("Operation", operation);
    requestHolder.setQueryParameter("ResponseGroup", "Minimal");
    requestHolder.setQueryParameter("Title", title);
    requestHolder.setQueryParameter("Description", description);
    requestHolder.setQueryParameter("Question", question);
    requestHolder.setQueryParameter("Reward.1.Amount", reward.toPlainString());
    requestHolder.setQueryParameter("Reward.1.CurrencyCode", "USD");
    requestHolder.setQueryParameter("LifetimeInSeconds", Integer.toString(lifetimeInSeconds));
    //requestHolder.setQueryParameter("AssignmentDurationInSeconds", Integer.toString(assignmentDurationInSeconds));
    requestHolder.setQueryParameter("AssignmentDurationInSeconds", ASSIGNMENT_DURATION_IN_SECONDS);
    requestHolder.setQueryParameter("MaxAssignments", Integer.toString(maxAssignments));
    requestHolder.setQueryParameter("AWSAccessKeyId", ACCESS_KEY);
    requestHolder.setQueryParameter("Timestamp", timestamp);

    if (qualificationRequirement != null) {
      requestHolder.setQueryParameter("QualificationRequirement.1.QualificationTypeId", qualificationRequirement.qualificationTypeId);
      requestHolder.setQueryParameter("QualificationRequirement.1.Comparator", qualificationRequirement.comparator);
      if (!qualificationRequirement.comparator.equals("Exists")) {
        requestHolder.setQueryParameter("QualificationRequirement.1.IntegerValue", qualificationRequirement.integerValue);
      }
      requestHolder.setQueryParameter("QualificationRequirement.1.RequiredToPreview", "true");
    }

    try {
      String signature = Signature.getSignature(service, operation, timestamp, SECRET_KEY);
      requestHolder.setQueryParameter("Signature", signature);
    } catch (java.security.SignatureException se) {
      Logger.error("Signature.getSignature threw a SignatureException: ".concat(se.toString()));
    }

    return requestHolder.get();
  }

}
