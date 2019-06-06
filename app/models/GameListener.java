package models;

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

  public void finish() {
    if (this.experimentInstance != null && (!this.experimentInstance.isTestInstance())) {
      this.experimentInstance.finish();
      this.experimentInstance.save();
    }
    if (user != null && engine != null) {
      this.out.write(user.toJson());
    }
  }

}
