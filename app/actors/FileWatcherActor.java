package actors;

import actors.FileWatcherActorProtocol.FileWatch;
import akka.actor.UntypedActor;
import models.Admin;
import models.FileWatcher;
import play.Logger;

import java.io.IOException;
import java.nio.file.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public class FileWatcherActor extends UntypedActor {
  private static DateFormat dateFormat;
  private WatchService watcher;
  private WatchKey watchKey;

  public FileWatcherActor() {
    dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
  }

  @Override
  public void onReceive(Object message) {
    if (message instanceof FileWatch) {
      FileWatcher fileWatcher = ((FileWatch) message).fileWatcher;
      // If fileMode for the selected experiment is false, do nothing
      /* TODO: if there are multiple admins listening, they could each be watching different experiments which is weird in the context of a single file watcher */
      if ( fileWatcher.getAdminListeners().isEmpty() || fileWatcher.getAdminListeners().get(0).getUser().selectedExperiment == null || (! fileWatcher.getAdminListeners().get(0).getUser().selectedExperiment.getFileMode()) ) {
        return;
      }
      fileWatcher.incrementUpdateIteration();
      Long updateIteration = fileWatcher.getUpdateIteration();
      if (updateIteration % 10 == 0) {
        Logger.debug(dateFormat.format(new Date()) + " - FileWatch:" + updateIteration);
      }
      Path devPath = FileSystems.getDefault().getPath("dev");
      try {
        boolean changed = false;
        watcher = FileSystems.getDefault().newWatchService();
        watchKey = devPath.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
        try {
          watchKey = watcher.take();
        } catch (InterruptedException x) {
          return;
        }

        // TODO: Reload step in script engine on change
        for (WatchEvent<?> event: watchKey.pollEvents()) {
          changed = true;
          WatchEvent<Path> ev = (WatchEvent<Path>) event;
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
          Logger.error("Can no longer watch the dev directory, was it deleted?");
        }
      } catch (IOException ioe) {
        Logger.error("Error watching dev directory for changes, check your permissions.");
      }
    }
  }
}

