package controllers;

import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import java.util.HashMap;
import java.util.Locale;

public class LanguageController extends Controller {

  public static Result getLanguages() {
    HashMap<String, String> languageMap = new HashMap<>();
    Locale[] languages = Locale.getAvailableLocales();
    for (Locale language : languages) {
      if (language.getISO3Language().length() > 0 && language.getDisplayLanguage().length() > 0) {
        languageMap.put(language.getISO3Language(), language.getDisplayLanguage());
      }
    }
    ObjectNode returnJson = Json.newObject();
    returnJson.put("languages", Json.toJson(languageMap));
    return ok(returnJson);
  }

}
