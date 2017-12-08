package models;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import com.tinkerpop.blueprints.Vertex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.imgscalr.Scalr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import play.Logger;
import play.Play;
import play.libs.*;
import play.libs.F.Callback;
import play.mvc.WebSocket;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

public class Breadboard extends UntypedActor {
  // How often to send websocket messages in ms
  public static final long WEBSOCKET_RATE = 100L;

  // One script environment instance per user at this time, map the user's email address to the instance
  static Map<String, ActorRef> instances = new HashMap<String, ActorRef>();

  static ActorRef breadboardController = Akka.system().actorOf(new Props(Breadboard.class));

  static ThrottledWebSocketOut out;

  public static void connect(final WebSocket.In<JsonNode> in, final WebSocket.Out<JsonNode> wsout) {
    out = new ThrottledWebSocketOut(wsout, WEBSOCKET_RATE);
    in.onMessage(new Callback<JsonNode>() {
      public void invoke(JsonNode event) {
        try {
          ObjectMapper mapper = new ObjectMapper();
          // TODO: check if there is a better way to do this with new version of Jackson
          Map<String, Object> jsonInput = mapper.readValue(event.toString(), Map.class);
          String action = jsonInput.get("action").toString();
          String uid = jsonInput.get("uid").toString();
          User user = User.findByUID(uid);
          if (user != null) {
            if (action.equals("LogIn")) {
              breadboardController.tell(new LogIn(user, out), null);
            } else if (action.equals("SaveUserSettings")) {
              String currentPassword = jsonInput.get("currentPassword").toString().trim();
              String newPassword = jsonInput.get("newPassword").toString().trim();
              String confirmPassword = jsonInput.get("confirmPassword").toString().trim();

              breadboardController.tell(new SaveUserSettings(user, currentPassword, newPassword, confirmPassword, out), null);

            } else if (action.equals("SelectExperiment")) {
              String experimentName = jsonInput.get("experiment").toString();
              Experiment experiment = user.getExperimentByName(experimentName);

              if (experiment != null)
                breadboardController.tell(new SelectExperiment(user, experiment, out), null);
            } else if (action.equals("CreateExperiment")) {
              String name = jsonInput.get("name").toString();
              String copyExperimentName = jsonInput.containsKey("copyExperimentName") ? jsonInput.get("copyExperimentName").toString() : null;
              breadboardController.tell(new CreateExperiment(user, name, copyExperimentName, out), null);
              Logger.debug("CreateExperiment");
            } else if (action.equals("ImportExperiment")) {
              String importFrom = jsonInput.get("importFrom").toString();
              String importTo = jsonInput.get("importTo").toString();
              breadboardController.tell(new ImportExperiment(user, importFrom, importTo, out), null);
              Logger.debug("ImportExperiment");
            } else if (action.equals("DeleteExperiment")) {
              String selectedExperimentName = jsonInput.get("selectedExperiment").toString();
              instances.get(user.email).tell(new DeleteExperiment(user, selectedExperimentName, out), null);
              //breadboardController.tell(new DeleteExperiment(user, selectedExperimentName, out));
            } else if (action.equals("ExportExperiment")) {
              String selectedExperimentName = jsonInput.get("selectedExperiment").toString();
              Experiment experiment = user.getExperimentByName(selectedExperimentName);
              if (experiment != null) {
                ObjectNode jsonOutput = Json.newObject();
                try {
                  experiment.export();
                  jsonOutput.put("output", "Experiment exported.");
                } catch (IOException ioe) {
                  jsonOutput.put("output", "Export failed.");
                  Logger.error(ioe.getMessage());
                }
                out.write(jsonOutput);
              }
            } else if (action.equals("SubmitAMTTask")) {
              Logger.debug("action.equals(\"SubmitAMTTask\")");
              try {
                String title = jsonInput.get("title").toString();
                String description = jsonInput.get("description").toString();
                BigDecimal reward = new BigDecimal(jsonInput.get("reward").toString());
                Integer lifetimeInSeconds = new Integer(jsonInput.get("lifetimeInSeconds").toString());
                Integer tutorialTime = new Integer(jsonInput.get("tutorialTime").toString());
                Integer maxAssignments = new Integer(jsonInput.get("maxAssignments").toString());
                String disallowPrevious = jsonInput.get("disallowPrevious").toString();
                Boolean sandbox = new Boolean(jsonInput.get("sandbox").toString());
                String experimentId = jsonInput.get("experimentId").toString();
                String experimentInstanceId = jsonInput.get("experimentInstanceId").toString();

                breadboardController.tell(new SubmitAMTTask(user, title, description, reward, lifetimeInSeconds, tutorialTime, maxAssignments, disallowPrevious, sandbox, experimentId, experimentInstanceId, out), null);
              } catch (NumberFormatException nfe) {
                Logger.error("Invalid number provided for reward, lifetimeInSeconds, onInSeconds, or maxAssignments parameter.");
              } catch (Exception e) {
                Logger.debug("Some other exception: " + e.getMessage());
                e.printStackTrace();
              }
            } else if (action.equals("GetAssignmentsForHIT")) {
              try {
                String amtHitIdString = jsonInput.get("amtHitId").toString();
                Logger.debug("amtHitIdString = " + amtHitIdString);
                Long amtHitId = Long.parseLong(amtHitIdString);
                breadboardController.tell(new GetAssignmentsForHIT(user, amtHitId, out), null);
              } catch (NumberFormatException nfe) {
                Logger.error("Invalid number provided for amtHitId.");
              }
            } else if (action.equals("ExtendHit")) {
              try {
                String amtHitIdString = jsonInput.get("amtHitId").toString();
                Logger.debug("amtHitIdString = " + amtHitIdString);
                Long amtHitId = Long.parseLong(amtHitIdString);
                breadboardController.tell(new ExtendHit(user, amtHitId, out), null);
              } catch (NumberFormatException nfe) {
                Logger.error("Invalid number provided for amtHitId.");
              }
            } else if (action.equals("GrantBonus")) {
              String workerId = jsonInput.get("workerId").toString();
              String assignmentId = jsonInput.get("assignmentId").toString();
              String bonus = jsonInput.get("bonus").toString();

              breadboardController.tell(new GrantBonus(user, workerId, assignmentId, bonus, out), null);
            } else if (action.equals("ApproveAssignment")) {
              String assignmentId = jsonInput.get("assignmentId").toString();
              breadboardController.tell(new ApproveAssignment(user, assignmentId, out), null);
            } else if (action.equals("RejectAssignment")) {
              String assignmentId = jsonInput.get("assignmentId").toString();
              breadboardController.tell(new RejectAssignment(user, assignmentId, out), null);
            } else if (action.equals("BlockWorker")) {
              String assignmentId = jsonInput.get("assignmentId").toString();
              breadboardController.tell(new BlockWorker(user, assignmentId, out), null);
            } else if (action.equals("MarkCompleted")) {
              String assignmentId = jsonInput.get("assignmentId").toString();
              breadboardController.tell(new MarkCompleted(user, assignmentId, out), null);
            } else if (action.equals("AssignQualification")) {
              String assignmentId = jsonInput.get("assignmentId").toString();
              breadboardController.tell(new AssignQualification(user, assignmentId, out), null);
            } else if (action.equals("RunGame")) {
              if (instances.containsKey(user.email)) {
                instances.get(user.email).tell(new RunGame(user, out), null);
              }
            } else if (action.equals("SendScript")) {
              if (instances.containsKey(user.email)) {
                String script = jsonInput.get("script").toString();
                instances.get(user.email).tell(new SendScript(user, script, out), null);
              }
            } else if (action.equals("AddLanguage")) {
              Long experimentId = Long.parseLong(jsonInput.get("experimentId").toString());
              String languageCode = jsonInput.get("code").toString();
              breadboardController.tell(new AddLanguage(user, experimentId, languageCode, out), null);
            } else if (action.equals("CreateContent")) {
              String name = jsonInput.get("name").toString();
              breadboardController.tell(new CreateContent(user, name, out), null);
            } else if (action.equals("SaveContent")) {
              Logger.debug("SaveContent");
              try {
                try {
                  Gson gson = new Gson();
                  SaveContentObject saveContentObject = gson.fromJson(event.toString(), SaveContentObject.class);
                  breadboardController.tell(new SaveContent(user, saveContentObject.getContentId(), saveContentObject.getName(), saveContentObject.getTranslations(), out), null);
                } catch (Exception e) {
                  StringWriter sw = new StringWriter();
                  e.printStackTrace(new PrintWriter(sw));
                  String exceptionAsString = sw.toString();
                  Logger.debug(exceptionAsString);
                }

              } catch (NumberFormatException nfe) {
                Logger.debug("SaveContent threw NumberFormatException at Long.parseLong parsing: " + jsonInput.get("contentId").toString());
              }
            } else if (action.equals("MakeChoice")) {
              // TODO: Player client will not be logged in with email and will
              // need a different way to identify the correct game
              if (instances.containsKey(user.email)) {
                String choiceUID = jsonInput.get("choiceUID").toString();
                String params = (jsonInput.containsKey("params")) ? jsonInput.get("params").toString() : null;
                instances.get(user.email).tell(new MakeChoice(user, choiceUID, params, out), null);
              }
            } else if (action.equals("SaveStyle")) {
              String style = jsonInput.get("style").toString();
              breadboardController.tell(new SaveStyle(user, style, out), null);
            } else if (action.equals("SaveClientHtml")) {
              String clientHtml = jsonInput.get("clientHtml").toString();
              breadboardController.tell(new SaveClientHtml(user, clientHtml, out), null);
            } else if (action.equals("SaveClientGraph")) {
              String clientGraph = jsonInput.get("clientGraph").toString();
              breadboardController.tell(new SaveClientGraph(user, clientGraph, out), null);
            } else if (action.equals("CreateStep")) {
              String name = jsonInput.get("name").toString();
              breadboardController.tell(new CreateStep(user, name, out), null);
            } else if (action.equals("DeleteStep")) {
              Long id = Long.valueOf(jsonInput.get("id").toString());
              breadboardController.tell(new DeleteStep(user, id, out), null);
            } else if (action.equals("DeleteContent")) {
              Long id = Long.valueOf(jsonInput.get("id").toString());
              //Logger.debug("action.equals(DeleteContent), id=" + id.toString());
              breadboardController.tell(new DeleteContent(user, id, out), null);
            } else if (action.equals("SendStep")) {
              if (instances.containsKey(user.email)) {
                try {
                  Long id = Long.parseLong(jsonInput.get("id").toString());
                  String name = jsonInput.get("name").toString();
                  String source = jsonInput.get("source").toString();
                  // TODO: is providing the name necessary?
                  instances.get(user.email).tell(new SendStep(user, id, name, source, out), null);
                } catch (NumberFormatException nfe) {
                  Logger.debug("Long.parseLong threw NumberFormatException, input: " + jsonInput.get("id").toString());
                }
              }
            } else if (action.equals("DropPlayer")) {
              if (instances.containsKey(user.email)) {
                String pid = jsonInput.get("pid").toString();
                instances.get(user.email).tell(new DropPlayer(user, pid, out), null);
              }
            } else if (action.equals("LaunchGame")) {
              if (instances.containsKey(user.email)) {
                String name = jsonInput.get("name").toString();
                Object parameters = jsonInput.get("parameters");
                Logger.debug("parameters.getClass().toString() = " + parameters.getClass().toString());
                if (parameters instanceof LinkedHashMap) {
                  LinkedHashMap params = (LinkedHashMap) parameters;
                  instances.get(user.email).tell(new LaunchGame(user, name, params, out), null);
                }
              }
            } else if (action.equals("TestGame")) {
              if (instances.containsKey(user.email)) {
                String name = jsonInput.get("name").toString();
                Object parameters = jsonInput.get("parameters");
                Logger.debug("parameters.getClass().toString() = " + parameters.getClass().toString());
                if (parameters instanceof LinkedHashMap) {
                  LinkedHashMap params = (LinkedHashMap) parameters;
                  instances.get(user.email).tell(new TestGame(user, name, params, out), null);
                }
              }
            } else if (action.equals("StopGame")) {
              if (instances.containsKey(user.email)) {
                try {
                  Long id = Long.parseLong(jsonInput.get("id").toString());
                  instances.get(user.email).tell(new StopGame(user, id, out), null);
                } catch (NumberFormatException nfe) {
                  Logger.debug("Error parsing Long from String: " + jsonInput.get("id").toString());
                }
              }
            } else if (action.equals("NewParameter")) {
              String name = jsonInput.get("name").toString();
              String type = jsonInput.get("type").toString();
              String minVal = (jsonInput.get("minVal") == null) ? "" : jsonInput.get("minVal").toString();
              String maxVal = (jsonInput.get("maxVal") == null) ? "" : jsonInput.get("maxVal").toString();
              String defaultVal = jsonInput.get("defaultVal").toString();
              String description = jsonInput.get("description").toString();

              breadboardController.tell(new NewParameter(user, name, type, minVal, maxVal, defaultVal, description, out), null);
            } else if (action.equals("RemoveParameter")) {
              String id = jsonInput.get("id").toString();

              breadboardController.tell(new RemoveParameter(user, id, out), null);
            } else if (action.equals("SelectInstance")) {
              Long id = Long.parseLong(jsonInput.get("id").toString());

              breadboardController.tell(new SelectInstance(user, id, out), null);
            } else if (action.equals("Update")) {
              breadboardController.tell(new Update(user, out), null);
            } else if (action.equals("ShowEvent")) {
              Long experimentInstanceId = Long.parseLong(jsonInput.get("id").toString());
              breadboardController.tell(new ShowEvent(user, experimentInstanceId, out), null);
            } else if (action.equals("DeleteInstance")) {
              Long experimentInstanceId = Long.parseLong(jsonInput.get("id").toString());
              breadboardController.tell(new DeleteInstance(user, experimentInstanceId, out), null);
            } else if (action.equals("ReloadEngine")) {
              instances.get(user.email).tell(new ReloadEngine(user, out), null);
            } else if (action.equals("DeleteImage")) {
              Long imageId = Long.parseLong(jsonInput.get("imageId").toString());
              breadboardController.tell(new DeleteImage(user, imageId, out), null);
            }
          } else { // END if (user != null)
            Logger.error("user not found with UID: " + user.uid);
          }
        } catch (java.io.IOException ioe) {
          Logger.error(ioe.getMessage());
        }
      }
    });
  }

  public void onReceive(Object message) throws Exception {
    if (message instanceof BreadboardMessage) {
      BreadboardMessage breadboardMessage = (BreadboardMessage) message;
      Logger.debug("breadboardMessage.getClass().getName() = " + breadboardMessage.getClass().getName());

      if (message instanceof LogIn) {
        if (!instances.containsKey(breadboardMessage.user.email)) {
          Logger.debug("! instances.containsKey(breadboardMessage.user.email)");
          // No running instance, start one
          // TODO: Allow multiple instances per user?
          // TODO: Allow starting and stopping of individual instances
          ActorRef scriptBoardController = Akka.system().actorOf(new Props(ScriptBoard.class));
          Logger.debug("scriptBoardController = " + scriptBoardController);
                    /*  Another way to declare the scriptBoardController if we need a non-default constructor
                    ActorRef scriptBoardController = Akka.system().actorOf(new Props(new UntypedActorFactory() {
                        public UntypedActor create() {
                            return new ScriptBoard();
                        }
                    }), "scriptBoardController");
                    */
          Logger.debug("breadboardMessage.user.email PUT: " + breadboardMessage.user.email);
          instances.put(breadboardMessage.user.email, scriptBoardController);

          // Add an Admin to the scriptBoardController
          scriptBoardController.tell(new AddAdmin(breadboardMessage.user, scriptBoardController, breadboardMessage.out), null);

          // If the User has a selected experiment, select the experiment
          if (breadboardMessage.user.selectedExperiment != null) {
            Logger.debug("breadboardMessage.user.selectedExperiment = " + breadboardMessage.user.selectedExperiment);
            breadboardController.tell(new SelectExperiment(breadboardMessage.user, breadboardMessage.user.selectedExperiment, breadboardMessage.out), null);
          }
          if (breadboardMessage.user.experimentInstanceId != -1) {
            breadboardController.tell(new SelectInstance(breadboardMessage.user, breadboardMessage.user.experimentInstanceId, breadboardMessage.out), null);
          }
        } else {
          //this is reconnect/refresh
          Logger.debug("Reconnecting...");
          ObjectNode userJson = breadboardMessage.user.toJson();
          //Logger.debug("breadboardMessage.out.write(breadboardMessage.user.toJson()); " + userJson);
          breadboardMessage.out.write(userJson);

          Logger.debug("breadboardMessage.user.email GET: " + breadboardMessage.user.email);
          instances.get(breadboardMessage.user.email).tell(new Refresh(breadboardMessage.user, breadboardMessage.out), null);
          return;
        }
        Logger.debug("LogIn: " + breadboardMessage.user.email);
      } else if (message instanceof SaveUserSettings) {
        SaveUserSettings saveUserSettings = (SaveUserSettings) message;

        //TODO: better validation like regex?
        List<String> errors = new ArrayList<String>();
        if (StringUtils.isEmpty(saveUserSettings.currentPassword)) {
          errors.add("'Current Password' is required");
        }
        if (!saveUserSettings.currentPassword.equals(saveUserSettings.user.password)) {
          errors.add("'Current Password' doesn't match the current password");
        }
        if (StringUtils.isEmpty(saveUserSettings.newPassword)) {
          errors.add("'New Password' is required");
        }
        if (!saveUserSettings.confirmPassword.equals(saveUserSettings.newPassword)) {
          errors.add("'Confirm Password' doesn't match the new password");
        }

        saveUserSettings.user.password = saveUserSettings.newPassword;
        saveUserSettings.user.update();

      } else if (message instanceof SelectExperiment) {
        // TODO: Clear the working memory of the ScriptEngine
        SelectExperiment selectExperiment = (SelectExperiment) message;
        Logger.debug("SelectExperiment: " + selectExperiment.experiment.toString());

        breadboardMessage.user.setSelectedExperiment(selectExperiment.experiment);
        breadboardMessage.user.update();

        // If there are steps associated with this experiment, load them
        Experiment selectedExperiment = breadboardMessage.user.getExperiment();
        if (selectedExperiment != null) {
          // TODO: Create "ChangeExperiment" action and handle it in ScriptBoard
          // Find Content based on currently selected Experiment
          instances.get(breadboardMessage.user.email).tell(new ChangeExperiment(breadboardMessage.user, selectedExperiment, breadboardMessage.out), null);
          //move the load steps into scriptboard
//                    for (Step step : selectedExperiment.steps)
//                        instances.get(breadboardMessage.user.email).tell(new RunStep(breadboardMessage.user, step.source, breadboardMessage.out));
        }
      } else if (message instanceof CreateExperiment) {
        CreateExperiment createExperiment = (CreateExperiment) message;
        Logger.debug("CreateExperiment: " + createExperiment.name);

        Experiment experiment = null;

        if (StringUtils.isNotEmpty(createExperiment.copyExperimentName)) {
          Experiment copyFrom = Experiment.findByName(createExperiment.copyExperimentName);
          if (copyFrom != null) {
            experiment = new Experiment(copyFrom);
            boolean foundOnJoin = false, foundOnLeave = false;
            for (Step step : experiment.steps) {
              if (Experiment.ON_JOIN_STEP_NAME.equals(step.name)) {
                foundOnJoin = true;
              } else if (Experiment.ON_LEAVE_STEP_NAME.equals(step.name)) {
                foundOnLeave = true;
              }
            }
            if (!foundOnJoin) {
              Step onJoin = Experiment.generateOnJoinStep();
              experiment.steps.add(onJoin);
            }
            if (!foundOnLeave) {
              Step onLeave = Experiment.generateOnLeaveStep();
              experiment.steps.add(onLeave);
            }
          }
        }
        if (experiment == null) {
          experiment = new Experiment();
          Step onJoin = Experiment.generateOnJoinStep();
          Step onLeave = Experiment.generateOnLeaveStep();
          Step init = Experiment.generateInitStep();
          experiment.steps.add(onJoin);
          experiment.steps.add(onLeave);
          experiment.steps.add(init);
          experiment.clientHtml = Experiment.defaultClientHTML();
          experiment.clientGraph = Experiment.defaultClientGraph();
        }
        experiment.name = createExperiment.name;
        experiment.save();

        breadboardMessage.user.ownedExperiments.add(experiment);
        breadboardMessage.user.update();
        breadboardMessage.user.saveManyToManyAssociations("ownedExperiments");

        // Select the newly created experiment
        breadboardController.tell(new SelectExperiment(breadboardMessage.user, experiment, breadboardMessage.out), null);
      } else if (message instanceof ImportExperiment) {
        ImportExperiment importExperiment = (ImportExperiment) message;
        Logger.debug("Importing experiment from " + importExperiment.importFrom + " to " + importExperiment.importTo);
        Experiment importedExperiment = new Experiment();
        importedExperiment.name = importExperiment.importTo;
        File experimentDirectory = new File(Play.application().path().toString() + "/experiments/" + importExperiment.importFrom);

        String style = FileUtils.readFileToString(new File(experimentDirectory, "style.css"));
        importedExperiment.style = style;

        String clientHtml = FileUtils.readFileToString(new File(experimentDirectory, "client.html"));
        importedExperiment.clientHtml = clientHtml;

        String clientGraph = FileUtils.readFileToString(new File(experimentDirectory, "client-graph.js"));
        importedExperiment.clientGraph = clientGraph;

        File stepsDirectory = new File(experimentDirectory, "/Steps");
        File[] stepFiles = stepsDirectory.listFiles();

        if (stepFiles != null) {
          for (File stepFile : stepFiles) {
            Step step = new Step();
            String stepName = FilenameUtils.removeExtension(stepFile.getName());
            String source = FileUtils.readFileToString(stepFile);
            step.name = stepName;
            step.source = source;
            importedExperiment.steps.add(step);
            Logger.debug("Adding step: " + stepName);
          }
        }

        File contentDirectory = new File(experimentDirectory, "/Content");
        File[] contentFiles = contentDirectory.listFiles();

        if (contentFiles != null) {
          for (File contentFile : contentFiles) {
            if (contentFile.isDirectory()) {
              // Language directory
              File[] languageFiles = contentFile.listFiles();
              for (File languageFile : languageFiles) {
                // For each file in the language directory with an extension equal to ".html"
                // The language is the same as the name of the containing directory
                if (languageFile.isFile() && FilenameUtils.getExtension(languageFile.getName()).equals("html")) {
                  // First, check if the language is in the database
                  Language language = Language.find.where().eq("code", contentFile.getName()).findUnique();

                  if (language == null) {
                    // Next, check if the language is in importedExperiment.languages
                    for (Language l : importedExperiment.languages) {
                      if (l.code.equals(contentFile.getName())) {
                        language = l;
                      }
                    }
                  }

                  if (language == null) {
                    // Language not found in database or importedExperiment.languages, create new language
                    Logger.debug("No language found, creating new language.");
                    language = new Language();
                    language.code = contentFile.getName();
                    // Try and determine the name of the Language from the code
                    language.name = new Locale(contentFile.getName()).getDisplayLanguage();
                    Logger.debug("The language based on the code " + contentFile.getName() + " is " + language.name);
                  }

                  boolean hasLanguage = false;
                  for (Language l : importedExperiment.languages) {
                    if (l.code.equals(contentFile.getName())) {
                      hasLanguage = true;
                    }
                  }
                  if (!hasLanguage) {
                    importedExperiment.languages.add(language);
                    importedExperiment.save();
                  }

                  Translation translation = new Translation();
                  translation.language = language;
                  String html = FileUtils.readFileToString(languageFile);
                  translation.html = html;

                  String contentName = FilenameUtils.removeExtension(languageFile.getName());
                  Content content = null;
                  for (Content c : importedExperiment.content) {
                    if (c.name.equals(contentName)) {
                      content = c;
                    }
                  }
                  if (content == null) {
                    content = new Content();
                    content.name = contentName;
                    importedExperiment.content.add(content);
                  }
                  Logger.debug("Adding content: " + contentName + " with language " + contentFile.getName());
                  content.translations.add(translation);
                }
              }
            } else if(contentFile.isFile() && FilenameUtils.getExtension(contentFile.getName()).equals("html")) {
              // Import from a v2.2.4 or earlier DB, let's assume the content is in English
              // First, check if english is in the database
              Language english = Language.find.where().eq("code", "en").findUnique();

              if (english == null) {
                // Next, check if the language is in importedExperiment.languages
                for (Language l : importedExperiment.languages) {
                  if (l.code.equals("en") && l.name.equals("English")) {
                    english = l;
                  }
                }
              }

              if (english == null) {
                // Not in the database or importedExperiment.languages, create a new language
                english = new Language();
                english.code = "en";
                english.name = "English";
                importedExperiment.languages.add(english);
                importedExperiment.save();
              }

              Translation translation = new Translation();
              translation.language = english;
              String html = FileUtils.readFileToString(contentFile);
              translation.html = html;

              Content content = new Content();
              String contentName = FilenameUtils.removeExtension(contentFile.getName());
              content.name = contentName;
              content.translations.add(translation);
              importedExperiment.content.add(content);
              Logger.debug("Adding content: " + contentName + " with default language en");
            }
          }
        }

        String ls = System.getProperty("line.separator");
        File parametersFile = new File(experimentDirectory, "parameters.csv");
        String parameters = FileUtils.readFileToString(parametersFile);
        String[] parametersLines = parameters.split(ls);

        if (parametersLines.length > 1) {
          for (int i = 1; i < parametersLines.length; i++) {
            String parameterLine = parametersLines[i];
            // TODO: Let's properly handle commas in the short description
            String[] parameterValues = parameterLine.split(",");
            if (parameterValues.length == 6) {
              Parameter parameter = new Parameter();
              String parameterName = parameterValues[0];
              Logger.debug("Adding parameter: " + parameterName);
              String parameterType = parameterValues[1];
              String parameterMinVal = parameterValues[2];
              String parameterMaxVal = parameterValues[3];
              String parameterDefaultVal = parameterValues[4];
              String parameterDescription = parameterValues[5];
              parameter.name = parameterName;
              parameter.type = parameterType;
              parameter.minVal = parameterMinVal;
              parameter.maxVal = parameterMaxVal;
              parameter.defaultVal = parameterDefaultVal;
              parameter.description = parameterDescription;
              importedExperiment.parameters.add(parameter);
            }
          }
        }

        File imagesDirectory = new File(experimentDirectory, "/Images");
        File[] imageFiles = imagesDirectory.listFiles();

        if (imageFiles != null) {
          for (File imageFile : imageFiles) {
            String imageName = FilenameUtils.removeExtension(imageFile.getName());
            byte[] imageBytes = FileUtils.readFileToByteArray(imageFile);
            Image image = new Image();
            image.fileName = imageFile.getName();
            image.file = imageBytes;
            image.contentType = "image/" + FilenameUtils.getExtension(imageFile.getName());

            // Create thumbnail
            InputStream in = new ByteArrayInputStream(image.file);
            BufferedImage bImage = ImageIO.read(in);
            BufferedImage scaledImage = Scalr.resize(bImage, 100);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(scaledImage, FilenameUtils.getExtension(imageFile.getName()), baos);
            baos.flush();

            byte[] thumbImageBytes = baos.toByteArray();
            image.thumbFile = thumbImageBytes;
            image.thumbFileName = (image.fileName).concat("_thumb");
            baos.close();

            importedExperiment.images.add(image);
            Logger.debug("Adding image: " + imageName);
          }
        }

        importedExperiment.save();

        breadboardMessage.user.ownedExperiments.add(importedExperiment);
        breadboardMessage.user.update();
        breadboardMessage.user.saveManyToManyAssociations("ownedExperiments");

        // Select the newly imported experiment
        breadboardController.tell(new SelectExperiment(breadboardMessage.user, importedExperiment, breadboardMessage.out), null);
      } else if (message instanceof SubmitAMTTask) {
        Logger.debug("message instanceof SubmitAMTTask");

        SubmitAMTTask submitAMTTask = (SubmitAMTTask) message;
        String title = submitAMTTask.title;
        String description = submitAMTTask.description;
        BigDecimal reward = submitAMTTask.reward;
        Integer lifetimeInSeconds = submitAMTTask.lifetimeInSeconds;
        Integer tutorialTime = submitAMTTask.tutorialTime;
        Integer maxAssignments = submitAMTTask.maxAssignments;
        String disallowPrevious = submitAMTTask.disallowPrevious;
        Boolean sandbox = submitAMTTask.sandbox;
        String experimentId = submitAMTTask.experimentId;
        String experimentInstanceId = submitAMTTask.experimentInstanceId;

        String rootURL = play.Play.application().configuration().getString("breadboard.rootUrl");
        Logger.debug("rootURL = " + rootURL);
        String gameURL = String.format("/game/%1$s/%2$s/amt", experimentId, experimentInstanceId);
        Logger.debug("gameURL = " + gameURL);
        String externalURL = rootURL + gameURL;
        Logger.debug("externalURL = " + externalURL);

        Integer frameHeight = play.Play.application().configuration().getInt("breadboard.amtFrameHeight");
        Logger.debug("frameHeight = " + frameHeight);

        Logger.info("SubmitAMTTask: ");
        Logger.info("\t title: " + title);
        Logger.info("\t description: " + description);
        Logger.info("\t reward: " + reward.toPlainString());
        Logger.info("\t lifetimeInSeconds: " + lifetimeInSeconds);
        Logger.info("\t tutorialTime: " + tutorialTime);
        Logger.info("\t maxAssignments: " + maxAssignments);
        Logger.info("\t disallowPrevious: " + disallowPrevious);
        Logger.info("\t sandbox: " + sandbox);
        Logger.info("\t experimentId: " + experimentId);
        Logger.info("\t experimentInstanceId: " + experimentInstanceId);
        Logger.info("\t externalURL: " + externalURL);

        Experiment experiment = Experiment.findById(Long.valueOf(experimentId));

        F.Promise<WS.Response> response = controllers.MechanicalTurk.createAMTHit(
            title,
            description,
            externalURL,
            frameHeight,
            reward,
            lifetimeInSeconds,
            maxAssignments,
            sandbox,
            null);

        String responseBody = response.get().getBody();
        Logger.debug("response: " + responseBody);


/* ResponseGroup: Minimal
<CreateHITResponse><OperationRequest><RequestId>46d2d2a3-3d07-43d1-9599-f77169c1
c920</RequestId></OperationRequest><HIT><Request><IsValid>True</IsValid></Reques
t><HITId>24ASCXKNQY2RG6N612ME6HR0T0SP0C</HITId><HITTypeId>22X2J1LY58B76UP0GJ6KKD
16Y20R65</HITTypeId></HIT></CreateHITResponse>
*/

/* ResponseGroup: Request
 <CreateHITResponse>
     <OperationRequest>
         <RequestId>74b8a338-1943-4569-93e9-b82aafcc 5c40</RequestId>
     </OperationRequest>

     <HIT>
         <Request>
             <IsValid>True</IsValid>
             <CreateHITRequest>
                 <AssignmentDurationInSeconds>900</AssignmentDurationInSeconds>
                 <Description>Testing</Description>
                 <LifetimeInSeconds>90</LifetimeInSeconds>
                 <MaxAssignments>1</MaxAssignments>
                 <Operation>CreateHIT</Operation>
                 <Question>&lt;ExternalQuestion xmlns="http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2006-07-14/ExternalQuestion.xsd"&gt;&lt;ExternalURL&gt;http://54.225.223.34:9000/game/34/621/amt&lt;/ExternalURL&gt;&lt;FrameHeight&gt;600&lt;/FrameHeight&gt;&lt;/ExternalQuestion&gt;</Question>
                 <ResponseGroup>Request</ResponseGroup>
                 <Reward tag="1"><Amount>1</Amount><CurrencyCode>USD</CurrencyCode></Reward>
                 <Title>HCP 4:27</Title>
             </CreateHITRequest>
         </Request>
     </HIT>
</CreateHITResponse>
*/
        // TODO: save hitId with ExperimentInstance and use to validate amtClientLogin
        Document dom = XML.fromString(responseBody);
        if (dom != null) {
          String requestId = XPath.selectText("//RequestId", dom);
          String isValid = XPath.selectText("//IsValid", dom);
          if (isValid.equals("True")) {
            String hitId = XPath.selectText("//HITId", dom);

            AMTHit amtHit = new AMTHit();
            amtHit.requestId = requestId;
            amtHit.isValid = isValid;
            amtHit.hitId = hitId;
            amtHit.description = description;
            amtHit.lifetimeInSeconds = lifetimeInSeconds.toString();
            amtHit.tutorialTime = tutorialTime.toString();
            amtHit.maxAssignments = maxAssignments.toString();
            amtHit.externalURL = externalURL;
            amtHit.reward = reward.toString();
            amtHit.title = title;
            amtHit.disallowPrevious = disallowPrevious;
            amtHit.sandbox = sandbox;

            ExperimentInstance experimentInstance = ExperimentInstance.findById(Long.parseLong(experimentInstanceId));
            experimentInstance.amtHits.add(amtHit);
            experimentInstance.save();

            instances.get(breadboardMessage.user.email).tell(new HitCreated(breadboardMessage.user, lifetimeInSeconds, tutorialTime, breadboardMessage.out), null);

            ObjectNode jsonOutput = Json.newObject();
            jsonOutput.put("output", "AMT HIT Created.");
            breadboardMessage.out.write(jsonOutput);
          } else {
            Logger.debug("isValid != True");
            ObjectNode jsonOutput = Json.newObject();
            jsonOutput.put("output", "There was a problem creating the HIT with request ID: " + requestId);
            breadboardMessage.out.write(jsonOutput);
          }
        }
      } else if (message instanceof GetAssignmentsForHIT) {
/*
<?xml version="1.0"?>
<GetAssignmentsForHITResponse>
    <OperationRequest>
        <RequestId>6d6deab2-eeea-4dd3-906c-f54de61b3ea2</RequestId>
    </OperationRequest>
    <GetAssignmentsForHITResult>
        <Request>
            <IsValid>True</IsValid>
        </Request>
        <NumResults>1</NumResults>
        <TotalNumResults>1</TotalNumResults>
        <PageNumber>1</PageNumber>
        <Assignment>
            <AssignmentId>2W0JIA0RYNJ96AMKK3WBPJTUI5G272</AssignmentId>
            <WorkerId>ARK0ERORKQBS1</WorkerId>
            <HITId>22H6R70G5R98HVM3THYWVSTN7SXAQ6</HITId>
            <AssignmentStatus>Submitted</AssignmentStatus>
            <AutoApprovalTime>2013-06-23T14:26:35Z</AutoApprovalTime>
            <AcceptTime>2013-05-24T13:47:18Z</AcceptTime>
            <SubmitTime>2013-05-24T14:26:35Z</SubmitTime>
            <Answer>&lt;? xml version="1.0" encoding="UTF-8" standalone="no"?&gt;&lt;QuestionFormAnswers xmlns="http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2005-10-01/QuestionFormAnswers.xsd"&gt;&lt;Answer&gt;&lt;QuestionIdentifier&gt;bonus&lt;/QuestionIdentifier&gt;&lt;FreeText&gt;0.5&lt;/FreeText&gt;&lt;/Answer&gt;&lt;/QuestionFormAnswers&gt;</Answer>
        </Assignment>
    </GetAssignmentsForHITResult>
</GetAssignmentsForHITResponse>
*/
        GetAssignmentsForHIT getAssignmentsForHIT = (GetAssignmentsForHIT) message;

        AMTHit hit = AMTHit.findById(getAssignmentsForHIT.amtHitId);

        if (hit != null) {
          F.Promise<WS.Response> response = controllers.MechanicalTurk.getAssignmentsForHIT(hit.hitId, hit.sandbox);

          if (response != null) {
            String responseBody = response.get().getBody();
            Logger.debug("response: " + responseBody);

            Document dom = XML.fromString(responseBody);
            if (dom != null) {
              ObjectNode jsonOutput = Json.newObject();

              NodeList assignmentNodes = XPath.selectNodes("//Assignment", dom);

              for (int i = 0; i < assignmentNodes.getLength(); i++) {
                Node assignmentNode = assignmentNodes.item(i);

                String assignmentId = XPath.selectText("AssignmentId", assignmentNode);
                String workerId = XPath.selectText("WorkerId", assignmentNode);
                String assignmentStatus = XPath.selectText("AssignmentStatus", assignmentNode);
                String autoApprovalTime = XPath.selectText("AutoApprovalTime", assignmentNode);
                String acceptTime = XPath.selectText("AcceptTime", assignmentNode);
                String submitTime = XPath.selectText("SubmitTime", assignmentNode);
                String answer = XPath.selectText("Answer", assignmentNode);
                //String unescapedAnswer = StringEscapeUtils.unescapeXml(answer);
                Document answerDom = XML.fromString(answer);
                //Logger.debug("answerDom.getElementsByTagName(\"FreeText\").getLength() = " + answerDom.getElementsByTagName("FreeText").getLength());

                //Logger.debug("answer = " + answer);
                //Logger.debug("unescapedAnswer = " + unescapedAnswer);
/*
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<QuestionFormAnswers xmlns="http://mechanicalturk.amazonaws.com/AWSMechanicalTurkDataSchemas/2005-10-01/QuestionFormAnswers.xsd">
<Answer>
<QuestionIdentifier>bonus</QuestionIdentifier>
<FreeText>0.85</FreeText>
</Answer>
</QuestionFormAnswers>
*/
                //String bonus = XPath.selectText("/QuestionFormAnswers/Answer/FreeText", answerDom);
                String strategy = "";
                String bonus = "";
                String reason = "";
                NodeList questionIdentifiers = answerDom.getElementsByTagName("QuestionIdentifier");

                for (int j = 0; j < questionIdentifiers.getLength(); j++) {
                  if (questionIdentifiers.item(j).getTextContent().equals("bonus")) {
                    if (questionIdentifiers.item(j).getNextSibling().getNextSibling() != null) {
                      bonus = questionIdentifiers.item(j).getNextSibling().getNextSibling().getTextContent();
                    }
                  }
                  if (questionIdentifiers.item(j).getTextContent().equals("reason")) {
                    if (questionIdentifiers.item(j).getNextSibling().getNextSibling() != null) {
                      reason = questionIdentifiers.item(j).getNextSibling().getNextSibling().getTextContent();
                    }
                  }
                }

                Logger.debug("AssignmentId = " + assignmentId);
                Logger.debug("WorkerId = " + workerId);
                Logger.debug("AssignmentStatus = " + assignmentStatus);
                Logger.debug("AutoApprovalTime = " + autoApprovalTime);
                Logger.debug("AcceptTime = " + acceptTime);
                Logger.debug("SubmitTime = " + submitTime);
                //Logger.debug("Answer = " + answer);
                Logger.debug("Bonus = " + bonus);
                Logger.debug("Reason = " + reason);

                //AMTAssignment assignment = AMTAssignment.findByAssignmentId(assignmentId);
                AMTAssignment assignment = hit.getAMTAssignmentById(assignmentId);
                boolean update = true;

                if (assignment == null) {
                  assignment = new AMTAssignment();
                  update = false;
                }

                assignment.assignmentId = assignmentId;
                assignment.workerId = workerId;
                assignment.assignmentStatus = assignmentStatus;
                assignment.autoApprovalTime = autoApprovalTime;
                assignment.acceptTime = acceptTime;
                assignment.submitTime = submitTime;
                assignment.answer = answer;
                assignment.score = bonus;
                assignment.reason = reason;

                if (!update) {
                  hit.amtAssignments.add(assignment);
                }
                hit.save();
              }
              jsonOutput.put("output", "Assignments Retrieved.");
              breadboardMessage.out.write(jsonOutput);
            } // if (dom != null)
          } // if (response != null)
        } // if (hit != null)
      } else if (message instanceof ExtendHit) {
        ExtendHit extendHit = (ExtendHit) message;

        AMTHit hit = AMTHit.findById(extendHit.amtHitId);

        if (hit != null) {
          Logger.debug("ExtendHit: " + hit);
          F.Promise<WS.Response> response = controllers.MechanicalTurk.extendHit(hit.hitId, 31536000, hit.sandbox);

          if (response != null) {
            String responseBody = response.get().getBody();
            Logger.debug("response: " + responseBody);

            Document dom = XML.fromString(responseBody);
            if (dom != null) {
              String isValid = XPath.selectText("//IsValid", dom);
              if (isValid.equals("True")) {
                hit.setExtended(true);
                hit.update();
              }
            }
          }
        }
      } else if (message instanceof GrantBonus) {
        GrantBonus grantBonus = (GrantBonus) message;

        Logger.debug("GrantBonus: " + grantBonus.assignmentId);
        AMTAssignment assignment = AMTAssignment.findByAssignmentId(grantBonus.assignmentId);

        if (assignment != null) {
          boolean sandbox = assignment.amtHit.sandbox;
          F.Promise<WS.Response> response = controllers.MechanicalTurk.grantBonus(grantBonus.workerId, grantBonus.assignmentId, grantBonus.bonus, sandbox);
          if (response != null) {
            String responseBody = response.get().getBody();
            Logger.debug("response: " + responseBody);

            Document dom = XML.fromString(responseBody);
            if (dom != null) {
              String isValid = XPath.selectText("//IsValid", dom);
              if (isValid.equals("True")) {
                assignment.bonusGranted = true;
                assignment.save();
                ObjectNode jsonOutput = Json.newObject();
                jsonOutput.put("output", "Bonus Granted: " + grantBonus.bonus);
                breadboardMessage.out.write(jsonOutput);
              }
            } //if (dom != null)
          } //if (response != null)
        } //if (assignment != null)
      } else if (message instanceof ApproveAssignment) {
        ApproveAssignment approveAssignment = (ApproveAssignment) message;

        Logger.debug("ApproveAssignment: " + approveAssignment.assignmentId);
        AMTAssignment assignment = AMTAssignment.findByAssignmentId(approveAssignment.assignmentId);

        if (assignment != null) {
          boolean sandbox = assignment.amtHit.sandbox;
          F.Promise<WS.Response> response = controllers.MechanicalTurk.approveAssignment(approveAssignment.assignmentId, sandbox);
          if (response != null) {
            String responseBody = response.get().getBody();
            Logger.debug("response: " + responseBody);

            Document dom = XML.fromString(responseBody);
            if (dom != null) {
              String isValid = XPath.selectText("//IsValid", dom);
              if (isValid.equals("True")) {
                assignment.assignmentStatus = "Approved";
                assignment.save();
                ObjectNode jsonOutput = Json.newObject();
                jsonOutput.put("output", "Assignment Approved: " + approveAssignment.assignmentId);
                breadboardMessage.out.write(jsonOutput);
                // Refresh the assignment status
                // TODO: why doesn't the status update when approve or reject assignment?
                //breadboardController.tell(new GetAssignmentsForHIT(breadboardMessage.user, assignment.amtHit.id, breadboardMessage.out));
              }
            } //if (dom != null)
          } //if (response != null)
        } //if (assignment != null)
      } else if (message instanceof RejectAssignment) {
        RejectAssignment rejectAssignment = (RejectAssignment) message;
        AMTAssignment assignment = AMTAssignment.findByAssignmentId(rejectAssignment.assignmentId);

        if (assignment != null) {
          boolean sandbox = assignment.amtHit.sandbox;
          Logger.debug("controllers.MechanicalTurk.rejectAssignment(" + rejectAssignment.assignmentId + ", " + sandbox + ")");
          F.Promise<WS.Response> response = controllers.MechanicalTurk.rejectAssignment(rejectAssignment.assignmentId, sandbox);
          if (response != null) {
            String responseBody = response.get().getBody();
            Logger.debug("response: " + responseBody);

            Document dom = XML.fromString(responseBody);
            if (dom != null) {
              String isValid = XPath.selectText("//IsValid", dom);
              if (isValid.equals("True")) {
                assignment.assignmentStatus = "Rejected";
                assignment.save();
                ObjectNode jsonOutput = Json.newObject();
                jsonOutput.put("output", "Assignment Rejected: " + rejectAssignment.assignmentId);
                breadboardMessage.out.write(jsonOutput);
                // Refresh the assignment status
                //breadboardController.tell(new GetAssignmentsForHIT(breadboardMessage.user, assignment.amtHit.id, breadboardMessage.out));
              }
            } //if (dom != null)
          } //if (response != null)
        } //if (assignment != null)
      } else if (message instanceof BlockWorker) {
        BlockWorker blockWorker = (BlockWorker) message;

        Logger.debug("BlockWorker: " + blockWorker.assignmentId);
        AMTAssignment assignment = AMTAssignment.findByAssignmentId(blockWorker.assignmentId);

        if (assignment != null) {
          boolean sandbox = assignment.amtHit.sandbox;
          F.Promise<WS.Response> response = controllers.MechanicalTurk.blockWorker(assignment.workerId, sandbox);
          if (response != null) {
            String responseBody = response.get().getBody();
            Logger.debug("response: " + responseBody);

            Document dom = XML.fromString(responseBody);
            if (dom != null) {
              String isValid = XPath.selectText("//IsValid", dom);
              if (isValid.equals("True")) {
                assignment.workerBlocked = true;
                assignment.save();
                ObjectNode jsonOutput = Json.newObject();
                jsonOutput.put("output", "Worker Blocked: " + assignment.workerId);
                breadboardMessage.out.write(jsonOutput);
              }
            } //if (dom != null)
          } //if (response != null)
        } //if (assignment != null)
      } else if (message instanceof MarkCompleted) {
        MarkCompleted markCompleted = (MarkCompleted) message;

        Logger.debug("MarkCompleted: " + markCompleted.assignmentId);
        AMTAssignment assignment = AMTAssignment.findByAssignmentId(markCompleted.assignmentId);

        if (assignment != null) {
          assignment.assignmentCompleted = true;
          assignment.save();
          ObjectNode jsonOutput = Json.newObject();
          jsonOutput.put("output", "Assignment marked as completed: " + markCompleted.assignmentId);
          breadboardMessage.out.write(jsonOutput);
        } //if (assignment != null)
      } else if (message instanceof AssignQualification) {
        AssignQualification assignQualification = (AssignQualification) message;

        Logger.debug("AssignQualification: " + assignQualification.assignmentId);
        AMTAssignment assignment = AMTAssignment.findByAssignmentId(assignQualification.assignmentId);

        if (assignment != null) {
          AMTHit amtHit = assignment.amtHit;
          if (amtHit != null) {
            ExperimentInstance experimentInstance = amtHit.experimentInstance;
            if (experimentInstance != null) {
              Experiment experiment = experimentInstance.experiment;
              if (experiment != null) {
                boolean sandbox = assignment.amtHit.sandbox;

                String generalQualificationTypeId = (sandbox) ? "2IO7WGKCCVLL7ZD31P8ASQ4C9QO4U5" : "23ADM25L8I9N3J9HC2XOL4QW7W9HR7";
                String experimentQualificationTypeId = (sandbox) ? experiment.qualificationTypeIdSandbox : experiment.qualificationTypeId;

                boolean generalSuccess = controllers.MechanicalTurk.assignOrUpdateQualification(generalQualificationTypeId, assignment.workerId, "1", sandbox);
                boolean experimentSuccess = controllers.MechanicalTurk.assignOrUpdateQualification(experimentQualificationTypeId, assignment.workerId, "1", sandbox);

                if (generalSuccess && experimentSuccess) {
                  assignment.qualificationAssigned = true;
                  assignment.save();
                  ObjectNode jsonOutput = Json.newObject();
                  jsonOutput.put("output", "Qualification Assigned to " + assignment.workerId);
                  breadboardMessage.out.write(jsonOutput);
                } else {
                  if (!generalSuccess) {
                    Logger.debug("Failed assigning and updating general qualification type.");
                  }

                  if (!experimentSuccess) {
                    Logger.debug("Failed assigning and updating experiment specific qualification type.");
                  }
                }
              } //if (experiment != null)
            } //if (experimentInstance != null)
          } //if (amtHit != null)
        } //if (assignment != null)
      } else if (message instanceof AddLanguage) {
        AddLanguage addLanguage = (AddLanguage) message;
        Language language = Language.find.where().eq("code", addLanguage.languageCode).findUnique();
        if (language == null) {
          // New language, create it
          language = new Language();
          language.code = addLanguage.languageCode;
          language.name = new Locale(addLanguage.languageCode).getDisplayLanguage();
        }
        // Add the language to the currently selected Experiment, if it doesn't already exist
        Experiment selectedExperiment = Experiment.findById(addLanguage.experimentId);
        boolean hasLanguage = false;
        for (Language l : selectedExperiment.languages) {
          if (l.id == language.id) {
            hasLanguage = true;
          }
        }
        if (!hasLanguage) {
          selectedExperiment.languages.add(language);
          selectedExperiment.save();
        }
        instances.get(breadboardMessage.user.email).tell(message, null);
      } else if (message instanceof CreateContent) {
        // TODO: Think about disallowing Content with the same name in the same Experiment
        CreateContent createContent = (CreateContent) message;
        Logger.debug("CreateContent: " + createContent.name);
        Experiment selectedExperiment = breadboardMessage.user.getExperiment();
        if (selectedExperiment != null) {
          Content newContent = new Content();
          newContent.name = createContent.name;
          //newContent.save();

          selectedExperiment.content.add(newContent);
          selectedExperiment.save();
          //selectedExperiment.saveManyToManyAssociations("content");
          instances.get(breadboardMessage.user.email).tell(message, null);
        }
      } else if (message instanceof CreateStep) {
        CreateStep createStep = (CreateStep) message;
        Logger.debug("CreateStep: " + createStep.name);
        Experiment selectedExperiment = breadboardMessage.user.getExperiment();
        if (selectedExperiment != null) {
          Step newStep = new Step();
          newStep.name = createStep.name;
          String nameVariableName = WordUtils.uncapitalize(newStep.name.replaceAll("[^a-zA-Z0-9\\s]", ""));
          newStep.source = nameVariableName + " = stepFactory.createStep()\n\n" +
              nameVariableName + ".run = {\n" +
              "\tprintln \"" + nameVariableName + ".run\"\n" +
              "}\n\n" +
              nameVariableName + ".done = {\n" +
              "\tprintln \"" + nameVariableName + ".done\"\n" +
              "}\n";
          //newStep.save();

          selectedExperiment.steps.add(newStep);
          selectedExperiment.save();
          //selectedExperiment.saveManyToManyAssociations("steps");
        }
      } else if (message instanceof DeleteStep) {
        DeleteStep deleteStep = (DeleteStep) message;
        Step step = Step.find.byId(deleteStep.id);
        step.delete();
      } else if (message instanceof DeleteContent) {
        DeleteContent deleteContent = (DeleteContent) message;
        Content content = Content.find.byId(deleteContent.id);
        //Logger.debug("message instanceof DeleteContent, content=" + content);
        content.delete();
      } else if (message instanceof SaveContent) {
        SaveContent saveContent = (SaveContent) message;
        Experiment selectedExperiment = breadboardMessage.user.getExperiment();
        if (selectedExperiment != null) {
          Content content = selectedExperiment.getContent(saveContent.contentId);
          if (content != null) {
            for (Translation t : saveContent.translations) {
              if (t.id == null) {
                // New translation, create it
                Translation newTranslation = new Translation();
                newTranslation.language = t.getLanguage();
                newTranslation.html = t.getHtml();
                content.translations.add(newTranslation);
                content.save();
              } else {
                // Existing translation, update it
                Translation translation = Translation.find.byId(t.id);
                translation.setHtml(t.getHtml());
                translation.update();
              }
            }

            instances.get(breadboardMessage.user.email).tell(message, null);

            // Send "Content saved" message to output
            ObjectNode jsonOutput = Json.newObject();
            jsonOutput.put("output", "Content saved.");
            breadboardMessage.out.write(jsonOutput);
          }
        }
      } else if (message instanceof SaveStyle) {
        SaveStyle saveStyle = (SaveStyle) message;
        Experiment selectedExperiment = breadboardMessage.user.getExperiment();
        if (selectedExperiment != null) {
          Logger.debug("SaveStyle: " + saveStyle.style);
          selectedExperiment.setStyle(saveStyle.style);
          selectedExperiment.update();
          // Send "Style saved" message to output
          ObjectNode jsonOutput = Json.newObject();
          jsonOutput.put("output", "Style saved.");
          breadboardMessage.out.write(jsonOutput);
        }
      } else if (message instanceof SaveClientHtml) {
        SaveClientHtml saveClientHtml = (SaveClientHtml) message;
        Experiment selectedExperiment = breadboardMessage.user.getExperiment();
        if (selectedExperiment != null) {
          Logger.debug("SaveClientHtml: " + saveClientHtml.clientHtml);
          selectedExperiment.setClientHtml(saveClientHtml.clientHtml);
          selectedExperiment.update();
          ObjectNode jsonOutput = Json.newObject();
          jsonOutput.put("output", "Client HTML saved.");
          breadboardMessage.out.write(jsonOutput);
          // TODO: Consider pushing update to all clients here, to prevent need for browser refresh.
        }
      } else if (message instanceof SaveClientGraph) {
        SaveClientGraph saveClientGraph = (SaveClientGraph) message;
        Experiment selectedExperiment = breadboardMessage.user.getExperiment();
        if (selectedExperiment != null) {
          Logger.debug("SaveClientGraph: " + saveClientGraph.clientGraph);
          selectedExperiment.setClientGraph(saveClientGraph.clientGraph);
          selectedExperiment.update();
          ObjectNode jsonOutput = Json.newObject();
          jsonOutput.put("output", "Client Graph saved.");
          breadboardMessage.out.write(jsonOutput);
          // TODO: Consider pushing update to all clients here, to prevent need for browser refresh.
        }
      } else if (message instanceof NewParameter) {
        NewParameter newParameter = (NewParameter) message;

        Experiment selectedExperiment = breadboardMessage.user.getExperiment();

        if (selectedExperiment != null) {
          Parameter parameter = new Parameter();
          parameter.name = newParameter.name;
          parameter.type = newParameter.type;
          parameter.minVal = (parameter.type.equals("Text") || parameter.type.equals("Boolean")) ? "" : newParameter.minVal;
          parameter.maxVal = (parameter.type.equals("Text") || parameter.type.equals("Boolean")) ? "" : newParameter.maxVal;
          parameter.defaultVal = newParameter.defaultVal;
          parameter.description = newParameter.description;
          //parameter.save();

          selectedExperiment.parameters.add(parameter);
          selectedExperiment.save();
          //selectedExperiment.saveManyToManyAssociations("parameters");
        }
      } else if (message instanceof RemoveParameter) {
        RemoveParameter removeParameter = (RemoveParameter) message;

        Experiment selectedExperiment = breadboardMessage.user.getExperiment();

        if (selectedExperiment != null) {
          Parameter parameter = Parameter.find.byId(removeParameter.id);
          if (parameter != null) {
            selectedExperiment.parameters.remove(parameter);
            //selectedExperiment.saveManyToManyAssociations("parameters");
            selectedExperiment.update();
            parameter.delete();
          }
        }
      } else if (message instanceof SelectInstance) {
        instances.get(breadboardMessage.user.email).tell(message, null);
      } else if (message instanceof Update) {
        //Do nothing, just update the user JSON object
      } else if (message instanceof ShowEvent) {
        ShowEvent showEvent = (ShowEvent) message;
        Long experimentInstanceId = showEvent.experimentInstanceId;
        ExperimentInstance experimentInstance = ExperimentInstance.findById(experimentInstanceId);
        List<Event> events = experimentInstance.events;
        Collections.sort(events, new Comparator<Event>() {
          @Override
          public int compare(Event o1, Event o2) {
            return o1.datetime.compareTo(o2.datetime);
          }
        });

        ObjectNode eventNode = Json.newObject();
        eventNode.put("eventExperimentInstance", experimentInstance.name);
        ArrayNode arrayNode = eventNode.putArray("events");

        for (Event event : events) {
          List<EventData> eventDatas = event.eventData;
          ObjectNode objectNode = Json.newObject();
          objectNode.put("id", event.id);
          objectNode.put("name", event.name);
          objectNode.put("datetime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,S").format(event.datetime));
          ArrayNode dataNodes = objectNode.putArray("data");
          for (EventData eventData : eventDatas) {
            ObjectNode dataNode = Json.newObject();
            dataNode.put("id", eventData.id);
            dataNode.put("name", eventData.name);
            dataNode.put("value", eventData.value);
            dataNodes.add(dataNode);
          }
          arrayNode.add(objectNode);
        }
        Logger.debug("breadboardMessage.out.write(eventNode); " + eventNode);
        breadboardMessage.out.write(eventNode);
        return;
      } else if (message instanceof DeleteInstance) {
        DeleteInstance deleteInstance = (DeleteInstance) message;
        Long experimentInstanceId = deleteInstance.experimentInstanceId;
        ExperimentInstance experimentInstance = ExperimentInstance.findById(experimentInstanceId);
        //only finished and stopped instance can be deleted
        if (experimentInstance.status == ExperimentInstance.Status.FINISHED || experimentInstance.status == ExperimentInstance.Status.STOPPED) {
          experimentInstance.delete();
        }
      } else if (message instanceof DeleteImage) {
        DeleteImage deleteImage = (DeleteImage) message;
        Long imageId = deleteImage.imageId;
        Image.findById(imageId).delete();
      }

      //Logger.debug("breadboardMessage.out.write(breadboardMessage.user.toJson()); " + breadboardMessage.user.toJson());
      breadboardMessage.out.write(breadboardMessage.user.toJson());

    } // END if(message instanceof BreadboardMessage)
  }

  public static abstract class BreadboardMessage {
    final User user;
    final ThrottledWebSocketOut out;

    public BreadboardMessage(User user, ThrottledWebSocketOut out) {
      this.user = user;
      this.out = out;
    }
  }

  public static class LogIn extends BreadboardMessage {
    public LogIn(User user, ThrottledWebSocketOut out) {
      super(user, out);
    }
  }

  public static class Refresh extends BreadboardMessage {
    public Refresh(User user, ThrottledWebSocketOut out) {
      super(user, out);
    }
  }

  public static class Update extends BreadboardMessage {
    public Update(User user, ThrottledWebSocketOut out) {
      super(user, out);
    }
  }

  public static class SaveUserSettings extends BreadboardMessage {

    final String currentPassword;
    final String newPassword;
    final String confirmPassword;

    public SaveUserSettings(User user, String currentPassword, String newPassword, String confirmPassword, ThrottledWebSocketOut out) {
      super(user, out);
      this.currentPassword = currentPassword;
      this.newPassword = newPassword;
      this.confirmPassword = confirmPassword;
    }
  }

  public static class SelectExperiment extends BreadboardMessage {
    final Experiment experiment;

    public SelectExperiment(User user, Experiment experiment, ThrottledWebSocketOut out) {
      super(user, out);
      this.experiment = experiment;
    }
  }

  public static class DeleteExperiment extends BreadboardMessage {
    final String experimentName;

    public DeleteExperiment(User user, String experimentName, ThrottledWebSocketOut out) {
      super(user, out);
      this.experimentName = experimentName;
    }
  }

  public static class ExportExperiment extends BreadboardMessage {
    final String experimentName;

    public ExportExperiment(User user, String experimentName, ThrottledWebSocketOut out) {
      super(user, out);
      this.experimentName = experimentName;
    }
  }

  public static class ChangeExperiment extends BreadboardMessage {
    final Experiment experiment;

    public ChangeExperiment(User user, Experiment experiment, ThrottledWebSocketOut out) {
      super(user, out);
      this.experiment = experiment;
    }
  }

  public static class CreateExperiment extends BreadboardMessage {
    final String name;
    final String copyExperimentName;

    public CreateExperiment(User user, String name, String copyExperimentName, ThrottledWebSocketOut out) {
      super(user, out);
      this.name = name;
      this.copyExperimentName = copyExperimentName;
    }
  }

  public static class ImportExperiment extends BreadboardMessage {
    final String importFrom;
    final String importTo;

    public ImportExperiment(User user, String importFrom, String importTo, ThrottledWebSocketOut out) {
      super(user, out);
      this.importFrom = importFrom;
      this.importTo = importTo;
    }
  }

  public static class SubmitAMTTask extends BreadboardMessage {
    final String title;
    final String description;
    final BigDecimal reward;
    final Integer lifetimeInSeconds;
    final Integer tutorialTime;
    final Integer maxAssignments;
    final String disallowPrevious;
    final Boolean sandbox;
    final String experimentId;
    final String experimentInstanceId;

    public SubmitAMTTask(User user, String title, String description, BigDecimal reward, Integer lifetimeInSeconds, Integer tutorialTime, Integer maxAssignments, String disallowPrevious, Boolean sandbox, String experimentId, String experimentInstanceId, ThrottledWebSocketOut out) {
      super(user, out);
      this.title = title;
      this.description = description;
      this.reward = reward;
      this.lifetimeInSeconds = lifetimeInSeconds;
      this.tutorialTime = tutorialTime;
      this.maxAssignments = maxAssignments;
      this.disallowPrevious = disallowPrevious;
      this.sandbox = sandbox;
      this.experimentId = experimentId;
      this.experimentInstanceId = experimentInstanceId;
    }
  }

  public static class HitCreated extends BreadboardMessage {
    final Integer lifetimeInSeconds;
    final Integer tutorialTime;

    public HitCreated(User user, Integer lifetimeInSeconds, Integer tutorialTime, ThrottledWebSocketOut out) {
      super(user, out);
      this.lifetimeInSeconds = lifetimeInSeconds;
      this.tutorialTime = tutorialTime;
    }
  }

  public static class GetAssignmentsForHIT extends BreadboardMessage {
    final Long amtHitId;

    public GetAssignmentsForHIT(User user, Long amtHitId, ThrottledWebSocketOut out) {
      super(user, out);
      this.amtHitId = amtHitId;
    }
  }

  public static class ExtendHit extends BreadboardMessage {
    final Long amtHitId;

    public ExtendHit(User user, Long amtHitId, ThrottledWebSocketOut out) {
      super(user, out);
      this.amtHitId = amtHitId;
    }
  }

  public static class GrantBonus extends BreadboardMessage {
    final String workerId;
    final String assignmentId;
    final String bonus;

    public GrantBonus(User user, String workerId, String assignmentId, String bonus, ThrottledWebSocketOut out) {
      super(user, out);
      this.workerId = workerId;
      this.assignmentId = assignmentId;
      this.bonus = bonus;
    }
  }

  public static class ApproveAssignment extends BreadboardMessage {
    final String assignmentId;

    public ApproveAssignment(User user, String assignmentId, ThrottledWebSocketOut out) {
      super(user, out);
      this.assignmentId = assignmentId;
    }
  }

  public static class RejectAssignment extends BreadboardMessage {
    final String assignmentId;

    public RejectAssignment(User user, String assignmentId, ThrottledWebSocketOut out) {
      super(user, out);
      this.assignmentId = assignmentId;
    }
  }

  public static class MarkCompleted extends BreadboardMessage {
    final String assignmentId;

    public MarkCompleted(User user, String assignmentId, ThrottledWebSocketOut out) {
      super(user, out);
      this.assignmentId = assignmentId;
    }
  }

  public static class BlockWorker extends BreadboardMessage {
    final String assignmentId;

    public BlockWorker(User user, String assignmentId, ThrottledWebSocketOut out) {
      super(user, out);
      this.assignmentId = assignmentId;
    }
  }

  public static class AssignQualification extends BreadboardMessage {
    final String assignmentId;

    public AssignQualification(User user, String assignmentId, ThrottledWebSocketOut out) {
      super(user, out);
      this.assignmentId = assignmentId;
    }
  }

  public static class CreateContent extends BreadboardMessage {
    final String name;

    public CreateContent(User user, String name, ThrottledWebSocketOut out) {
      super(user, out);
      this.name = name;
    }
  }

  public static class AddLanguage extends BreadboardMessage {
    final Long experimentId;
    final String languageCode;

    public AddLanguage(User user, Long experimentId, String languageCode, ThrottledWebSocketOut out) {
      super(user, out);
      this.experimentId = experimentId;
      this.languageCode = languageCode;
    }
  }

  public static class CreateStep extends BreadboardMessage {
    final String name;

    public CreateStep(User user, String name, ThrottledWebSocketOut out) {
      super(user, out);
      this.name = name;
    }
  }

  public static class DeleteStep extends BreadboardMessage {
    final Long id;

    public DeleteStep(User user, Long id, ThrottledWebSocketOut out) {
      super(user, out);
      this.id = id;
    }
  }

  public static class DeleteContent extends BreadboardMessage {
    final Long id;

    public DeleteContent(User user, Long id, ThrottledWebSocketOut out) {
      super(user, out);
      this.id = id;
    }
  }

  public static class SaveContent extends BreadboardMessage {
    final Long contentId;
    final String name;
    final List<Translation> translations;

    public SaveContent(User user, Long contentId, String name, List<Translation> translations, ThrottledWebSocketOut out) {
      super(user, out);
      this.contentId = contentId;
      this.name = name;
      this.translations = translations;
    }
  }

  public class SaveContentObject implements Serializable {
    @JsonCreator
    public SaveContentObject(@JsonProperty("action") String action,
                             @JsonProperty("uid") String uid,
                             @JsonProperty("contentId") long contentId,
                             @JsonProperty("name") String name,
                             @JsonProperty("translations") List<Translation> translations) {
      this.action = action;
      this.uid = uid;
      this.contentId = contentId;
      this.name = name;
      this.translations = translations;
    }
    private String action;
    private String uid;
    private long contentId;
    private String name;
    @JsonProperty("translations")
    private List<Translation> translations;

    @JsonGetter("action")
    public String getAction() {
      return action;
    }

    @JsonSetter("action")
    public void setAction(String action) {
      this.action = action;
    }

    @JsonGetter("uid")
    public String getUid() {
      return uid;
    }

    @JsonSetter("uid")
    public void setUid(String uid) {
      this.uid = uid;
    }

    @JsonGetter("contentId")
    public long getContentId() {
      return contentId;
    }

    @JsonSetter("contentId")
    public void setContentId(long contentId) {
      this.contentId = contentId;
    }

    @JsonGetter("name")
    public String getName() {
      return name;
    }

    @JsonSetter("name")
    public void setName(String name) {
      this.name = name;
    }

    @JsonGetter("translations")
    public List<Translation> getTranslations() {
      return translations;
    }

    @JsonSetter("translations")
    public void setTranslations(List<Translation> translations) {
      this.translations = translations;
    }
  }

  public static class SaveStyle extends BreadboardMessage {
    final String style;

    public SaveStyle(User user, String style, ThrottledWebSocketOut out) {
      super(user, out);
      this.style = style;
    }
  }

  public static class SaveClientHtml extends BreadboardMessage {
    final String clientHtml;

    public SaveClientHtml(User user, String clientHtml, ThrottledWebSocketOut out) {
      super(user, out);
      this.clientHtml = clientHtml;
    }
  }

  public static class SaveClientGraph extends BreadboardMessage {
    final String clientGraph;

    public SaveClientGraph(User user, String clientGraph, ThrottledWebSocketOut out) {
      super(user, out);
      this.clientGraph = clientGraph;
    }
  }

  public static class RunGame extends BreadboardMessage {
    public RunGame(User user, ThrottledWebSocketOut out) {
      super(user, out);
    }
  }

  public static class SendScript extends BreadboardMessage {
    final String script;

    public SendScript(User user, String script, ThrottledWebSocketOut out) {
      super(user, out);
      this.script = script;
    }
  }

  public static class SendStep extends BreadboardMessage {
    final Long id;
    final String name;
    final String source;

    public SendStep(User user, Long id, String name, String source, ThrottledWebSocketOut out) {
      super(user, out);
      this.id = id;
      this.name = name;
      this.source = source;
    }
  }

  public static class RunStep extends BreadboardMessage {
    final String source;

    public RunStep(User user, String source, ThrottledWebSocketOut out) {
      super(user, out);
      this.source = source;
    }
  }

  public static class MakeChoice extends BreadboardMessage {
    final String uid;
    final String params;

    public MakeChoice(User user, String uid, String params, ThrottledWebSocketOut out) {
      super(user, out);
      this.uid = uid;
      this.params = params;
    }
  }

  public static class DropPlayer extends BreadboardMessage {
    final String pid;

    public DropPlayer(User user, String pid, ThrottledWebSocketOut out) {
      super(user, out);
      this.pid = pid;
    }
  }

  public static class LaunchGame extends BreadboardMessage {
    final String name;
    final LinkedHashMap parameters;

    public LaunchGame(User user, String name, LinkedHashMap parameters, ThrottledWebSocketOut out) {
      super(user, out);
      this.name = name;
      this.parameters = parameters;
    }
  }

  public static class TestGame extends BreadboardMessage {
    final LinkedHashMap parameters;
    final String name;

    public TestGame(User user, String name, LinkedHashMap parameters, ThrottledWebSocketOut out) {
      super(user, out);
      this.name = name;
      this.parameters = parameters;
    }
  }

  public static class StopGame extends BreadboardMessage {
    final Long id;

    public StopGame(User user, Long id, ThrottledWebSocketOut out) {
      super(user, out);
      this.id = id;
    }
  }

  public static class NewParameter extends BreadboardMessage {
    final String name;
    final String type;
    final String minVal;
    final String maxVal;
    final String defaultVal;
    final String description;

    public NewParameter(User user, String name, String type, String minVal, String maxVal, String defaultVal, String description, ThrottledWebSocketOut out) {
      super(user, out);
      this.name = name;
      this.type = type;
      this.minVal = minVal;
      this.maxVal = maxVal;
      this.defaultVal = defaultVal;
      this.description = description;
    }
  }

  public static class RemoveParameter extends BreadboardMessage {
    final String id;

    public RemoveParameter(User user, String id, ThrottledWebSocketOut out) {
      super(user, out);
      this.id = id;
    }
  }

  public static class SelectInstance extends BreadboardMessage {
    final Long id;

    public SelectInstance(User user, Long id, ThrottledWebSocketOut out) {
      super(user, out);
      this.id = id;
    }
  }

  public static class AddAdmin extends BreadboardMessage {
    final ActorRef scriptBoardController;

    public AddAdmin(User user, ActorRef scriptBoardController, ThrottledWebSocketOut out) {
      super(user, out);
      this.scriptBoardController = scriptBoardController;
    }
  }

  public static class RunOnJoinStep extends BreadboardMessage {
    final Vertex vertex;

    public RunOnJoinStep(User user, Vertex vertex, ThrottledWebSocketOut out) {
      super(user, out);
      this.vertex = vertex;
    }
  }

  public static class RunOnLeaveStep extends BreadboardMessage {
    final Vertex vertex;

    public RunOnLeaveStep(User user, Vertex vertex, ThrottledWebSocketOut out) {
      super(user, out);
      this.vertex = vertex;
    }
  }

  public static class ShowEvent extends BreadboardMessage {
    final Long experimentInstanceId;

    public ShowEvent(User user, Long experimentInstanceId, ThrottledWebSocketOut out) {
      super(user, out);
      this.experimentInstanceId = experimentInstanceId;
    }
  }

  public static class GameFinish extends BreadboardMessage {
    public GameFinish(User user, ThrottledWebSocketOut out) {
      super(user, out);
    }
  }

  public static class DeleteInstance extends BreadboardMessage {

    final Long experimentInstanceId;

    public DeleteInstance(User user, Long experimentInstanceId, ThrottledWebSocketOut out) {
      super(user, out);
      this.experimentInstanceId = experimentInstanceId;
    }
  }

  public static class ReloadEngine extends BreadboardMessage {
    public ReloadEngine(User user, ThrottledWebSocketOut out) {
      super(user, out);
    }
  }

  public static class DeleteImage extends BreadboardMessage {
    final Long imageId;

    public DeleteImage(User user, Long imageId, ThrottledWebSocketOut out) {
      super(user, out);
      this.imageId = imageId;
    }
  }

}
