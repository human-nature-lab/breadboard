package actors;

import models.FileWatcher;

public class FileWatcherActorProtocol {
  public static class FileWatch {
    final FileWatcher fileWatcher;
    public FileWatch(FileWatcher fileWatcher) {
      this.fileWatcher = fileWatcher;
    }
  }
}

