package models;

import actors.FileWatcherActor;
import actors.FileWatcherActorProtocol;
import akka.actor.ActorRef;
import akka.actor.Props;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FalseFileFilter;
import play.Logger;
import play.libs.Akka;
import scala.concurrent.duration.Duration;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class FileWatcher {
  private Long updateIteration = 0L;
  private ArrayList<Admin> adminListeners = new ArrayList<>();
  static ActorRef fileWatcherActor;
  private WatchService watcher;
  private Map<WatchKey, Path> watchKeys;

  public FileWatcher(ArrayList<Admin> adminListeners) {
    this.adminListeners = adminListeners;

    Path devPath = FileSystems.getDefault().getPath("dev");
    this.watchKeys = new HashMap<>();
    try {
      this.watcher = FileSystems.getDefault().newWatchService();
      // Recursively watch all directories inside of the 'dev' directory
      registerRecursive(devPath);
    } catch (IOException ioe) {
      Logger.error("Can no longer watch the dev directory, was it deleted?");
    }

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

  public void registerRecursive(Path parent) throws IOException {
    for (File f : FileUtils.listFilesAndDirs(parent.toFile(), FalseFileFilter.INSTANCE, DirectoryFileFilter.DIRECTORY)) {
      WatchKey watchKey = f.toPath().register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
      watchKeys.put(watchKey, f.toPath());
    }
  }

  public ArrayList<Admin> getAdminListeners() {
    return this.adminListeners;
  }

  public Experiment getExperiment() {
    return this.adminListeners.get(0).getUser().selectedExperiment;
  }

  public void incrementUpdateIteration() {
    this.updateIteration++;
  }

  public Long getUpdateIteration() {
    return this.updateIteration;
  }

  public WatchService getWatcher() {
    return this.watcher;
  }

  public Map<WatchKey, Path> getWatchKeys() {
    return this.watchKeys;
  }
}
