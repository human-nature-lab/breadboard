package controllers;

import models.*;
import org.apache.commons.io.FileUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.node.ObjectNode;
import org.imgscalr.Scalr;
import org.mindrot.jbcrypt.BCrypt;
import play.Logger;
import play.data.Form;
import play.libs.Json;
import play.mvc.Content;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import play.mvc.WebSocket;
import views.html.breadboard;
import views.html.createFirstUser;
import views.html.login;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;


public class Application extends Controller {
  public static Result login() {
    if (User.findRowCount() == 0) {
      return ok(createFirstUser.render(Form.form(CreateFirstUser.class)));
    } else {
      return ok(login.render(Form.form(Login.class)));
    }
  }

  public static Result createFirstUser() {
    if (User.findRowCount() == 0) {
      return ok(createFirstUser.render(Form.form(CreateFirstUser.class)));
    } else {
      return ok(login.render(Form.form(Login.class)));
    }
  }

  public static Result addFirstUser() {
    Form<CreateFirstUser> createFirstUserForm = Form.form(CreateFirstUser.class).bindFromRequest();

    if (createFirstUserForm.hasErrors()) {
      //Logger.debug("loginForm.hasErrors():");
      //Logger.debug("Email:" + loginForm.get().email);
      //Logger.debug("Password:" + loginForm.get().password);
      return badRequest(createFirstUser.render(createFirstUserForm));
    } else {
      String email = createFirstUserForm.get().email;
      String password = createFirstUserForm.get().newPassword;

      session("email", email);

      String uid = UUID.randomUUID().toString();
      session("uid", uid);

      User user = new User();
      user.email = email;
      user.password = BCrypt.hashpw(password, BCrypt.gensalt());
      user.role = "admin";
      user.currentScript = "";
      user.experimentInstanceId = -1l;
      user.selectedExperiment = null;
      user.save();

      if (user != null) {
        Logger.info("authenticate: uid = " + uid);
        user.uid = uid;
        user.update();
        if (user.role.equals("admin")) {
          return redirect(routes.Application.index());
        } else if (user.role.equals("amt_admin")) {
          return redirect(routes.AMTAdmin.index());
        }
      }

      return badRequest(createFirstUser.render(createFirstUserForm));
    }
  }



  public static Result authenticate() {
    Form<Login> loginForm = Form.form(Login.class).bindFromRequest();

    if (loginForm.hasErrors()) {
      //Logger.debug("loginForm.hasErrors():");
      //Logger.debug("Email:" + loginForm.get().email);
      //Logger.debug("Password:" + loginForm.get().password);
      return badRequest(login.render(loginForm));
    } else {
      String email = loginForm.get().email;

      session("email", email);

      String uid = UUID.randomUUID().toString();
      session("uid", uid);

      User user = User.findByEmail(email);

      if (user != null) {
        Logger.info("authenticate: uid = " + uid);
        user.uid = uid;
        user.update();
        if (user.role.equals("admin")) {
          return redirect(routes.Application.index());
        } else if (user.role.equals("amt_admin")) {
          return redirect(routes.AMTAdmin.index());
        }
      }

      return badRequest(login.render(loginForm));
    }
  }

  public static Result logout() {
    session().clear();
    flash("success", "You've been logged out");
    return redirect(routes.Application.login());
  }

  public static Result index() {
    if (session("email") == null) {
      if (User.findRowCount() == 0) {
        return redirect(routes.Application.createFirstUser());
      } else {
        return redirect(routes.Application.login());
      }
    } else {
      return ok(breadboard.render());
    }
  }

  public static Result uploadImage() {
    MultipartFormData body = request().body().asMultipartFormData();
    FilePart picture = body.getFile("picture");
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
        image.fileName = fileName;
        image.file = FileUtils.readFileToByteArray(file);
        image.contentType = contentType;

        // Create thumbnail
        InputStream in = new ByteArrayInputStream(image.file);
        BufferedImage bImage = ImageIO.read(in);
        BufferedImage scaledImage = Scalr.resize(bImage, 100);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(scaledImage, image.contentType.replaceAll("image/", "").toLowerCase(), baos);
        baos.flush();

        byte[] imageBytes = baos.toByteArray();
        image.thumbFile = imageBytes;
        image.thumbFileName = (image.fileName).concat("_thumb");
        baos.close();

        experiment.images.add(image);
        experiment.save();
      } catch (NullPointerException npe) {
        Logger.error("IOException in uploadImage(): " + npe.getMessage());
        return ok("Error uploading");
      } catch (NumberFormatException nfe) {
        Logger.error("IOException in uploadImage(): " + nfe.getMessage());
        return ok("Error uploading");
      } catch (IOException ioe) {
        Logger.error("IOException in uploadImage(): " + ioe.getMessage());
        return ok("Error uploading");
      }


      //TODO: For web deployment consider storing images to filesystem
      //Integer nextId = (Integer)Ebean.nextId(Image.class);
      //String uniqueFileName = nextId.toString() + "_" + fileName;
      //Logger.info("uniquefileName = " + uniqueFileName);
      //String uploadPath = "";


      return ok("File uploaded");
    } else {
      return ok("Error uploading");
    }
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

  public static Result getImageThumb(Long imageId) {
    Image image = Image.findById(imageId);

    if (image != null) {
      if (image.contentType != null && image.thumbFile != null) {
        response().setContentType(image.contentType);
        return ok(image.thumbFile);
      }
    }

    return notFound();
  }

  public static Result saveUserSettings() {
    Form<UserSettings> userSettingsForm = Form.form(UserSettings.class);
    userSettingsForm = userSettingsForm.bindFromRequest();

    ObjectNode result = Json.newObject();

    result.put("success", false);
    if (userSettingsForm.hasErrors()) {
      result.put("error", userSettingsForm.globalError().message());
      return ok(result);
    }

    UserSettings userSettings = userSettingsForm.get();

    userSettings.user.password = BCrypt.hashpw(userSettings.newPassword, BCrypt.gensalt());
    userSettings.user.update();
    result.put("success", true);

    return ok(result);
  }

  public static Result dataCsv(Long experimentId) {
    Experiment experiment = Experiment.findById(experimentId);
    final StringBuilder csv = new StringBuilder();
    List<Parameter> parameters = experiment.parameters;
    csv.append("ID,Instance,");
    for (Parameter parameter : parameters) {
      csv.append(parameter.name + ",");
    }
    csv.deleteCharAt(csv.length() - 1);
    csv.append("\n");
    List<ExperimentInstance> instances = experiment.instances;
    for (ExperimentInstance instance : instances) {
      List<Data> instanceData = instance.data;
      csv.append(instance.id + ",");
      csv.append(instance.name + ",");
      for (Data d : instanceData) {
        csv.append(d.value + ",");
      }
      csv.deleteCharAt(csv.length() - 1);
      csv.append("\n");
    }


    Content content = new Content() {
      @Override
      public String body() {
        return csv.toString();
      }

      @Override
      public String contentType() {
        return "text/csv";
      }
    };
    response().setHeader("Content-Disposition", "attachment; filename=\"experiment_" + experimentId + ".csv\"");
    return ok(content);
  }

  public static Result eventCsv(Long experimentInstanceId) {
    ExperimentInstance ei = ExperimentInstance.findById(experimentInstanceId);
    String experimentInstanceName = ei.name;
    final StringBuilder csv = new StringBuilder();
    csv.append("id,event,datetime,data name,data value\n");
    Collections.sort(ei.events, new Comparator<Event>() {
      @Override
      public int compare(Event o1, Event o2) {
        return o1.datetime.compareTo(o2.datetime);
      }
    });
    for (Event event : ei.events) {
      List<EventData> eventDatas = event.eventData;
      for (EventData eventData : eventDatas) {
        csv.append(event.id + "," + event.name + ",\"" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,S").format(event.datetime) +
            "\"," + eventData.name + "," + eventData.valueToCSV() + "\n");
      }
    }

    Content content = new Content() {
      @Override
      public String body() {
        return csv.toString();
      }

      @Override
      public String contentType() {
        return "text/csv";
      }
    };
    response().setHeader("Content-Disposition", "attachment; filename=\"" + experimentInstanceName + "_" + experimentInstanceId + ".csv\"");
    return ok(content);
  }

  /**
   * Handle the websocket.
   */
  public static WebSocket<JsonNode> connect() {
    return new WebSocket<JsonNode>() {
      // Called when the Websocket Handshake is done.
      public void onReady(final WebSocket.In<JsonNode> in, final WebSocket.Out<JsonNode> out) {
        try {
          Breadboard.connect(in, out);
        } catch (Exception ex) {
          ex.printStackTrace();
        }
      }
    };
  }

  public static class Login {
    public String email;
    public String password;

    public String validate() {
      Logger.debug("email: " + email);
      //Logger.debug("password: " + password);
      if (User.authenticate(email, password) == null) {
                /*
                 * This code makes the app throw an java.lang.reflect.InvocationTargetException
                 * when the database needs to be reloaded into memory
				for (User u : User.findAll())
				{
					Logger.info(u.toString());
				}
				*/

        return "Invalid user or password";
      }
      return null;
    }
  }

}
