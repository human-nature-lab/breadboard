package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import models.Experiment;
import models.Language;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;

import java.util.*;

public class LanguageController extends Controller {

  public static List<Language> seedLanguages() {
    Locale[] locales = Locale.getAvailableLocales();
    Set<String> addedLanguages = new HashSet<>();
    List<Language> returnLanguages = new ArrayList<>();
    for (Locale locale : locales) {
      if (locale.getISO3Language().length() > 0 &&
          locale.getDisplayLanguage().length() > 0 &&
          (! addedLanguages.contains(locale.getISO3Language() + '-' + locale.getDisplayLanguage()))) {
        addedLanguages.add(locale.getISO3Language() + '-' + locale.getDisplayLanguage());
        Language language = new Language();
        language.setName(locale.getDisplayLanguage());
        language.setCode(locale.getISO3Language());
        language.save();
        returnLanguages.add(language);
      }
    }
    return returnLanguages;
  }

  public static Result getLanguages() {
    ObjectNode returnJson = Json.newObject();
    ArrayNode jsonLanguages = returnJson.putArray("languages");
    List<Language> languages = Language.findAll();
    if (languages.isEmpty()) {
      // The database table is empty, add default languages
      languages = seedLanguages();
    }
    for (Language language : languages) {
      jsonLanguages.add(language.toJson());
    }
    return ok(returnJson);
  }

  @Security.Authenticated(Secured.class)
  public static Result addLanguage() {
    Long experimentId;
    Long languageId;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      experimentId = json.findValue("experimentId").asLong();
      languageId = json.findValue("languageId").asLong();
    }

    Experiment experiment = Experiment.findById(experimentId);
    Language language = Language.findById(languageId);

    if (experiment == null || language == null) {
      return badRequest("Invalid experiment or language ID");
    }

    if (!experiment.languages.contains(language)) {
      experiment.languages.add(language);
      experiment.save();
    }

    return ok(language.toJson());
  }

  @Security.Authenticated(Secured.class)
  public static Result removeLanguage() {
    Long experimentId;
    Long languageId;
    JsonNode json = request().body().asJson();
    if(json == null) {
      return badRequest("Expecting Json data");
    } else {
      experimentId = json.findValue("experimentId").asLong();
      languageId = json.findValue("languageId").asLong();
    }

    Experiment experiment = Experiment.findById(experimentId);
    Language language = Language.findById(languageId);

    if (experiment == null || language == null) {
      return badRequest("Invalid experiment or language ID");
    }

    experiment.languages.remove(language);
    experiment.save();
    experiment.saveManyToManyAssociations("languages");

    return ok();
  }
}
