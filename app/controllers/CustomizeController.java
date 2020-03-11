package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.*;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;

public class CustomizeController extends Controller {

  @Security.Authenticated(Secured.class)
  public static Result getContent(Long experimentViewId) {  // getClientHtml
    ExperimentView ev = ExperimentView.findById(experimentViewId);
    if(ev == null) {
      return badRequest("Invalid ExperimentView ID");
    }

    ObjectNode returnJson = Json.newObject();
    returnJson.put("content", ev.content);
    return ok(returnJson);
  }

  @Security.Authenticated(Secured.class)
  public static Result getScript(Long experimentViewScriptId) { // getClientGraph
    ExperimentViewScript evs = ExperimentViewScript.findById(experimentViewScriptId);
    if(evs == null) {
      return badRequest("Invalid ExperimentViewScript ID");
    }

    ObjectNode returnJson = Json.newObject();
    returnJson.put("script", evs.script);
    return ok(returnJson);
  }

  @Security.Authenticated(Secured.class)
  public static Result getStyle(Long experimentViewId) {
    ExperimentView ev = ExperimentView.findById(experimentViewId);
    if(ev == null) {
      return badRequest("Invalid ExperimentView ID");
    }

    ObjectNode returnJson = Json.newObject();
    returnJson.put("style", ev.style);
    return ok(returnJson);
  }

  @Security.Authenticated(Secured.class)
  public static Result updateContent(Long experimentViewId) { // updateClientHtml
    ExperimentView ev = ExperimentView.findById(experimentViewId);
    if(ev == null) {
      return badRequest("Invalid ExperimentView ID");
    }

    String content;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      content = json.findPath("content").textValue();
    }

    if (content == null) {
      return badRequest("Please provide client HTML.");
    }
    ev.content = content;
    ev.save();

    return ok();
  }

  @Security.Authenticated(Secured.class)
  public static Result updateScript(Long scriptId) { // updateClientGraph
    ExperimentViewScript evs = ExperimentViewScript.findById(scriptId);
    String script;
    if(evs == null) {
      return badRequest("Invalid ExperimentViewScript ID");
    }

    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      script = json.findPath("script").textValue();
    }

    if (script == null) {
      return badRequest("Please provide script.");
    }

    evs.script = script;
    evs.save();

    return ok();
  }

  public static Result updateTemplate(Long templateId) {
    ExperimentViewTemplate evt = ExperimentViewTemplate.findById(templateId);
    String content;
    if(evt == null) {
      return badRequest("Invalid ExperimentViewTemplate ID");
    }

    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      content = json.findPath("content").textValue();
    }

    if (content == null) {
      return badRequest("Please provide content.");
    }

    evt.content = content;
    evt.save();

    return ok();
  }

  public static Result createDependency(Long experimentViewId) {
    ExperimentView ev = ExperimentView.findById(experimentViewId);
    String content;
    String position;

    if(ev == null) {
      return badRequest("Invalid ExperimentView ID");
    }

    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      content = json.findPath("content").textValue();
      position = json.findPath("position").textValue();
    }

    if (content == null || position == null) {
      return badRequest("Please provide content and position.");
    }

    ExperimentViewDependency evd = new ExperimentViewDependency();
    evd.content = content;
    evd.position = position;
    evd.content = content;
    ev.dependencies.add(evd);
    ev.save();
    evd.save();

    return ok(evd.toJson());
  }

  public static Result updateDependency(Long dependencyId) {
    ExperimentViewDependency evd = ExperimentViewDependency.findById(dependencyId);
    String content;
    String position;

    if(evd == null) {
      return badRequest("Invalid ExperimentViewDependency ID");
    }

    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      content = json.findPath("content").textValue();
      position = json.findPath("position").textValue();
    }

    if (content == null) {
      return badRequest("Please provide content and position.");
    }

    evd.content = content;
    evd.position = position;
    evd.save();

    return ok();
  }

  @Security.Authenticated(Secured.class)
  public static Result updateStyle(Long experimentViewId) {
    ExperimentView ev = ExperimentView.findById(experimentViewId);
    if(ev == null) {
      return badRequest("Invalid ExperimentView ID");
    }

    String style;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      style = json.findPath("style").textValue();
    }

    if (style == null) {
      return badRequest("Please provide style.");
    }

    ev.style = style;
    ev.save();

    return ok();
  }

}
