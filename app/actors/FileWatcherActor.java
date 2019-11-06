package actors;

import actors.FileWatcherActorProtocol.FileWatch;
import akka.actor.UntypedActor;
import models.Admin;
import models.FileWatcher;
import org.apache.commons.io.FileUtils;
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

      for (WatchEvent<?> event: watchKey.pollEvents()) {
        WatchEvent<Path> ev = (WatchEvent<Path>) event;
        Path name = ev.context();
        Path child = dir.resolve(name);
        Logger.debug(child.toString());
        String selectedExperimentDirectory = (fileWatcher.getAdminListeners().get(0).getUser().selectedExperiment == null) ? "" : fileWatcher.getAdminListeners().get(0).getUser().selectedExperiment.getDirectoryName();
        boolean selectedExperimentChanged = child.toString().contains(selectedExperimentDirectory);

        if (ev.kind() == ENTRY_CREATE && child.toFile().isDirectory()) {
          // new directory, need to watch it
          try {
            fileWatcher.registerRecursive(child);
          } catch (IOException ioe) {
            Logger.error("Unable to watch directory " + child.toFile().getPath() + " check the permissions.");
          }
          Logger.debug("DIRECTORY ENTRY_CREATE");
        } else if ((ev.kind() == ENTRY_CREATE || ev.kind() == ENTRY_MODIFY) && child.toFile().isFile() && selectedExperimentChanged) {
          // A file was modified or created in the selected experiment directory, update the admin
          changed = true;
          if (child.toFile().getParent().endsWith("/Steps")) {
            // Step was modified or created, send to ScriptEngine
            try {
              String stepContents = FileUtils.readFileToString(child.toFile());
              fileWatcher.loadStep(stepContents, fileWatcher.getAdminListeners().get(0).getOut(), child.getFileName().toString());
            } catch (IOException ioe) {
              Logger.error("Unable to read contents of Step " + child.getFileName().toString() + " check your permissions.");
            }
          }
          Logger.debug("FILE ENTRY_MODIFY || ENTRY_CREATE");
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

