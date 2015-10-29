package models;

import org.codehaus.jackson.JsonNode;
import play.Logger;
import play.mvc.WebSocket;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;

/**
 * Created with IntelliJ IDEA.
 * User: ewong
 * Date: 10/11/12
 * Time: 11:34 PM
 * To change this template use File | Settings | File Templates.
 */
public class GameListener {

    public ExperimentInstance experimentInstance;
    public User user;
    public ScriptEngine engine;
    public ThrottledWebSocketOut out;

    public GameListener() {
    }

    public void hasStarted() {
        if (this.experimentInstance != null && (! this.experimentInstance.isTestInstance())) {
            this.experimentInstance.setHasStarted(Boolean.TRUE);
        }
    }

    public void start() {
        if (this.experimentInstance != null && (! this.experimentInstance.isTestInstance())) {
            this.experimentInstance.start();
        }
    }

    public void stop() {
        if (this.experimentInstance != null && (! this.experimentInstance.isTestInstance())) {
            this.experimentInstance.stop();
        }
    }


    public void nextStep() {
        //To change body of implemented methods use File | Settings | File Templates.
    }


    public void finish() {
        if (this.experimentInstance != null && (! this.experimentInstance.isTestInstance())) {
            this.experimentInstance.finish();
        }
        if (user != null && engine != null) {
            for (Parameter p : user.getExperiment().getParameters()) {
                if (engine.getBindings(ScriptContext.ENGINE_SCOPE).containsKey(p.name))
                    engine.getBindings(ScriptContext.ENGINE_SCOPE).remove(p.name);
            }
            this.out.write(user.toJson());

            // Set User ExperimentInstance to -1 here.
            user.setExperimentInstanceId(-1L);
            user.update();
            Breadboard.instances.get(user.email).tell(new Breadboard.GameFinish(user, out));
        }

    }


}
