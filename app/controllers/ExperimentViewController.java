package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;

public class ExperimentViewController extends Controller {

  @Security.Authenticated(Secured.class)
  public static Result getExperimentView(Long experimentViewId) {
    ExperimentView ev = ExperimentView.findById(experimentViewId);
    if(ev == null) {
      return badRequest("Invalid ExperimentView ID");
    }

    ObjectNode returnJson = Json.newObject();
    returnJson.put("experimentView", ev.toJson());
    return ok(returnJson);
  }

  @Security.Authenticated(Secured.class)
  public static Result createContent(Long experimentViewId) {
    ExperimentView ev = ExperimentView.findById(experimentViewId);
    String name;
    String type;
    String content;
    Integer loadOrder;

    if(ev == null) {
      return badRequest("Invalid ExperimentView ID");
    }

    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      name = json.findPath("name").textValue();
      type = json.findPath("type").textValue();
      content = json.findPath("content").textValue();
      loadOrder = json.findPath("content").intValue();
    }

    if (name == null || type == null || content == null || loadOrder == null) {
      return badRequest("Please provide name, type, content, and loadOrder.");
    }

    ExperimentViewContent evc = new ExperimentViewContent();
    evc.name = name;
    evc.type = type;
    evc.content = content;
    evc.loadOrder = loadOrder;

    ev.content.add(evc);
    ev.save();
    evc.save();

    return ok(evc.toJson());
  }

  @Security.Authenticated(Secured.class)
  public static Result getContent(Long experimentViewContentId) {
    ExperimentViewContent evc = ExperimentViewContent.findById(experimentViewContentId);
    if(evc == null) {
      return badRequest("Invalid ExperimentViewContent ID");
    }

    ObjectNode returnJson = Json.newObject();
    returnJson.put("content", evc.content);
    return ok(returnJson);
  }

  @Security.Authenticated(Secured.class)
  public static Result updateContent(Long experimentViewContentId) {
    ExperimentViewContent evc = ExperimentViewContent.findById(experimentViewContentId);
    if(evc == null) {
      return badRequest("Invalid ExperimentViewContent ID");
    }

    String content;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      content = json.findPath("content").textValue();
    }

    if (content == null) {
      return badRequest("Please provide content.");
    }
    evc.content = content;
    evc.save();

    return ok();
  }

  @Security.Authenticated(Secured.class)
  public static Result deleteContent(Long experimentViewId, Long experimentViewContentId) {
    ExperimentView ev = ExperimentView.findById(experimentViewId);
    if(ev == null) {
      return badRequest("Invalid ExperimentView ID");
    }

    ExperimentViewContent evc = ExperimentViewContent.findById(experimentViewContentId);
    if(evc == null) {
      return badRequest("Invalid ExperimentViewContent ID");
    }

    ev.content.remove(ev);
    ev.update();
    evc.delete();

    return ok();
  }

}
