package models;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;

public class GameListener {

  public ExperimentInstance experimentInstance;
  public User user;
  public ScriptEngine engine;
  public ThrottledWebSocketOut out;

  public GameListener() {
  }

  public void hasStarted() {
    if (this.experimentInstance != null && (!this.experimentInstance.isTestInstance())) {
      this.experimentInstance.setHasStarted(Boolean.TRUE);
      this.experimentInstance.save();
    }
  }

  public void start() {
    if (this.experimentInstance != null && (!this.experimentInstance.isTestInstance())) {
      this.experimentInstance.start();
      this.experimentInstance.save();
    }
  }

  public void stop() {
    if (this.experimentInstance != null && (!this.experimentInstance.isTestInstance())) {
      this.experimentInstance.stop();
      this.experimentInstance.save();
    }
  }


  public void nextStep() {
    //To change body of implemented methods use File | Settings | File Templates.
  }


  public void finish() {
    if (this.experimentInstance != null && (!this.experimentInstance.isTestInstance())) {
      this.experimentInstance.finish();
      this.experimentInstance.save();
    }
    if (user != null && engine != null) {
      /*
      for (Parameter p : user.getExperiment().getParameters()) {
        if (engine.getBindings(ScriptContext.ENGINE_SCOPE).containsKey(p.name))
          engine.getBindings(ScriptContext.ENGINE_SCOPE).remove(p.name);
      }
      */
      this.out.write(user.toJson());

      // Set User ExperimentInstance to -1 here.
      // user.setExperimentInstanceId(-1L);
      // user.update();
      // Breadboard.instances.get(user.email).tell(new Breadboard.GameFinish(user, out), null);
    }

  }

}
