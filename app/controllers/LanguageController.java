package controllers;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import play.libs.Json;
import play.mvc.Controller;
import play.mvc.Result;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class LanguageController extends Controller {

  public static Result getLanguages() {
    ObjectNode returnJson = Json.newObject();
    ArrayNode jsonLanguages = returnJson.putArray("languages");
    Locale[] languages = Locale.getAvailableLocales();
    Set<String> addedLanguages = new HashSet<>();
    for (Locale language : languages) {
      if (language.getISO3Language().length() > 0 &&
          language.getDisplayLanguage().length() > 0 &&
          (! addedLanguages.contains(language.getISO3Language() + '-' + language.getDisplayLanguage()))) {
        addedLanguages.add(language.getISO3Language() + '-' + language.getDisplayLanguage());
        ObjectNode jsonLanguage = Json.newObject();
        jsonLanguage.put("name", language.getDisplayLanguage());
        jsonLanguage.put("iso3", language.getISO3Language());
        jsonLanguages.add(jsonLanguage);
      }
    }
    return ok(returnJson);
  }

}
