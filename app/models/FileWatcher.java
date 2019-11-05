package models;

import actors.FileWatcherActor;
import actors.FileWatcherActorProtocol;
import akka.actor.ActorRef;
import akka.actor.Props;
import play.Logger;
import play.libs.Akka;
import scala.concurrent.duration.Duration;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class FileWatcher {
  private Long updateIteration = 0L;
  private ArrayList<Admin> adminListeners = new ArrayList<>();
  static ActorRef fileWatcherActor;

  public FileWatcher(ArrayList<Admin> adminListeners) {
    this.adminListeners = adminListeners;
    fileWatcherActor = Akka.system().actorOf(new Props(FileWatcherActor.class));
    Long fileWatchRate = play.Play.application().configuration().getMilliseconds("breadboard.fileWatchRate");
    if (fileWatchRate == null) {
      Logger.debug("fileWatchRate = null");
      fileWatchRate = 100L;
    }
    Akka.system().scheduler().schedule(
        Duration.create(0, TimeUnit.MILLISECONDS),
        Duration.create(fileWatchRate, TimeUnit.MILLISECONDS),
        fileWatcherActor,
        new FileWatcherActorProtocol.FileWatch(this),
        Akka.system().dispatcher(),
        null
    );
  }

  public ArrayList<Admin> getAdminListeners() {
    return this.adminListeners;
  }

  public Experiment getExperiment() {
    return this.getExperiment();
  }

  public void incrementUpdateIteration() {
    this.updateIteration++;
  }

  public Long getUpdateIteration() {
    return this.updateIteration;
  }
}
