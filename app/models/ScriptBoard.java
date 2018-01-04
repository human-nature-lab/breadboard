package models;

import akka.actor.UntypedActor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tinkerpop.blueprints.Edge;
import com.tinkerpop.blueprints.Graph;
import com.tinkerpop.blueprints.Vertex;
import com.tinkerpop.blueprints.util.wrappers.event.listener.GraphChangedListener;
import com.tinkerpop.gremlin.groovy.GremlinGroovyPipeline;
import groovy.util.ObservableMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.commons.lang3.StringUtils;
import org.codehaus.groovy.control.CompilationFailedException;
import play.Logger;
import play.Play;
import play.libs.F.Callback;
import play.libs.Json;
import play.mvc.WebSocket;

import javax.script.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class ScriptBoard extends UntypedActor {
  private static ObjectMapper mapper = new ObjectMapper();

  private static ScriptEngineManager manager = new ScriptEngineManager();
  private static ScriptEngine engine;

  private static Map results = new HashMap();
  private static PlayerActionsInterface playerActions;
  private static BreadboardGraphInterface graphInterface;
  private static BreadboardGraphChangedListener graphChangedListener;
  private static EventTracker eventTracker = new EventTracker();

  private static Random rand = new Random();

  private static HashMap<String, Client> clients = new HashMap<>();

  // A list of admins currently watching the game.
  private static ArrayList<Admin> admins = new ArrayList<>();

  private static UserDataInterface instanceData;

  private GameListener gameListener = new GameListener();

  public static boolean checkPassword(String _password) {
    // If no password is set, always return true
    if (engine.get("password") == null)
      return true;
    Object password = engine.get("password");
    return _password.equals(password.toString());
  }

  public ScriptBoard() throws IOException, ScriptException {
    rebuildScriptBoard(null);
  }

  private void init() {
    results = new HashMap();
    mapper = new ObjectMapper();
    admins = new ArrayList<Admin>();
    clients = new HashMap<String, Client>();
    eventTracker = new EventTracker();
    manager = new ScriptEngineManager();
  }

  private void resetEngine(Experiment experiment) throws IOException, ScriptException {
    Logger.debug("resetEngine");
    if (engine != null) {
      //just in case
      playerActions.turnAIOff();
      //clean up the graph
      processScript("g.empty()", null);
    }

    engine = manager.getEngineByName("gremlin-groovy");
    engine.getBindings(ScriptContext.ENGINE_SCOPE).put("r", rand);
    engine.getBindings(ScriptContext.ENGINE_SCOPE).put("results", results);

    engine.getBindings(ScriptContext.ENGINE_SCOPE).put("eventTracker", eventTracker);
    engine.getBindings(ScriptContext.ENGINE_SCOPE).put("gameListener", gameListener);

    if (experiment != null) {
      engine.getBindings(ScriptContext.ENGINE_SCOPE).put("c", experiment.contentFetcher);
    }

    // Run the test Groovy script here
    File scriptFile = new File(Play.application().path().toString() + "/groovy/hello.groovy");
    String scriptString = FileUtils.readFileToString(scriptFile, "UTF-8");
    engine.eval(scriptString);

    // get script object on which we want to implement the interface with
    Object a = engine.get("a");
    Invocable inv = (Invocable) engine;
    playerActions = inv.getInterface(a, PlayerActionsInterface.class);

    Object d = engine.get("d");
    instanceData = inv.getInterface(d, UserDataInterface.class);

    Object g = engine.get("g");
    graphInterface = inv.getInterface(g, BreadboardGraphInterface.class);

    if (graphChangedListener == null) {
      Logger.debug("graphChangedListener == null");
      Long clientUpdateRate = play.Play.application().configuration().getMilliseconds("breadboard.clientUpdateRate");
      if (clientUpdateRate == null || clientUpdateRate == 0) {
        Logger.debug("clientUpdateRate not found or 0, using event based updating");
        graphChangedListener = new EventGraphChangedListener((Graph) g);

      } else {
        Logger.debug("clientUpdateRate found, using polling");
        graphChangedListener = new IteratedBreadboardGraphChangedListener((Graph) g);
      }
    } else {
      Logger.debug("graphChangedListener != null");
      graphChangedListener.setGraph((Graph) g);
    }

    /*
    TODO: Why would we need to recreate the graph listener here?
    IteratedBreadboardGraphChangedListener oldGraphChangedListener = graphChangedListener;
    if (oldGraphChangedListener != null) {
      // Stop the old executor before starting the new one
      oldGraphChangedListener.stopExecutor();
      try {
        Logger.debug("Awaiting termination");
        oldGraphChangedListener.getExecutor().awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
      } catch (InterruptedException e) {
        Logger.debug("InterruptedException: " + e.getLocalizedMessage());
      }
      Logger.debug("Executor terminated");
    }

    graphChangedListener = new IteratedBreadboardGraphChangedListener((Graph) g);

    //graphChangedListener.getExecutor().get

    // if there are any existing adminListeners they need to be added as listeners to the new graph
    if (oldGraphChangedListener != null) {
      Logger.debug("oldGraphChangedListener.getClientListeners().size(): " + oldGraphChangedListener.getClientListeners().size());
      for (ClientListener listener : oldGraphChangedListener.getAdminListeners()) {
        graphChangedListener.addAdminListener(listener);
      }

      // same with client listeners
      for (Client client : oldGraphChangedListener.getClientListeners()) {
        graphChangedListener.addClientListener(client);
      }
    }

    Logger.debug("graphChangedListener.getClientListeners().size(): " + graphChangedListener.getClientListeners().size());
    */

    graphInterface.addListener(graphChangedListener);

    gameListener.engine = engine;
  }

  private void loadSteps(Experiment experiment, ThrottledWebSocketOut out) {
    for (Step step : experiment.steps) {
      //should call RunStep?
      processScript(step.source, out);
    }
  }

  private void rebuildScriptBoard(Experiment experiment) throws IOException, ScriptException {
    //init();
    resetEngine(experiment);
  }

  public static void addAdmin(Admin admin) {
    admins.add(admin);
    graphChangedListener.addAdminListener(admin);
  }

  public static void addClient(String experimentIdString, String experimentInstanceIdString, String clientId, final WebSocket.In<JsonNode> in, final ThrottledWebSocketOut out) throws Exception {
    try {
      Long experimentId = Long.parseLong(experimentIdString);
      Long experimentInstanceId = Long.parseLong(experimentInstanceIdString);

      Experiment experiment = Experiment.findById(experimentId);
      if (experiment == null) {
        Logger.debug("addClient: experiment == null");
        return;
      }

      ExperimentInstance experimentInstance = null;
      if (experimentInstanceId.equals(Experiment.TEST_INSTANCE_ID)) {
        experimentInstance = experiment.getTestInstance();
      } else {
        experimentInstance = ExperimentInstance.findById(experimentInstanceId);
      }
      if (experimentInstance == null) {
        Logger.debug("addClient: experimentInstance == null");
        return;
      }

      Client client = clients.get(experimentIdString + "-" + experimentInstanceIdString + "-" + clientId);

      if (client == null && experimentInstance.hasStarted) {
        Logger.debug("New client trying to join after game has already started.");
        return;
      }

      if (client == null) {
        // New client, let's create a new Client object
        client = new Client(clientId, experimentInstance, in, out);
        clients.put(experimentIdString + "-" + experimentInstanceIdString + "-" + clientId, client);
      } else {
        // Reconnecting: let's change the in / out so they continue to receive messages
        client.setIn(in);
        client.setOut(out);
      }

      /*
      in.onClose(new Callback0() {
        public void invoke() {
              Logger.debug("Websocket Closed!");
          }
      });
      */

      in.onMessage(new Callback<JsonNode>() {
        public void invoke(JsonNode event) {
          try {
            ObjectMapper mapper = new ObjectMapper();
            // TODO: is event.toString() the correct method here?
            Map<String, Object> jsonInput = mapper.readValue(event.toString(), Map.class);
            String action = jsonInput.get("action").toString();
            if (action.equals("LogIn")) {
              String clientId = jsonInput.get("clientId").toString();
              String referer = jsonInput.get("referer").toString();
              String connection = jsonInput.get("connection").toString();
              String accept = jsonInput.get("accept").toString();
              String cacheControl = jsonInput.get("cacheControl").toString();
              String acceptCharset = jsonInput.get("acceptCharset").toString();
              String cookie = jsonInput.get("cookie").toString();
              String acceptLanguage = jsonInput.get("acceptLanguage").toString();
              String acceptEncoding = jsonInput.get("acceptEncoding").toString();
              String userAgent = jsonInput.get("userAgent").toString();
              String host = jsonInput.get("host").toString();
              String ipAddress = jsonInput.get("ipAddress").toString();
              String requestURI = jsonInput.get("requestURI").toString();

              if (eventTracker != null) {
                LinkedHashMap<Object, Object> data = new LinkedHashMap<Object, Object>();
                data.put("clientId", clientId);
                data.put("referer", referer);
                data.put("connection", connection);
                data.put("acceptCharset", acceptCharset);
                data.put("acceptLanguage", acceptLanguage);
                data.put("acceptEncoding", acceptEncoding);
                data.put("userAgent", userAgent);
                data.put("host", host);
                data.put("ipAddress", ipAddress);
                data.put("requestURI", requestURI);
                eventTracker.track("clientLogIn", data);
              }
            } else if (action.equals("MakeChoice")) {
              String choiceUID = jsonInput.get("choiceUID").toString();
              String params = (jsonInput.containsKey("params")) ? jsonInput.get("params").toString() : null;
              makeChoice(choiceUID, params, out);
            }
          } catch (java.io.IOException ignored) {
            //Logger.debug("java.io.IOException");
          }
        }
      });

      if (graphChangedListener != null) {
        graphChangedListener.addClientListener(client);
      }

      if (graphInterface != null) {
        if (!experimentInstance.hasStarted) {
          graphInterface.addPlayer(clientId);
        }
        Graph wholeGraph = (Graph) engine.get("g");
        Vertex clientVertex = wholeGraph.getVertex(clientId);
        if (clientVertex != null) {
          if (graphChangedListener instanceof EventGraphChangedListener) {
            client.updateGraph(clientVertex);
          }
        }
      }

      // Update the client's state
      ObjectNode jsonOutput = Json.newObject();
      jsonOutput.put("style", experimentInstance.experiment.style);
      Logger.debug("addClient, " + clientId);
      out.write(jsonOutput);
    } catch (NumberFormatException nfe) {
      Logger.debug("addClient: NumberFormatException");
    }
  }

  public void onReceive(Object message) throws Exception {
    try {
      if (message instanceof Breadboard.BreadboardMessage) {
        Breadboard.BreadboardMessage breadboardMessage = (Breadboard.BreadboardMessage) message;
        if (breadboardMessage.user != null) {
          gameListener.user = breadboardMessage.user;
          gameListener.out = breadboardMessage.out;
        }
        if (message instanceof Breadboard.AddAdmin) {
          Breadboard.AddAdmin addAdmin = (Breadboard.AddAdmin) message;

          addAdmin(new Admin(addAdmin.user, addAdmin.scriptBoardController, addAdmin.out));
        } else if (message instanceof Breadboard.RunGame) {
          if (breadboardMessage.user != null) {
            // Run Game
            processScript("initStep.start()", breadboardMessage.out);
          }

        } else if (message instanceof Breadboard.HitCreated) {
          Breadboard.HitCreated hitCreated = (Breadboard.HitCreated) message;
          if (breadboardMessage.user != null) {
            Integer lifetimeInMs = hitCreated.lifetimeInSeconds * 1000;
            Integer tutorialTimeInMs = hitCreated.tutorialTime * 1000;
            Long timerTime = System.currentTimeMillis() + (lifetimeInMs + tutorialTimeInMs);
            // startAt will be used to add timer to clients for start of game
            processScript("startAt = " + timerTime, breadboardMessage.out);

            final ThrottledWebSocketOut breadboardOut = breadboardMessage.out;
            // Set timer for lifetimeInSeconds time after which no longer allow new client connections
            new Timer().schedule(new TimerTask() {
              @Override
              public void run() {
                gameListener.hasStarted();
              }
            }, lifetimeInMs);

            // Set timer for lifetimeInMs + tutorialTimeInMs time after which send an initStep.start() message
            new Timer().schedule(new TimerTask() {
              @Override
              public void run() {
                processScript("initStep.start()", breadboardOut);
                Logger.debug("initStep.start()");
              }
            }, (lifetimeInMs + tutorialTimeInMs));
          }
        } else if (message instanceof Breadboard.SendScript) {
          Breadboard.SendScript sendScript = (Breadboard.SendScript) message;

          if (breadboardMessage.user != null) {
            // Save script in database
            breadboardMessage.user.currentScript = sendScript.script;
            breadboardMessage.user.update();

            // Process script
            processScript(sendScript.script, breadboardMessage.out);
          }

        } else if (message instanceof Breadboard.MakeChoice) {
          Breadboard.MakeChoice makeChoice = (Breadboard.MakeChoice) message;
          makeChoice(makeChoice.uid, makeChoice.params, breadboardMessage.out);
        } else if (message instanceof Breadboard.SendStep) {
          Breadboard.SendStep sendStep = (Breadboard.SendStep) message;

          if (breadboardMessage.user != null && breadboardMessage.user.selectedExperiment != null) {
            Logger.debug("SendStep: " + sendStep.id + ", " + sendStep.name + ", " + sendStep.source);
            /*
            Step step = breadboardMessage.user.selectedExperiment.getStep(sendStep.id);
            if (step != null) {
              step.setSource(sendStep.source);
              step.update();
            }
            */
            // Now we only run the step
            Breadboard.instances.get(breadboardMessage.user.email).tell(new Breadboard.RunStep(breadboardMessage.user, sendStep.source, breadboardMessage.out), null);
          }
        } else if (message instanceof Breadboard.RunStep) {
          // TODO: Compile step here and make available to scripting engine
          Breadboard.RunStep runStep = (Breadboard.RunStep) message;
          processScript(runStep.source, breadboardMessage.out);
        } else if (message instanceof Breadboard.ChangeExperiment) {
          if (breadboardMessage.user != null) {
            rebuildScriptBoard(breadboardMessage.user.selectedExperiment);
            loadSteps(breadboardMessage.user.selectedExperiment, breadboardMessage.out);
            Breadboard.ChangeExperiment changeExperiment = (Breadboard.ChangeExperiment) message;

            //reset the instance
            if (breadboardMessage.user.experimentInstanceId != -1) {
              ExperimentInstance runningInstance = ExperimentInstance.findById(breadboardMessage.user.experimentInstanceId);
              if (runningInstance != null) {
                if (runningInstance.status == ExperimentInstance.Status.RUNNING && runningInstance.experiment.id.equals(changeExperiment.experiment.id)) {
                  eventTracker.setExperimentInstance(runningInstance);
                  gameListener.experimentInstance = runningInstance;
                  for (Data param : runningInstance.data) {
                    initParam(param, changeExperiment.experiment);
                  }
                }
              } else {
                Logger.error("runningInstance == null");
              }
            }
          }
        } else if (message instanceof Breadboard.DeleteExperiment) {
          Breadboard.DeleteExperiment deleteExperiment = (Breadboard.DeleteExperiment) message;

          Experiment toBeDeleteExperiment = deleteExperiment.user.getExperimentByName(deleteExperiment.experimentName);
          Logger.debug("DeleteExperiment: " + toBeDeleteExperiment.name);

          //also stops the game
          for (Parameter p : breadboardMessage.user.getExperiment().getParameters()) {
            Logger.debug("Key: " + p.name);
            if (engine.getBindings(ScriptContext.ENGINE_SCOPE).containsKey(p.name))
              engine.getBindings(ScriptContext.ENGINE_SCOPE).remove(p.name);
          }

          breadboardMessage.user.currentScript = "";
          processScript("g.empty()", breadboardMessage.out);

          if (toBeDeleteExperiment != null) {
            //needs to remove all the selectedExperiment and ownedExperiments which is this toBeDeleteExperiment
            List<User> users = User.find.all();

            boolean update;
            boolean updateManyToMany;
            for (User user : users) {
              if (user.email.equalsIgnoreCase(breadboardMessage.user.email)) {
                user = breadboardMessage.user;
                user.setExperimentInstanceId(-1L);
              }
              update = false;
              updateManyToMany = false;
              if (user.selectedExperiment != null && user.selectedExperiment.id == toBeDeleteExperiment.id) {
                update = true;
                user.setSelectedExperiment(null);
              }
              if (user.ownedExperiments.contains(toBeDeleteExperiment)) {
                updateManyToMany = true;
                user.ownedExperiments.remove(toBeDeleteExperiment);
              }
              if (update) {
                user.update();
              }
              if (updateManyToMany) {
                user.saveManyToManyAssociations("ownedExperiments");
              }
            }

            toBeDeleteExperiment.delete();
          }

          Breadboard.breadboardController.tell(new Breadboard.Update(breadboardMessage.user, breadboardMessage.out), null);
        } else if (message instanceof Breadboard.LaunchGame) {
          Breadboard.LaunchGame launchGame = (Breadboard.LaunchGame) message;

          if (breadboardMessage.user.selectedExperiment != null) {
            rebuildScriptBoard(breadboardMessage.user.selectedExperiment);
            ExperimentInstance instance = startGame(launchGame.name, breadboardMessage.user.experimentInstanceId, launchGame.parameters,
                breadboardMessage.user.selectedExperiment, breadboardMessage.user);

            instanceData.addPropertyChangeListener(new UserDataChangeListener(instance));

            eventTracker.enable();
            eventTracker.setExperimentInstance(instance);
            gameListener.experimentInstance = instance;
            gameListener.start();

            // Re-run the Steps
            for (Step step : instance.experiment.steps)
              Breadboard.instances.get(breadboardMessage.user.email).tell(new Breadboard.RunStep(breadboardMessage.user, step.source, breadboardMessage.out), null);

          } // END if (breadboardMessage.user.selectedExperiment != null)
          // Update User JSON
          Breadboard.breadboardController.tell(new Breadboard.Update(breadboardMessage.user, breadboardMessage.out), null);

        } // END else if(message instanceof Breadboard.LaunchGame)
        else if (message instanceof Breadboard.SelectInstance) {
          Breadboard.SelectInstance selectInstance = (Breadboard.SelectInstance) message;

          rebuildScriptBoard(breadboardMessage.user.selectedExperiment);
          loadSteps(breadboardMessage.user.selectedExperiment, selectInstance.out);
          breadboardMessage.user.setExperimentInstanceId(selectInstance.id);
          breadboardMessage.user.update();

          gameListener.stop();
          ExperimentInstance instance = ExperimentInstance.findById(selectInstance.id);
          if (instance.status.isRunnable()) {
            List<Data> parameters = instance.data;
            for (Data param : parameters) {
              initParam(param, breadboardMessage.user.selectedExperiment);
            } // END for (Data param : parameters)

            eventTracker.enable();
            eventTracker.setExperimentInstance(instance);
            gameListener.experimentInstance = instance;
            gameListener.start();

            instanceData.addPropertyChangeListener(new UserDataChangeListener(instance));

            //don't know why... ebean bug? if don't do this, the instances in the selectedExperiment doesn't get updated
            //which is causing the launch button still shows up (because the status isn't updated) instead of the stop button for the instance
            breadboardMessage.user.selectedExperiment = Experiment.findById(breadboardMessage.user.selectedExperiment.id);
          }
          Breadboard.breadboardController.tell(new Breadboard.Update(breadboardMessage.user, breadboardMessage.out), null);
        } // END else if(message instanceof Breadboard.SelectInstance)
        else if (message instanceof Breadboard.StopGame) {
          Breadboard.StopGame stopGame = (Breadboard.StopGame) message;

          ExperimentInstance instance = ExperimentInstance.findById(stopGame.id);
          if (gameListener.experimentInstance != null && gameListener.experimentInstance.equals(instance)) {
            for (Parameter p : breadboardMessage.user.getExperiment().getParameters()) {
              //Logger.debug("Key: " + p.name);
              if (engine.getBindings(ScriptContext.ENGINE_SCOPE).containsKey(p.name))
                engine.getBindings(ScriptContext.ENGINE_SCOPE).remove(p.name);
            }

            if (gameListener.experimentInstance == null) {
              Logger.debug("gameListener.experimentInstance == null");
              gameListener.experimentInstance = ExperimentInstance.findById(breadboardMessage.user.experimentInstanceId);
            }

            if (gameListener.experimentInstance.status.isRunnable()) {
              Logger.debug("gameListener.experimentInstance.status.isRunnable()");
              gameListener.stop();
            }

          } else {
            if (instance.status.isRunnable()) {
              instance.stop();
            }
          }
          breadboardMessage.user.setExperimentInstanceId(-1L);
          breadboardMessage.user.update();
          // Update User JSON
          Breadboard.breadboardController.tell(new Breadboard.Update(breadboardMessage.user, breadboardMessage.out), null);
        } // END else if(message instanceof Breadboard.StopGame)
        else if (message instanceof Breadboard.RunOnJoinStep) {
          Breadboard.RunOnJoinStep runOnJoinStep = (Breadboard.RunOnJoinStep) message;
          runOnJoinStep.user.refresh();
          //the script engine was throwing exception for ArbesmanRand because of missing onJoinStep
          if (runOnJoinStep.user.selectedExperiment != null && runOnJoinStep.user.selectedExperiment.hasOnJoinStep()) {
            Vertex player = runOnJoinStep.vertex;
            processScript("onJoinStep.start(\"" + player.getId().toString() + "\")", runOnJoinStep.out);
          }

        } // END else if(message instanceof Breadboard.RunOnJoinStep)
        else if (message instanceof Breadboard.RunOnLeaveStep) {
          Breadboard.RunOnLeaveStep runOnLeaveStep = (Breadboard.RunOnLeaveStep) message;
          runOnLeaveStep.user.refresh();
          //the script engine was throwing exception for ArbesmanRand because of missing runOnLeaveStep
          if (runOnLeaveStep.user.selectedExperiment != null && runOnLeaveStep.user.selectedExperiment.hasOnLeaveStep()) {
            Vertex player = runOnLeaveStep.vertex;
            if (engine.get("onLeaveStep.start()") != null) {
              processScript("onLeaveStep.start(\"" + player.getId().toString() + "\")", runOnLeaveStep.out);
            }
          }

        } // END else if(message instanceof Breadboard.RunOnLeaveStep)
        else if (message instanceof Breadboard.SaveContent || message instanceof Breadboard.CreateContent) {
          breadboardMessage.user.selectedExperiment.refresh();
          engine.getBindings(ScriptContext.ENGINE_SCOPE).put("c", breadboardMessage.user.selectedExperiment.contentFetcher);
        } // END else if(message instanceof Breadboard.SaveContent || message instanceof Breadboard.CreateContent)
        else if (message instanceof Breadboard.Refresh) {
          Logger.debug("Breadboard.Refresh");

          //need to refresh the listeners
          for (Admin admin : admins) {
            admin.setOut(breadboardMessage.out);
          }

          ObjectNode jsonOutput = Json.newObject();

          Graph wholeGraph = (Graph) engine.get("g");

          for (Admin admin : admins) {
            for (Vertex v : wholeGraph.getVertices()) {
              admin.vertexAdded(v, false);
            }
            for (Edge e : wholeGraph.getEdges()) {
              admin.edgeAdded(e);
            }
          }

          breadboardMessage.out.write(jsonOutput);
        } else if (message instanceof Breadboard.GameFinish) {
          Breadboard.instances.get(breadboardMessage.user.email).tell(new Breadboard.ReloadEngine(breadboardMessage.user, breadboardMessage.out), null);
          Breadboard.breadboardController.tell(new Breadboard.Update(breadboardMessage.user, breadboardMessage.out), null);
        } else if (message instanceof Breadboard.ReloadEngine) {
          Experiment selectedExperiment = breadboardMessage.user.getExperiment();
          rebuildScriptBoard(selectedExperiment);
          loadSteps(selectedExperiment, breadboardMessage.out);
          Breadboard.ReloadEngine reloadEngine = (Breadboard.ReloadEngine) message;
          if (reloadEngine.user.experimentInstanceId != -1) {
            ExperimentInstance ei = ExperimentInstance.findById(reloadEngine.user.experimentInstanceId);
            if (ei != null) {
              eventTracker.enable();
              eventTracker.setExperimentInstance(ei);
              for (Data d : ei.data) {
                initParam(d, selectedExperiment);
              }
            }
          }
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private ExperimentInstance startGame(String gameName, long currentExperimentInstanceId, LinkedHashMap parameters, Experiment selectedExperiment, User user) {
    ExperimentInstance instance = new ExperimentInstance(gameName, selectedExperiment);
    instance.hasStarted = false;
    instance.status = ExperimentInstance.Status.STOPPED;

    if (currentExperimentInstanceId != -1) {
      ExperimentInstance runningInstance = ExperimentInstance.findById(currentExperimentInstanceId);
      if (runningInstance != null && runningInstance.status.isRunnable()) {
        runningInstance.status = ExperimentInstance.Status.STOPPED;
        runningInstance.update();
      }
    }

    initAllParam(parameters, selectedExperiment, instance);

    selectedExperiment.instances.add(instance);
    selectedExperiment.save();

    user.setExperimentInstanceId(instance.id);
    user.update();

    return instance;
  }

  private void initAllParam(Map params, Experiment experiment, ExperimentInstance instance) {

    Iterator it = params.keySet().iterator();
    while (it.hasNext()) {
      Object key = it.next();
      // Add the initial parameters to the data of the ExperimentInstance
      Data data = new Data();
      data.name = key.toString();
      data.value = params.get(key).toString();
      if (instance != null) {
        instance.data.add(data);
      }

      initParam(data, experiment);
    } // END while(it.hasNext())
  }

  private void initParam(Data param, Experiment experiment) {
    String key = param.name;
    Logger.debug("initParam: " + key);
    Parameter parameter = experiment == null ? null : experiment.getParameterByName(key);
    if (parameter == null) {
      //default string value
      engine.getBindings(ScriptContext.ENGINE_SCOPE).put(key, param.value);
      return;
    }
    // TODO: Perhaps put this code elsewhere?
    // Bind the initial variables to the script engine
    if (parameter != null) {
      if (parameter.type.equals("Integer")) {
        try {
          Integer intParameter = Integer.parseInt(param.value);
          engine.getBindings(ScriptContext.ENGINE_SCOPE).put(key, intParameter);
        } catch (NumberFormatException npe) {
          Logger.error("Breadboard.LaunchGame: Caught NumberFormatException parsing string as Integer: " + param.value);
        }
      } else if (parameter.type.equals("Decimal")) {
        try {
          Double doubleParameter = Double.parseDouble(param.value);
          engine.getBindings(ScriptContext.ENGINE_SCOPE).put(key, doubleParameter);
        } catch (NumberFormatException npe) {
          Logger.error("Breadboard.LaunchGame: Caught NumberFormatException parsing string as Double: " + param.value);
        }

      } else if (parameter.type.equals("Text")) {
        engine.getBindings(ScriptContext.ENGINE_SCOPE).put(key, param.value);
      } else if (parameter.type.equals("Boolean")) {
        Boolean booleanParameter = Boolean.parseBoolean(param.value);
        engine.getBindings(ScriptContext.ENGINE_SCOPE).put(key, booleanParameter);
      }
    } //END if (parameter != null)
  }

  private static void makeChoice(String uid, String params, ThrottledWebSocketOut out) {
    ObjectNode jsonOutput = Json.newObject();
    playerActions.choose(uid, params);
    out.write(jsonOutput);
  }

  private static void processScript(String script, ThrottledWebSocketOut out) {

    ObjectNode jsonOutput = Json.newObject();
    //TODO: better way to handle this?
    boolean initStep = false;
    synchronized (ScriptBoard.class) {
      if (script.contains("initStep.start()")) {
        if (engine.get("initStep.start()") != null) {
          Logger.debug("engine.get(\"initStep.start()\") = " + engine.get("initStep.start()").toString());
          Logger.warn("The initStep has started already.");
          jsonOutput.put("error", "Caught error: ".concat("initStep started already").concat("\n"));
          if (out != null) {
            out.write(jsonOutput);
          }
          return;
        }
        engine.put("initStep.start()", true);
        initStep = true;
      }
    }
    try {
      Object outputObject = engine.eval(script);
      // Clear out error
      jsonOutput.put("error", "");

      // Write out the results Map as JSON
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

      mapper.writeValue(outputStream, engine.getBindings(ScriptContext.ENGINE_SCOPE).get("results"));
      String outputString = script.trim().concat("\n\n==>");

      if (outputObject != null) {
        if (outputObject instanceof GremlinGroovyPipeline) {
          outputString += StringUtils.join(((GremlinGroovyPipeline) outputObject).toList(), "\n==>");
        } else {
          outputString += outputObject.toString();
        }
      }

      jsonOutput.put("output", outputString.trim());
    } catch (CompilationFailedException cfe) {
      Logger.error("Unable to compile the script.", cfe);
      jsonOutput.put("error", "Caught error: ".concat(cfe.getMessage()).concat("\n"));
      if (initStep) {
        engine.put("initStep.start()", null);
      }
    } catch (ScriptException se) {
      Logger.error("Script Error.", se);
      jsonOutput.put("error", "Caught error: ".concat(se.getMessage()).concat("\n"));
      if (initStep) {
        engine.put("initStep.start()", null);
      }
    } catch (Exception e) {
      Logger.error("Failed to process the script.", e);
      jsonOutput.put("error", "Caught error: ".concat(e.getMessage()).concat("\n"));
      if (initStep) {
        engine.put("initStep.start()", null);
      }
    } finally {
      if (out != null) {
        out.write(jsonOutput);
      }
    }
  }

  private class UserDataChangeListener implements PropertyChangeListener {

    private ExperimentInstance experimentInstance;

    private UserDataChangeListener(ExperimentInstance experimentInstance) {
      this.experimentInstance = experimentInstance;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      List<Data> dataList = experimentInstance.data;
      Data foundData = null;
      if (evt.getPropertyName() != null) {
        foundData = findDataByName(evt.getPropertyName());
      }
      if (evt instanceof ObservableMap.PropertyAddedEvent) {
        Data newData = foundData == null ? new Data() : foundData;
        newData.name = evt.getPropertyName();
        newData.value = evt.getNewValue().toString();
        newData.experimentInstance = experimentInstance;
        if (foundData == null) {
          dataList.add(newData);
          experimentInstance.save();
        } else {
          newData.save();
        }
        initParam(newData, null);

      } else if (evt instanceof ObservableMap.PropertyClearedEvent) {
        List<Parameter> parameters = experimentInstance.experiment.parameters;
        List<String> parameterNames = new ArrayList<String>();
        for (Parameter parameter : parameters) {
          parameterNames.add(parameter.name);
        }
        Iterator<Data> dataIterator = dataList.iterator();
        while (dataIterator.hasNext()) {
          Data d = dataIterator.next();
          //make sure not removing the parameters
          if (!parameterNames.contains(d.name)) {
            dataIterator.remove();
            engine.getBindings(ScriptContext.ENGINE_SCOPE).remove(d.name);
          }
        }
        experimentInstance.save();

      } else if (evt instanceof ObservableMap.PropertyRemovedEvent) {
        if (foundData != null) {
          dataList.remove(foundData);
          engine.getBindings(ScriptContext.ENGINE_SCOPE).remove(foundData.name);
        }
        experimentInstance.save();
      } else if (evt instanceof ObservableMap.PropertyUpdatedEvent) {
        if (foundData != null) {
          foundData.value = evt.getNewValue().toString();
        }
        foundData.save();
        initParam(foundData, null);
      }
    }

    private Data findDataByName(String name) {
      List<Data> dataList = experimentInstance.data;
      for (Data d : dataList) {
        //this should be case-sensitive
        if (d.name.equals(name)) {
          return d;
        }
      }
      return null;
    }
  }
}
