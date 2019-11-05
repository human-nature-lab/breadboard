package actors;

import actors.FileWatcherActorProtocol.FileWatch;
import akka.actor.UntypedActor;
import models.Admin;
import models.FileWatcher;
import play.Logger;
import java.nio.file.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class FileWatcherActor extends UntypedActor {
  private static DateFormat dateFormat;

  public FileWatcherActor() {
    dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  }


  @Override
  public void onReceive(Object message) {
    if (message instanceof FileWatch) {
      FileWatcher fileWatcher = ((FileWatch) message).fileWatcher;

      // If fileMode for the selected experiment is false, do nothing
      /* TODO: if there are multiple admins listening, they could each be watching different experiments which is weird in the context of a single file watcher */
      // if ( fileWatcher.getAdminListeners().isEmpty() || fileWatcher.getAdminListeners().get(0).getUser().selectedExperiment == null || (! fileWatcher.getAdminListeners().get(0).getUser().selectedExperiment.getFileMode()) ) {
      //  return;
      //}
      fileWatcher.incrementUpdateIteration();
      Long updateIteration = fileWatcher.getUpdateIteration();
      if (updateIteration % 100 == 0) {
        Logger.debug(dateFormat.format(new Date()) + " - FileWatch:" + updateIteration);
      }

      WatchKey watchKey;
      boolean changed = false;
      watchKey = fileWatcher.getWatcher().poll();
      if (watchKey == null) {
        return;
      }

      Path dir = fileWatcher.getWatchKeys().get(watchKey);
      if (dir == null) {
        Logger.error("Event triggered from watchKey not registered properly.");
        return;
      }

      /* TODO: Reload step in script engine on change */
      for (WatchEvent<?> event: watchKey.pollEvents()) {
        changed = true;
        WatchEvent<Path> ev = (WatchEvent<Path>) event;
        Path name = ev.context();
        Path child = dir.resolve(name);
        Logger.debug(child.toString());
        // Logger.debug(filePath.toAbsolutePath().toString());
        // File file = devPath.resolve(filePath).toFile();
        //if (file.isFile()) {
        //  Logger.debug("file.isFile()");
        //  Logger.debug(file.getPath());
        //}
        //if (filePath.endsWith("/Steps")) {
        //  Logger.debug("editing step");
        //}
        if (ev.kind() == ENTRY_CREATE) {
          Logger.debug("ENTRY_CREATE");
        } else if (ev.kind() == ENTRY_MODIFY) {
          Logger.debug("ENTRY_MODIFY");
        } else if (ev.kind() == ENTRY_DELETE) {
          Logger.debug("ENTRY_DELETE");
        }
      }

      if (changed) {
        // Something was changed, let's update all admin listeners
        for(Admin a : fileWatcher.getAdminListeners()) {
          a.update();
        }
      }

      boolean valid = watchKey.reset();
      if (!valid) {
        fileWatcher.getWatchKeys().remove(watchKey);
        Logger.debug("Watched directory was deleted.");
      }
    }
  }
}

