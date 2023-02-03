package controllers;

import play.libs.WS;
import play.Logger;
import play.mvc.Result;
import static play.libs.F.*;
import static play.libs.F.Promise;
import static play.mvc.Controller.response;
import static play.mvc.Results.ok;
import static play.mvc.Results.badRequest;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BundlesController {

  private static Boolean isChildOf(String child, String parent) {
    Path parentPath = Paths.get(parent);
    Path childPath = Paths.get(child);
    return isChildOf(childPath, parentPath);
  }

  private static Boolean isChildOf(Path child, Path parent) {
    while (child != null) {
      if (child.equals(parent)) {
        return true;
      }
      child = child.getParent();
    }
    return false;
  }

  private static Boolean isCacheable (Path filePath) {
    String[] exts = {"js", "css", "png", "jpg", "jpeg", "woff", "webp", "woff2", "js.map", "ico", "ttf", "otf"};
    for (String ext : exts) {
      if (filePath.endsWith("." + ext)) {
        return true;
      }
    }
    return false;
  }

  public static Result asset(String filePath) {

    String dir = play.Play.application().configuration().getString("application.staticPath", "generated");
    Path dirPath = Paths.get(dir).normalize();
    if (!dirPath.isAbsolute()) {
      dirPath = dirPath.toAbsolutePath();
    }

    Path assetPath = dirPath.resolve(filePath).normalize();
    
    // Prevent path traversal attacks by checking that this file is contained in the configured dir
    if (!isChildOf(assetPath, dirPath)) {
      return badRequest("Invalid path.");
    }
    
    Logger.trace("serving " + filePath + " from " + assetPath.toAbsolutePath().toString());
    if (isCacheable(assetPath)) {
      response().setHeader("Cache-Control", "max-age=86400 public");
    }
    File file = new File(assetPath.toString());
    return ok(file, true);
  }

}
