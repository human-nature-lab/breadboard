package controllers;

import models.Experiment;
import models.Image;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import play.Logger;
import play.Play;
import play.mvc.Controller;
import play.mvc.Http;
import play.mvc.Result;
import play.mvc.Security;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;

public class ImagesController extends Controller {

  @Security.Authenticated(Secured.class)
  public static Result uploadImage() {
    Http.MultipartFormData body = request().body().asMultipartFormData();
    Http.MultipartFormData.FilePart picture = body.getFile("picture[file]");
    if (picture != null) {
      String fileName = picture.getFilename();
      String contentType = picture.getContentType();
      File file = picture.getFile();

      Map<String, String[]> values = body.asFormUrlEncoded();

      try {
        String experimentId = values.get("experimentId")[0];
        Long eid = Long.parseLong(experimentId);
        Experiment experiment = Experiment.findById(eid);
        Image image = new Image();
        String uniqueFileName = fileName;
        // If there is already an Image with the same filename, append a "-2" to the filename
        int appendNumber = 2;
        while (Image.find.where().eq("experiment_id", experimentId).eq("file_name", uniqueFileName).findRowCount() > 0) {
          uniqueFileName = FilenameUtils.removeExtension(fileName) + "-" + appendNumber + "." + FilenameUtils.getExtension(fileName);
          appendNumber++;
        }
        image.fileName = uniqueFileName;
        image.file = FileUtils.readFileToByteArray(file);
        image.contentType = contentType;
        experiment.images.add(image);
        experiment.save();
        return ok(image.toJson());
      } catch (NullPointerException npe) {
        Logger.error("NullPointerException in uploadImage(): " + npe.getMessage());
        return ok("Error uploading");
      } catch (NumberFormatException nfe) {
        Logger.error("NumberFormatException in uploadImage(): " + nfe.getMessage());
        return ok("Error uploading");
      } catch (IOException ioe) {
        Logger.error("IOException in uploadImage(): " + ioe.getMessage());
        return ok("Error uploading");
      }
    } else {
      return ok("Error uploading");
    }
  }

  @Security.Authenticated(Secured.class)
  public static Result removeImage(Long imageId) {
    Image image = Image.find.byId(imageId);
    if (image != null) {
      image.delete();
    } else {
      return badRequest("Invalid image ID");
    }
    return ok();
  }

  public static Result getImage(Long imageId) {
    Image image = Image.findById(imageId);

    if (image != null) {
      if (image.contentType != null && image.file != null) {
        response().setContentType(image.contentType);
        return ok(image.file);
      }
    }

    return notFound();
  }

  public static Result getImageByFileName(Long experimentId, String fileName) {
    Experiment experiment = Experiment.findById(experimentId);
    if (experiment == null) {
      return notFound();
    }

    if (experiment.fileMode) {
      File imageDirectory = new File(Play.application().path().toString() + "/dev/" + experiment.getDirectoryName() + "/Images");
      try {
        File file = FileUtils.getFile(imageDirectory, fileName);
        String contentType = Files.probeContentType(file.toPath());
        byte[] contents = FileUtils.readFileToByteArray(file);
        response().setContentType(contentType);
        return ok(contents);
      } catch (IOException ioe) {
        return notFound();
      }
    }

    Image image = Image.find.where()
        .eq("experiment_id", experimentId)
        .eq("file_name", fileName)
        .setMaxRows(1)
        .findUnique();

    if (image != null) {
      if (image.contentType != null && image.file != null) {
        response().setContentType(image.contentType);
        return ok(image.file);
      }
    }

    return notFound();
  }

  public static Result getImageThumb(Long imageId) {
    Image image = Image.findById(imageId);

    if (image != null) {
      if (image.contentType != null && image.thumbFile != null && image.thumbFile.length > 0) {
        response().setContentType(image.contentType);
        return ok(image.thumbFile);
      }
    }

    return notFound();
  }
}
