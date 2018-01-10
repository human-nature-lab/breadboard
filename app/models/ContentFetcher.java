package models;


import play.Logger;

public class ContentFetcher {
  public Experiment selectedExperiment;
  public String defaultLanguage;
  public ContentFetcher(Experiment selectedExperiment) {
    this.selectedExperiment = selectedExperiment;
  }
  public void setDefaultLanguage(String defaultLanguage) {
    this.defaultLanguage = defaultLanguage;
  }

  public String get(String name) {
    if (defaultLanguage != null) {
      return this.getTranslated(name, defaultLanguage);
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

  public String get(String name, Object... parameters) {
    String returnContent = this.get(name);
    for (int i = 0; i < parameters.length; i++) {
      returnContent = returnContent.replace("{" + i + "}", parameters[i].toString());
    }

    return returnContent;
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
