package controllers;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.mturk.AmazonMTurk;
import com.amazonaws.services.mturk.AmazonMTurkClientBuilder;
import com.amazonaws.services.mturk.model.*;
import com.amazonaws.services.mturk.model.Locale;
import com.amazonaws.services.mturk.model.QualificationRequirement;
import com.avaje.ebean.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import models.*;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.IOUtils;
import play.Logger;
import play.Play;
import play.libs.*;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Security;

import java.io.*;
import java.lang.reflect.Type;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class AMTAdmin extends Controller {
  private static final String PRODUCTION_ENDPOINT = "mturk-requester.us-east-1.amazonaws.com";
  private static final String SANDBOX_ENDPOINT = "mturk-requester-sandbox.us-east-1.amazonaws.com";
  private static final String SIGNING_REGION = "us-east-1";
  private static final String SECRET_KEY = Play.application().configuration().getString("amt.secretKey");
  private static final String ACCESS_KEY = Play.application().configuration().getString("amt.accessKey");
  private static final String PARTICIPANT_QUALIFICATION_PREFIX = "breadboard_participant_";

  @Security.Authenticated(Secured.class)
  public static Result getAccountBalance(Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }
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

  private static class JsonWorker {
    public String workerId;
    public List<ObjectNode> assignments = new ArrayList<>();
    public int nCompleted = 0;

    JsonWorker(String id) {
      workerId = id;
    }

    public ObjectNode toJson() {
      ObjectNode worker = Json.newObject();
      worker.put("id", workerId);
      worker.put("nAssignments", assignments.size());
      worker.put("assignmentsCompleted", nCompleted);
      ArrayNode jsonAssignments = worker.putArray("assignments");
      jsonAssignments.addAll(assignments);
      return worker;
    }
  }

  @Security.Authenticated(Secured.class)
  public static Result addQualificationType(String experimentUid, Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }

    Experiment experiment = Experiment.findByUid(experimentUid);

    if (experiment == null) {
      return badRequest("Invalid experiment UID.");
    }

    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();

      String qualificationName = PARTICIPANT_QUALIFICATION_PREFIX + experimentUid;
      String reason = "You previously participated in this experiment.";
      // Create a new qualification type
      CreateQualificationTypeRequest createQualificationTypeRequest = new CreateQualificationTypeRequest()
          .withName(qualificationName)
          .withDescription(reason)
          .withQualificationTypeStatus(QualificationTypeStatus.Active);
      CreateQualificationTypeResult createQualificationTypeResult = mTurk.createQualificationType(createQualificationTypeRequest);
      QualificationType qualificationType = createQualificationTypeResult.getQualificationType();

      ObjectNode qualificationTypeJson = Json.newObject();

      TimeZone tz = TimeZone.getTimeZone("UTC");
      DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
      df.setTimeZone(tz);

      qualificationTypeJson.put("experimentUid", experimentUid);
      qualificationTypeJson.put("experimentName", experiment.name);
      qualificationTypeJson.put("creationTime", df.format(qualificationType.getCreationTime()));
      qualificationTypeJson.put("description", qualificationType.getDescription());
      qualificationTypeJson.put("isRequestable", qualificationType.getIsRequestable());
      qualificationTypeJson.put("keywords", qualificationType.getKeywords());
      qualificationTypeJson.put("name", qualificationType.getName());
      qualificationTypeJson.put("qualificationTypeId", qualificationType.getQualificationTypeId());
      qualificationTypeJson.put("qualificationTypeStatus", qualificationType.getQualificationTypeStatus());
      qualificationTypeJson.put("autoGranted", qualificationType.getAutoGranted());

      return ok(qualificationTypeJson);
    } catch (AmazonServiceException ase) {
      return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  public static QualificationType getQualificationTypeByName(String qualificationName, Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return null;
    }

    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();

      ListQualificationTypesRequest listQualificationTypesRequest = new ListQualificationTypesRequest()
          .withMaxResults(1)
          .withMustBeOwnedByCaller(false)
          .withMustBeRequestable(false)
          .withQuery(qualificationName);

      ListQualificationTypesResult listQualificationTypesResult = mTurk.listQualificationTypes(listQualificationTypesRequest);
      List<QualificationType> qualificationTypes = listQualificationTypesResult.getQualificationTypes();

      if (qualificationTypes.isEmpty()) {
        return null;
      }

      return qualificationTypes.get(0);
    } catch (AmazonServiceException ase) {
      return null;
    } catch (AmazonClientException ace) {
      return null;
    }
  }

  @Security.Authenticated(Secured.class)
  public static Result listQualificationTypes(Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }

    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();

      List<ExperimentQualificationType> experimentQualificationTypes = new ArrayList<ExperimentQualificationType>();

      for (Experiment experiment: Experiment.findAll()) {
        ListQualificationTypesRequest listQualificationTypesRequest = new ListQualificationTypesRequest()
            .withMaxResults(1)
            .withMustBeOwnedByCaller(true)
            .withMustBeRequestable(false)
            .withQuery(PARTICIPANT_QUALIFICATION_PREFIX + experiment.uid);

        ListQualificationTypesResult listQualificationTypesResult = mTurk.listQualificationTypes(listQualificationTypesRequest);
        List<QualificationType> qualificationType = listQualificationTypesResult.getQualificationTypes();
        ExperimentQualificationType experimentQualificationType = new ExperimentQualificationType();

        if (!qualificationType.isEmpty()) {
          experimentQualificationType.qualificationType = qualificationType.get(0);
        }
        experimentQualificationType.experiment = experiment;

        experimentQualificationTypes.add(experimentQualificationType);
      }

      ObjectNode returnJson = Json.newObject();
      ArrayNode qualificationTypesJson = returnJson.putArray("qualificationTypes");

      TimeZone tz = TimeZone.getTimeZone("UTC");
      DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
      df.setTimeZone(tz);

      for (ExperimentQualificationType experimentQualificationType : experimentQualificationTypes) {
        QualificationType qualificationType = experimentQualificationType.qualificationType;
        Experiment experiment = experimentQualificationType.experiment;

        ObjectNode qualificationTypeJson = Json.newObject();

        qualificationTypeJson.put("label", experiment.name + " participant");
        qualificationTypeJson.put("experimentUid", experiment.uid);
        qualificationTypeJson.put("experimentName", experiment.name);
        qualificationTypeJson.put("creationTime", (qualificationType == null) ? null : df.format(qualificationType.getCreationTime()));
        qualificationTypeJson.put("description", (qualificationType == null) ? null : qualificationType.getDescription());
        qualificationTypeJson.put("isRequestable", (qualificationType == null) ? null : qualificationType.getIsRequestable());
        qualificationTypeJson.put("keywords", (qualificationType == null) ? null : qualificationType.getKeywords());
        qualificationTypeJson.put("name", (qualificationType == null) ? null : qualificationType.getName());
        qualificationTypeJson.put("qualificationTypeId", (qualificationType == null) ? null : qualificationType.getQualificationTypeId());
        qualificationTypeJson.put("qualificationTypeStatus", (qualificationType == null) ? null : qualificationType.getQualificationTypeStatus());
        qualificationTypeJson.put("autoGranted", (qualificationType == null) ? null : qualificationType.getAutoGranted());
        qualificationTypesJson.add(qualificationTypeJson);
      }
      returnJson.put("rows", experimentQualificationTypes.size());

      return ok(returnJson);
    } catch (AmazonServiceException ase) {
      return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  @Security.Authenticated(Secured.class)
  public static Result getExperimentQualificationTypeId(String experimentUid, Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }

    QualificationType qualificationType = getQualificationTypeByName(PARTICIPANT_QUALIFICATION_PREFIX + experimentUid, sandbox);
    if (qualificationType == null) {
      try {
        AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
        AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
        builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
        AmazonMTurk mTurk = builder.build();

        String qualificationName = PARTICIPANT_QUALIFICATION_PREFIX + experimentUid;
        String reason = "You previously participated in this experiment.";
        // Create a new qualification type
        CreateQualificationTypeRequest createQualificationTypeRequest = new CreateQualificationTypeRequest()
            .withName(qualificationName)
            .withDescription(reason)
            .withQualificationTypeStatus(QualificationTypeStatus.Active);
        CreateQualificationTypeResult createQualificationTypeResult = mTurk.createQualificationType(createQualificationTypeRequest);
        qualificationType = createQualificationTypeResult.getQualificationType();
      } catch (AmazonServiceException ase) {
        return badRequest(ase.getMessage());
      } catch (AmazonClientException ace) {
        return internalServerError(ace.getMessage());
      }
    }

    ObjectNode returnJson = Json.newObject();
    returnJson.put("qualificationTypeId", qualificationType.getQualificationTypeId());
    return ok(returnJson);
  }

  @Security.Authenticated(Secured.class)
  public static Result assignParticipantQualification(Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }
    String workerId = null;
    String qualificationTypeId = null;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      workerId = json.findPath("workerId").textValue();
      qualificationTypeId = json.findPath("qualificationTypeId").textValue();
    }

    if (workerId == null) {
      return badRequest("Please provide an AMT worker ID.");
    }

    if (qualificationTypeId == null) {
      return badRequest("Please provide a qualification type ID.");
    }

    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();

      AssociateQualificationWithWorkerRequest associateQualificationWithWorkerRequest = new AssociateQualificationWithWorkerRequest()
          .withQualificationTypeId(qualificationTypeId)
          .withIntegerValue(1)
          .withSendNotification(false)
          .withWorkerId(workerId);

      mTurk.associateQualificationWithWorker(associateQualificationWithWorkerRequest);
      return ok();
    } catch (AmazonServiceException ase) {
      return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  @Security.Authenticated(Secured.class)
  public static Result removeParticipantQualification(Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }
    String workerId = null;
    String qualificationTypeId = null;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      workerId = json.findPath("workerId").textValue();
      qualificationTypeId = json.findPath("qualificationTypeId").textValue();
    }

    if (workerId == null) {
      return badRequest("Please provide an AMT worker ID.");
    }

    if (qualificationTypeId == null) {
      return badRequest("Please provide a qualification type ID.");
    }

    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();

      DisassociateQualificationFromWorkerRequest disassociateQualificationFromWorkerRequest = new DisassociateQualificationFromWorkerRequest()
          .withQualificationTypeId(qualificationTypeId)
          .withWorkerId(workerId);

      mTurk.disassociateQualificationFromWorker(disassociateQualificationFromWorkerRequest);
      return ok();
    } catch (AmazonServiceException ase) {
      return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  @Security.Authenticated(Secured.class)
  public static Result getQualificationScore(Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }
    String workerId = null;
    String qualificationTypeId = null;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      workerId = json.findPath("workerId").textValue();
      qualificationTypeId = json.findPath("qualificationTypeId").textValue();
    }

    if (workerId == null) {
      return badRequest("Please provide an AMT worker ID.");
    }

    if (qualificationTypeId == null) {
      return badRequest("Please provide a qualification type ID.");
    }

    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();

      GetQualificationScoreRequest getQualificationScoreRequest = new GetQualificationScoreRequest()
          .withQualificationTypeId(qualificationTypeId)
          .withWorkerId(workerId);

      GetQualificationScoreResult qualificationScoreResult = mTurk.getQualificationScore(getQualificationScoreRequest);
      Qualification qualification = qualificationScoreResult.getQualification();

      TimeZone tz = TimeZone.getTimeZone("UTC");
      DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
      df.setTimeZone(tz);

      ObjectNode returnJson = Json.newObject();

      if (qualification == null) {
        returnJson.put("status", "None");
      } else {
        returnJson.put("grantTime", df.format(qualification.getGrantTime()));
        returnJson.put("integerValue", qualification.getIntegerValue());
        if (qualification.getLocaleValue() != null) {
          returnJson.put("localeValue", qualification.getLocaleValue().toString());
        }
        returnJson.put("qualificationTypeId", qualification.getQualificationTypeId());
        returnJson.put("status", qualification.getStatus());
        returnJson.put("workerId", qualification.getWorkerId());
      }
      return ok(returnJson);
    } catch (AmazonServiceException ase) {
      ObjectNode returnJson = Json.newObject();
      returnJson.put("status", "None");
      return ok(returnJson);
      //return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  @Security.Authenticated(Secured.class)
  public static Result importAMTWorkers(Long experimentId, Boolean sandbox) {
    Http.MultipartFormData body = request().body().asMultipartFormData();
    Long maxUploadSize = play.Play.application().configuration().getLong("maxUploadSize", 50L * 1024L * 1024L);

    // Validate Content-Length header
    try {
      Long fileSize = Long.parseLong(request().getHeader("Content-Length"), 10);
      if (fileSize > maxUploadSize) {
        return badRequest("Uploaded file is too large");
      }
    } catch(Exception e){
      return badRequest("Upload was malformed");
    }

    // Validate the size of the file
    Http.MultipartFormData.FilePart filePart = body.getFile("file");
    File workerCsvFile = filePart.getFile();

    Experiment experiment = Experiment.findById(experimentId);

    // Validate the other data
    if(experiment == null){
      return badRequest("Invalid experiment ID");
    }

    if(workerCsvFile.length() > maxUploadSize){
      return badRequest("Uploaded file is too large");
    }

    try {
      Reader in = new FileReader(workerCsvFile);
      try {
        Date now = new Date();
        String importTitle = "IMPORTED_" + experimentId + "_" + now.getTime();
        // Add a fake Experiment Instance
        ExperimentInstance instance = new ExperimentInstance(importTitle, experiment);
        experiment.instances.add(instance);
        experiment.save();

        // Add a fake AMTHit for the import
        AMTHit amtHit = new AMTHit();
        amtHit.creationDate = now;
        amtHit.requestId = "IMPORTED";
        amtHit.isValid = "true";
        amtHit.hitId = "IMPORTED_" + experimentId + "_" + now.getTime();
        amtHit.title = "IMPORTED INTO " + experiment.name + " AT " + now.getTime();
        amtHit.description = "This is a fake HIT created by breadboard when importing AMT Worker IDs to prevent repeat play.";
        amtHit.lifetimeInSeconds = "0";
        amtHit.tutorialTime = "0";
        amtHit.maxAssignments = "0";
        amtHit.externalURL = "IMPORTED";
        amtHit.reward = "0";
        amtHit.disallowPrevious = "none";
        amtHit.sandbox = sandbox;
        amtHit.setExtended(false);
        amtHit.experimentInstance = instance;
        amtHit.save();

        int assignmentIndex = 0;
        CSVFormat format = CSVFormat.DEFAULT;
        for (CSVRecord record : format.parse(in)) {
          String workerId = record.get(0);
          Logger.debug("workerId = " + workerId);
          AMTWorker amtWorker = AMTWorker.findByWorkerId(workerId);
          if (amtWorker == null) {
            amtWorker = new AMTWorker();
            amtWorker.workerId = workerId;
            amtWorker.score = "";
            amtWorker.completion = "";
            amtWorker.amtHit = amtHit;
            amtWorker.save();
          }

          // Add an assignment for the current experiment and mark it as completed
          AMTAssignment amtAssignment = new AMTAssignment();
          amtAssignment.assignmentId = "IMPORTED_" + experimentId + "_" + now.getTime() + "_" + (++assignmentIndex);
          amtAssignment.workerId = workerId;
          amtAssignment.assignmentCompleted = true;
          amtAssignment.assignmentStatus = "IMPORTED";
          amtAssignment.autoApprovalTime = "IMPORTED";
          amtAssignment.acceptTime = null;
          amtAssignment.submitTime = null;
          amtAssignment.answer = "";
          amtAssignment.score = "";
          amtAssignment.reason = "IMPORTED";
          amtAssignment.completion = "";
          amtAssignment.bonusAmount = "";
          amtAssignment.bonusGranted = false;
          amtAssignment.workerBlocked = false;
          amtAssignment.qualificationAssigned = false;
          amtAssignment.amtHit = amtHit;
          amtAssignment.save();
        }
      } finally {
        in.close();
      }
    } catch (IOException ioe) {
      return badRequest("Error reading uploaded file");
    }

    return ok();
  }

  @Security.Authenticated(Secured.class)
  public static Result disassociateQualificationFromWorker(String qualificationTypeId, Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }

    String workerId;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      workerId = json.findPath("workerId").textValue();
    }

    if (workerId == null) {
      return badRequest("Please provide an AMT worker ID.");
    }

    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();
      DisassociateQualificationFromWorkerRequest disassociateQualificationFromWorkerRequest = new DisassociateQualificationFromWorkerRequest()
          .withQualificationTypeId(qualificationTypeId)
          .withWorkerId(workerId)
          .withReason("Allowing re-participation in this experiment type.");

      mTurk.disassociateQualificationFromWorker(disassociateQualificationFromWorkerRequest);

      return ok();
    } catch (AmazonServiceException ase) {
      return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  @Security.Authenticated(Secured.class)
  public static Result listWorkersWithQualificationType(String qualificationTypeId, Integer maxResults, String nextToken, Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }
    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();
      ListWorkersWithQualificationTypeRequest listWorkersWithQualificationTypeRequest = new ListWorkersWithQualificationTypeRequest()
          .withQualificationTypeId(qualificationTypeId)
          .withMaxResults((maxResults == null) ? 20 : maxResults)
          .withStatus(QualificationStatus.Granted);

      if (nextToken != null) listWorkersWithQualificationTypeRequest.setNextToken(nextToken);
      ListWorkersWithQualificationTypeResult listWorkersWithQualificationTypeResult = mTurk.listWorkersWithQualificationType(listWorkersWithQualificationTypeRequest);
      List<Qualification> qualifications = listWorkersWithQualificationTypeResult.getQualifications();

      ObjectNode returnJson = Json.newObject();
      ArrayNode qualificationsJson = returnJson.putArray("qualifications");
      TimeZone tz = TimeZone.getTimeZone("UTC");
      DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
      df.setTimeZone(tz);

      for (Qualification qualification : qualifications) {
        ObjectNode qualificationJson = Json.newObject();
        qualificationJson.put("qualificationTypeId", qualification.getQualificationTypeId());
        qualificationJson.put("grantTime", df.format(qualification.getGrantTime()));
        qualificationJson.put("integerValue", qualification.getIntegerValue());
        qualificationJson.put("status", qualification.getStatus());
        qualificationJson.put("workerId", qualification.getWorkerId());
        qualificationsJson.add(qualificationJson);
      }
      returnJson.put("rows", listWorkersWithQualificationTypeResult.getNumResults());
      returnJson.put("nextToken", listWorkersWithQualificationTypeResult.getNextToken());

      return ok(returnJson);
    } catch (AmazonServiceException ase) {
      return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  @Security.Authenticated(Secured.class)
  public static Result getAMTWorkers(Long experimentId, Boolean sandbox, Integer limit, Integer offset, String search) {
    HashMap<String, JsonWorker> amtWorkerAssignments = new HashMap<>();
    ObjectNode returnJson = Json.newObject();

    /*
    Experiment experiment = Experiment.findById(experimentId);

    if (experiment == null) {
      return badRequest("Invalid experiment ID");
    }
    */
    // TODO: There is a bug in the interaction between distinct and limit in H2 1.3.172,
    // Need to upgrade to latest version of H2 to return exactly limit workers
    // should be fixed in a commit here:
    // https://github.com/h2database/h2database/pull/578/files
    //"(select distinct worker_id from amt_assignments " +

    // Return all workers with their assignments and names and UIDs of experiments

    String workerCountSql = "select count(distinct worker_id) as worker_count from amt_assignments " +
        " where worker_id like CONCAT(:search, '%')" +
        " and worker_id in " +
        "(select worker_id from amt_assignments " +
        " where amt_hit_id in " +
        "(select id from amt_hits where sandbox = :sandbox));";

    SqlRow sqlRow = Ebean.createSqlQuery(workerCountSql)
        .setParameter("search", search)
        .setParameter("sandbox", (sandbox) ? 1 : 0)
        .findUnique();
    Long workerCount = sqlRow.getLong("worker_count");

    String workerIdSql = "select distinct worker_id from amt_assignments " +
        " where worker_id like CONCAT(:search, '%')" +
        " and worker_id in " +
        "(select worker_id from amt_assignments " +
        " where amt_hit_id in " +
        "(select id from amt_hits where sandbox = :sandbox))" +
        " order by worker_id limit :limit offset :offset";

    //" where worker_id like ':search%'" +
    /*
    String sql = "select * from amt_assignments " +
        "where worker_id in " +
        "(select worker_id from amt_assignments " +
        " where worker_id like CONCAT(:search, '%')" +
        " and amt_hit_id in " +
        "(select id from amt_hits where sandbox = :sandbox and experiment_instance_id in " +
        "(select id from experiment_instances where experiment_id = :experimentId)) " +
        "order by worker_id limit :limit offset :offset) order by worker_id;";
       */

     SqlQuery sqlQuery = Ebean.createSqlQuery(workerIdSql)
        .setParameter("limit", limit)
        .setParameter("offset", offset)
        .setParameter("search", search)
        .setParameter("sandbox", (sandbox) ? 1 : 0);

     //Logger.debug(sqlQuery.toString());

      List<SqlRow> workerIds = sqlQuery.findList();

      returnJson.put("total", workerCount);
      returnJson.put("offset", offset);
      returnJson.put("limit", limit);

      // This is necessary because of the bug regarding distinct + limit
    int endI = Math.min(limit, workerIds.size());
    for (int i = 0; i < endI; i++) {
      SqlRow row = workerIds.get(i);
      String workerId = row.getString("worker_id");
      List<AMTAssignment> assignments = AMTAssignment.find
          .where()
          .eq("worker_id", workerId)
          .findList();

      if (! amtWorkerAssignments.containsKey(workerId)) {
        amtWorkerAssignments.put(workerId, new JsonWorker(workerId));
      }

      JsonWorker jsonWorker = amtWorkerAssignments.get(workerId);
      for (AMTAssignment assignment : assignments) {
        AMTHit hit = assignment.amtHit;
        ExperimentInstance instance = hit.experimentInstance;
        Experiment experiment = (instance == null) ? null : instance.experiment;

        String experimentName = (experiment == null) ? "" : experiment.name;
        Long eId = (experiment == null) ? -1L : experiment.id;
        String eUid = (experiment == null) ? "" : experiment.uid;

        Boolean assignmentCompleted = assignment.assignmentCompleted;;
        if (assignmentCompleted != null && assignmentCompleted) jsonWorker.nCompleted++;

        ObjectNode amtAssignment = Json.newObject();

        amtAssignment.put("id", assignment.id);
        amtAssignment.put("assignmentId", assignment.assignmentId);
        amtAssignment.put("workerId", workerId);
        amtAssignment.put("assignmentStatus", assignment.assignmentStatus);
        amtAssignment.put("autoApprovalTime", assignment.autoApprovalTime);
        amtAssignment.put("acceptTime", assignment.acceptTime);
        amtAssignment.put("submitTime", assignment.submitTime);
        amtAssignment.put("answer", assignment.answer);
        amtAssignment.put("score", assignment.score);
        amtAssignment.put("reason", assignment.reason);
        amtAssignment.put("completion", assignment.completion);
        amtAssignment.put("assignmentCompleted", assignmentCompleted);
        amtAssignment.put("bonusGranted", assignment.bonusGranted);
        amtAssignment.put("bonusAmount", assignment.bonusAmount);
        amtAssignment.put("workerBlocked", assignment.workerBlocked);
        amtAssignment.put("qualificationAssigned", assignment.qualificationAssigned);
        amtAssignment.put("experimentName", experimentName);
        amtAssignment.put("experimentUid", eUid);
        amtAssignment.put("experimentId", eId);

        jsonWorker.assignments.add(amtAssignment);
      }

    }

    ArrayNode amtWorkersJson = returnJson.putArray("amtWorkers");

    for (JsonWorker jsonWorker : amtWorkerAssignments.values()) {
      amtWorkersJson.add(jsonWorker.toJson());
    }
    returnJson.put("rows", amtWorkerAssignments.size());

    /*
    for (Map.Entry<String, List<ObjectNode>> entry : amtWorkerAssignments.entrySet()) {
      ObjectNode worker = Json.newObject();
      int nCompleted = 0;
      worker.put("id", entry.getKey());
      worker.put("nAssignments", entry.getValue().size());
      ArrayNode workerAssignments = worker.putArray("assignments");
      for (AMTAssignment assignment : entry.getValue()) {
        if (assignment != null) {
          if (assignment.assignmentCompleted != null && assignment.assignmentCompleted) nCompleted++;
          workerAssignments.add(assignment.toJson());
        }
      }
      worker.put("assignmentsCompleted", nCompleted);
      amtWorkersJson.add(worker);
    }
    */

    return ok(returnJson);
  }

  @Security.Authenticated(Secured.class)
  public static Result listHITs(String nextToken, Integer maxResults, Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }
    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();
      ListHITsRequest listHITsRequest = new ListHITsRequest().withMaxResults((maxResults == null) ? 20 : maxResults);
      if (nextToken != null) listHITsRequest.setNextToken(nextToken);
      ListHITsResult hitResults = mTurk.listHITs(listHITsRequest);
      List<HIT> hits = hitResults.getHITs();

      // Update amt_hits table
      for (HIT hit : hits) {
        AMTHit amtHit = AMTHit.findByHitId(hit.getHITId());
        if (amtHit == null) {
          amtHit = new AMTHit();
        }
        amtHit.hitId = hit.getHITId();
        amtHit.creationDate = hit.getCreationTime();
        amtHit.title = hit.getTitle();
        amtHit.description = hit.getDescription();
        amtHit.maxAssignments = hit.getMaxAssignments().toString();
        amtHit.reward = hit.getReward();
        amtHit.sandbox = sandbox;
        amtHit.save();
      }

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

  @Security.Authenticated(Secured.class)
  public static Result listAssignmentsForHIT(String hitId, Integer maxResults, String nextToken, Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }
    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();
      ListAssignmentsForHITRequest listAssignmentsForHITRequest = new ListAssignmentsForHITRequest().withMaxResults(maxResults).withHITId(hitId);
      ObjectNode returnJson = Json.newObject();
      ArrayNode jsonAssignments = returnJson.putArray("assignments");
      if (nextToken != null) listAssignmentsForHITRequest.setNextToken(nextToken);
      ListAssignmentsForHITResult assignmentResults = mTurk.listAssignmentsForHIT(listAssignmentsForHITRequest);
      List<Assignment> assignments = assignmentResults.getAssignments();

      TimeZone tz = TimeZone.getTimeZone("UTC");
      DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
      df.setTimeZone(tz);

      // Update amt_assignments table
      for (Assignment assignment : assignments) {
        AMTHit hit = AMTHit.findByHitId(assignment.getHITId());
        AMTAssignment amtAssignment = hit.getAMTAssignmentById(assignment.getAssignmentId());
        boolean update = true;
        if (amtAssignment == null) {
          amtAssignment = new AMTAssignment();
          // Default to incomplete, require checking completed box to prevent repeat play
          amtAssignment.assignmentCompleted = false;
          update = false;
        }
        amtAssignment.amtHit = hit;
        amtAssignment.assignmentId = assignment.getAssignmentId();
        amtAssignment.workerId = assignment.getWorkerId();
        amtAssignment.assignmentStatus = assignment.getAssignmentStatus();
        amtAssignment.autoApprovalTime = assignment.getAutoApprovalTime().toString();
        amtAssignment.acceptTime = (assignment.getAcceptTime() == null) ? null :  df.format(assignment.getAcceptTime());
        amtAssignment.submitTime = (assignment.getSubmitTime() == null) ? null :  df.format(assignment.getSubmitTime());
        amtAssignment.answer = assignment.getAnswer();

        if (!update) {
          hit.amtAssignments.add(amtAssignment);
        }
        hit.save();

        ObjectNode jsonAssignment = Json.newObject();
        jsonAssignment.put("assignmentId", amtAssignment.assignmentId);
        jsonAssignment.put("workerId", amtAssignment.workerId);
        jsonAssignment.put("approvalTime", (assignment.getApprovalTime() == null) ? null : df.format(assignment.getApprovalTime()));
        jsonAssignment.put("rejectionTime", (assignment.getRejectionTime() == null) ? null : df.format(assignment.getRejectionTime()));
        jsonAssignment.put("answer", amtAssignment.answer);
        jsonAssignment.put("assignmentCompleted", amtAssignment.assignmentCompleted);

        jsonAssignments.add(jsonAssignment);
      }

      String token = assignmentResults.getNextToken();
      returnJson.put("nextToken", token);
      return ok(returnJson);
    } catch (AmazonServiceException ase) {
      return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  @Security.Authenticated(Secured.class)
  public static Result listBonusPaymentsForHIT(String hitId, Integer maxResults, String nextToken, Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }
    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();
      ListBonusPaymentsRequest listBonusPaymentsRequest = new ListBonusPaymentsRequest().withMaxResults(maxResults).withHITId(hitId);
      if (nextToken != null) listBonusPaymentsRequest.setNextToken(nextToken);
      ListBonusPaymentsResult bonusPaymentsResults = mTurk.listBonusPayments(listBonusPaymentsRequest);
      List<BonusPayment> bonusPayments = bonusPaymentsResults.getBonusPayments();
      // Update amt_assignments table
      for (BonusPayment bonusPayment : bonusPayments) {
        AMTAssignment amtAssignment = AMTAssignment.findByAssignmentId(bonusPayment.getAssignmentId());
        if (amtAssignment != null) {
          amtAssignment.bonusGranted = true;
          amtAssignment.bonusAmount = bonusPayment.getBonusAmount();
          amtAssignment.save();
        }
      }
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

  @Security.Authenticated(Secured.class)
  public static Result approveAssignment(String assignmentId, Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }
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

  @Security.Authenticated(Secured.class)
  public static Result rejectAssignment(String assignmentId, Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }
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

  @Security.Authenticated(Secured.class)
  public static Result sendBonus(String assignmentId, Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }
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

  @Security.Authenticated(Secured.class)
  public static Result updateAssignmentCompleted(String assignmentId) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }
    String completedText;

    JsonNode json = request().body().asJson();

    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      completedText = json.findPath("completed").asText();
    }

    if (completedText == null || (!(completedText.matches("(?i)0|1|true|false")))) {
      return badRequest("Please provide completed as a boolean value (0, 1, true, false).");
    }

    AMTAssignment amtAssignment = AMTAssignment.findByAssignmentId(assignmentId);

    if (amtAssignment == null) {
      return badRequest("Invalid Assignment ID.");
    }

    Boolean completed = (completedText.matches("(?i)1|true"));

    amtAssignment.assignmentCompleted = completed;
    amtAssignment.save();

    return ok();
  }

  @Security.Authenticated(Secured.class)
  public static Result createHIT(Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }
    String title;
    String description;
    String reward;
    Integer maxAssignments;
    Long hitLifetime;
    Long tutorialTime;
    Long assignmentDuration;
    String keywords;
    String disallowPrevious;
    String experimentId;
    String experimentInstanceId;
    BBQualificationRequirement[] qualificationRequirements = null;

    JsonNode json = request().body().asJson();
    if (json == null) {
      return badRequest("Expecting Json data");
    } else {
      title = json.findPath("title").textValue();
      description = json.findPath("description").textValue();
      reward = json.findPath("reward").textValue();
      maxAssignments = json.findPath("maxAssignments").asInt(-1);
      hitLifetime = json.findPath("hitLifetime").asLong(-1L);
      tutorialTime = json.findPath("tutorialTime").asLong(-1L);
      assignmentDuration = json.findPath("assignmentDuration").asLong(-1L);
      keywords = json.findPath("keywords").textValue();
      disallowPrevious = json.findPath("disallowPrevious").textValue();
      experimentId = json.findPath("experimentId").textValue();
      experimentInstanceId = json.findPath("experimentInstanceId").textValue();
      Logger.debug("qualificationRequirements.asText() = " + json.findPath("qualificationRequirements").toString());
      try {
        qualificationRequirements = new ObjectMapper().readValue(json.findPath("qualificationRequirements").toString(), BBQualificationRequirement[].class);
      } catch(IOException ioe) {}
    }


    if (title == null || description == null || reward == null || maxAssignments < 0 || hitLifetime < 0 || tutorialTime < 0 || assignmentDuration < 0 || keywords == null || disallowPrevious == null || experimentId == null || experimentInstanceId == null) {
      return badRequest("Please provide experiment ID, experiment instance ID, title, description, reward, max assignments, hit lifetime, tutorial time, assignment duration, keywords, and allow repeat play option.");
    }


    String rootURL = play.Play.application().configuration().getString("breadboard.rootUrl");
    String gameURL = String.format("/game/%1$s/%2$s/amt", experimentId, experimentInstanceId);
    String externalURL = rootURL + gameURL;
    Integer frameHeight = play.Play.application().configuration().getInt("breadboard.amtFrameHeight");
    String question = "<ExternalQuestion xmlns=\"http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2006-07-14/ExternalQuestion.xsd\">\n" +
        "  <ExternalURL>" + externalURL + "</ExternalURL>\n" +
        "  <FrameHeight>" + frameHeight + "</FrameHeight>\n" +
        "</ExternalQuestion>";

    try {
      AWSStaticCredentialsProvider awsCredentials = new AWSStaticCredentialsProvider(new BasicAWSCredentials(ACCESS_KEY, SECRET_KEY));
      AmazonMTurkClientBuilder builder = AmazonMTurkClientBuilder.standard().withCredentials(awsCredentials);
      builder.setEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration((sandbox ? SANDBOX_ENDPOINT : PRODUCTION_ENDPOINT), SIGNING_REGION));
      AmazonMTurk mTurk = builder.build();

      Experiment experiment = Experiment.findById(Long.parseLong(experimentId));
      String experimentName = experiment.name;
      if (experimentName.length() > 120) experimentName = experimentName.substring(0, 120) + "...";
      String annotation = "{\"experimentType\":\"breadboard\",\"experimentUid\":\"" + experiment.uid + "\",\"experimentName\":\"" + experimentName + "\"}";

      // Create HIT
      CreateHITRequest createHITRequest = new CreateHITRequest()
          .withQuestion(question)
          .withTitle(title)
          .withDescription(description)
          .withMaxAssignments(maxAssignments)
          .withLifetimeInSeconds(hitLifetime)
          .withAssignmentDurationInSeconds(assignmentDuration)
          .withKeywords(keywords)
          .withReward(reward)
          .withRequesterAnnotation(annotation);

      if (qualificationRequirements != null) {
        Logger.debug("qualificationRequirements = " + ((qualificationRequirements != null) ? qualificationRequirements : "qualificationRequirements is null"));
      }

      List<QualificationRequirement> qualificationRequirementList = new ArrayList<>();
      for (BBQualificationRequirement bbQualificationRequirement : qualificationRequirements) {
        List<Locale> locales = new ArrayList<>();
        for (BBLocale bbLocale : bbQualificationRequirement.locales) {
          Locale locale = new Locale()
              .withCountry(bbLocale.country.trim());
          if (!bbLocale.subdivision.trim().isEmpty()) {
              locale.setSubdivision(bbLocale.subdivision.trim());
          }
          locales.add(locale);
        }
        List<Integer> integerValues = new ArrayList<>();
        for (String integerString : bbQualificationRequirement.integerValues.split(",")) {
          try {
            Integer integerValue = Integer.parseInt(integerString.trim());
            integerValues.add(integerValue);
          } catch (NumberFormatException nfe) {}
        }

        String qualificationTypeId = bbQualificationRequirement.selectedQualificationType.qualificationTypeId;

        if (qualificationTypeId.equals("OTHER_EXPERIMENT")) {
          String experimentUid = bbQualificationRequirement.otherExperiment;
          String qualificationName = PARTICIPANT_QUALIFICATION_PREFIX + experimentUid;
          QualificationType qualificationType = getQualificationTypeByName(qualificationName, sandbox);
          if (qualificationType != null) {
            qualificationTypeId = qualificationType.getQualificationTypeId();
          }
        }

        QualificationRequirement qualificationRequirement = new QualificationRequirement()
            .withActionsGuarded(bbQualificationRequirement.actionsGuarded)
            .withComparator(bbQualificationRequirement.comparator)
            .withQualificationTypeId(qualificationTypeId);

        if (!locales.isEmpty()) {
          qualificationRequirement.setLocaleValues(locales);
        }

        if (!integerValues.isEmpty()) {
          qualificationRequirement.setIntegerValues(integerValues);
        }

        qualificationRequirementList.add(qualificationRequirement);
      }

      if (!qualificationRequirementList.isEmpty()) {
        createHITRequest.setQualificationRequirements(qualificationRequirementList);
      }

      Logger.debug("createHitRequest = " + createHITRequest.toString());

      CreateHITResult createHITResult = mTurk.createHIT(createHITRequest);
      HIT hit = createHITResult.getHIT();
      AMTHit amtHit = new AMTHit();
      amtHit.hitId = hit.getHITId();
      amtHit.description = hit.getDescription();
      amtHit.lifetimeInSeconds = hitLifetime.toString();
      amtHit.tutorialTime = tutorialTime.toString();
      amtHit.maxAssignments = hit.getMaxAssignments().toString();
      amtHit.externalURL = externalURL;
      amtHit.reward = hit.getReward();
      amtHit.title = hit.getTitle();
      amtHit.disallowPrevious = disallowPrevious;
      amtHit.sandbox = sandbox;

      ExperimentInstance experimentInstance = ExperimentInstance.findById(Long.parseLong(experimentInstanceId));
      experimentInstance.amtHits.add(amtHit);
      experimentInstance.save();

      return ok(amtHit.toJson());
    } catch (AmazonServiceException ase) {
      return badRequest(ase.getMessage());
    } catch (AmazonClientException ace) {
      return internalServerError(ace.getMessage());
    }
  }

  @Security.Authenticated(Secured.class)
  public static Result createDummyHit(Boolean sandbox) {
    if (SECRET_KEY == null || ACCESS_KEY == null) {
      return badRequest("No AWS keys provided");
    }
    String workerId = null;
    String reason = null;
    String reward = null;
    String paymentHitHtml = getDummyHitHTML();
    if (paymentHitHtml == null) {
      return badRequest("Unable to read 'payment-hit.html' or 'default-payment-hit.html' file in conf/defaults directory.");
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
          .withQualificationTypeStatus(QualificationTypeStatus.Active);
      CreateQualificationTypeResult createQualificationTypeResult = mTurk.createQualificationType(createQualificationTypeRequest);

      AssociateQualificationWithWorkerRequest associateQualificationWithWorkerRequest = new AssociateQualificationWithWorkerRequest()
          .withQualificationTypeId(createQualificationTypeResult.getQualificationType().getQualificationTypeId())
          .withIntegerValue(1)
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
    InputStream paymentHitHtml = Play.application().resourceAsStream("defaults/payment-hit.html");
    if (paymentHitHtml == null) paymentHitHtml = Play.application().resourceAsStream("defaults/default-payment-hit.html");
    if (paymentHitHtml == null) return null;
    try {
      returnString = "<HTMLQuestion xmlns=\"http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2011-11-11/HTMLQuestion.xsd\">\n" +
                     "  <HTMLContent><![CDATA[" +
                     IOUtils.toString(paymentHitHtml) +
                     "]]>\n" +
                     "  </HTMLContent>\n" +
                     "  <FrameHeight>600</FrameHeight>\n" +
                     "</HTMLQuestion>";
    } catch (IOException ioe) { }
    return returnString;
  }

  private static class ExperimentQualificationType {
    public QualificationType qualificationType;
    public Experiment experiment;
  }

  private static class BBQualificationRequirement {
    public String actionsGuarded;
    public String comparator;
    public String integerValues;
    public List<BBLocale> locales;
    public String otherExperiment;
    public BBQualificationType selectedQualificationType;
  }

  private static class BBQualificationType {
    public String label;
    public String qualificationTypeId;
    public String experimentUid;
    public String experimentName;
    public String creationTime;
    public String description;
    public Boolean isRequestable;
    public Boolean autoGranted;
    public String keywords;
    public String name;
    public String qualificationTypeStatus;
  }

  private static class BBLocale {
    public String country;
    public String subdivision;
  }
}
