package controllers;

import play.libs.WS;
import play.Logger;
import play.mvc.Result;
import security.PathSafety;
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

    // Prevent path traversal attacks by checking that this file is contained in the configured dir
    Path assetPath = PathSafety.resolveContained(dirPath, filePath);
    if (assetPath == null) {
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
