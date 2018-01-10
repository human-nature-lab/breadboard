package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.gson.Gson;
import models.Content;
import models.Experiment;
import models.Translation;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;

import java.io.Serializable;
import java.util.List;

public class ContentController extends Controller {

  public static Result getContent(Long experimentId) {
    Experiment experiment = Experiment.findById(experimentId);
    if(experiment == null) {
      return badRequest("Invalid Experiment ID");
    }

    ObjectNode returnJson = Json.newObject();

    ArrayNode jsonSteps = returnJson.putArray("content");
    for (Content c : experiment.content) {
      jsonSteps.add(c.toJson());
    }

    return ok(returnJson);
  }

  public static Result deleteContent(Long contentId) {
    Content content = Content.findById(contentId);
    if (content == null) {
      return badRequest("Invalid Content ID");
    }
    content.delete();
    return ok();
  }

  public static Result updateContent(Long contentId) {
    Gson gson = new Gson();
    Content content;
    Experiment experiment;
    String name;
    Long experimentId;
    List<Translation> translations;

    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      UpdateContentObject updateContentObject = gson.fromJson(json.toString(), UpdateContentObject.class);
      name = updateContentObject.name;
      experimentId = updateContentObject.experimentId;
      translations = updateContentObject.translations;
    }

    experiment = Experiment.findById(experimentId);

    if (experiment == null) {
      return badRequest("Invalid experiment ID.");
    }

    if (name == null) {
      return badRequest("Please provide content name.");
    }

    Boolean isNewContent = (contentId == -1);
    if (isNewContent) {
      content = new Content();
    } else {
      content = experiment.getContent(contentId);
    }

    if (content == null) {
      return badRequest("Invalid Content ID");
    }

    content.name = name;

    if (translations != null) {
      // Create or update translations
      for (Translation t : translations) {
        Translation translation;
        Boolean isNewTranslation = false;
        if (t.id == -1) {
          // New translation
          isNewTranslation = true;
          translation = new Translation();
        } else {
          translation = Translation.findById(t.id);
        }

        if (translation == null) {
          return badRequest("Invalid translation ID.");
        }

        translation.setLanguage(t.getLanguage());
        translation.setHtml(t.getHtml());

        if (isNewTranslation) {
          content.translations.add(translation);
        } else {
          translation.update();
        }
      }
    }

    if (isNewContent) {
      experiment.content.add(content);
    }
    experiment.save();

    return ok(content.toJson());
  }

  private class UpdateContentObject {
    public UpdateContentObject() {}
    public Long experimentId;
    public String name;
    public List<Translation> translations;
  }

}
