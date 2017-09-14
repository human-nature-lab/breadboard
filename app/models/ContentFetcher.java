package models;


public class ContentFetcher {
  public Experiment selectedExperiment;

  public ContentFetcher(Experiment selectedExperiment) {
    this.selectedExperiment = selectedExperiment;
  }

  public String get(String name) {
    Content c = selectedExperiment.getContentByName(name);
    Translation t = c.translations.get(0);
    /*
    if (c == null)
      return " ";
    String returnString = "[";
    for (int i = 0; i < c.translations.size(); i++) {
      Translation t = c.translations.get(i);
      returnString += "{'language':" + t.language.code;
      returnString += ",'html':" + t.html + "}";
      if (i < (c.translations.size() - 1)) {
        returnString += ",";
      }
    }
    returnString += "]";
    return returnString;
    */
    //if (c == null) return " ";
    //return c.toJson().toString();
    return t.html;
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
    Content c = selectedExperiment.getContentByName(name);
    for (Translation t : c.translations) {
      if (t.language.code.equals(languageCode)) {
        returnString = t.html;
      }
    }
    for (int i = 0; i < parameters.length; i++) {
      returnString = returnString.replace("{" + i + "}", parameters[i].toString());
    }

    return returnString;
  }
}
