package models;


import java.util.Map;
import com.avaje.ebean.Expr;
import play.Logger;

public class ContentFetcher {
  public Experiment selectedExperiment;
  public Language defaultLanguage;
  public ContentFetcher(Experiment selectedExperiment) {
    this.selectedExperiment = selectedExperiment;
  }

  public void setDefaultLanguage(String languageString) {
    Language language = Language.find.where()
        .or(Expr.eq("code", languageString),
            Expr.eq("name", languageString))
        .setMaxRows(1)
        .findUnique();

    this.defaultLanguage = language;
  }

  public String get(String name) {
    if (defaultLanguage != null) {
      return this.getTranslated(name, defaultLanguage.getCode());
    }

    Content c = Content.find.where()
        .eq("experiment_id", selectedExperiment.id)
        .eq("name", name)
        .setMaxRows(1)
        .findUnique();

    if (c != null){
      Translation t = Translation.find.where()
          .eq("content_id", c.id)
          .setMaxRows(1)
          .findUnique();

      if (t != null) {
        return t.getHtml();
      }
    } else {
      Logger.debug("get -> c == null");
    }
    return "";
  }

  /**
   * Interpolate numbered curly brackets in a string. Ex: ("These are 3 numbers: {0}, {1}, and {2}", [1, 2, 3]) -> "This is one: 1, 2, and 3"
   * @param content
   * @param parameters
   * @return
   */
  public String interpolate (String content, Object[] parameters) {
    String returnContent = content;
    for (int i = 0; i < parameters.length; i++) {
      returnContent = returnContent.replace("{" + i + "}", parameters[i].toString());
    }
    return returnContent;
  }

  public String get(String name, Object... parameters) {
    String content = this.get(name);
    return this.interpolate(content, parameters);
  }

  public String getTranslated(String name, String languageCode, Object... parameters) {
    String returnString = "";
    Content c = Content.find.where()
        .eq("experiment_id", selectedExperiment.id)
        .eq("name", name)
        .setMaxRows(1)
        .findUnique();
    if (c != null) {
      Language l = Language.find.where()
          .eq("code", languageCode)
          .setMaxRows(1)
          .findUnique();

      Translation t = Translation.find.where()
          .eq("content_id", c.id)
          .eq("languages_id", l.id)
          .setMaxRows(1)
          .findUnique();

      returnString = t.getHtml();

      for (int i = 0; i < parameters.length; i++) {
        returnString = returnString.replace("{" + i + "}", parameters[i].toString());
      }
    } else {
      Logger.debug("getTranslated -> c == null");
    }
    return returnString;
  }
}
