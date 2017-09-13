package models;


public class ContentFetcher {
  public Experiment selectedExperiment;

  public ContentFetcher(Experiment selectedExperiment) {
    this.selectedExperiment = selectedExperiment;
  }

  public String get(String name) {
    Content c = selectedExperiment.getContentByName(name);
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
    if (c == null) return " ";
    return c.toJson().toString();
  }

  public String get(String name, Object... parameters) {
    String returnContent = this.get(name);

    for (int i = 0; i < parameters.length; i++) {
      returnContent = returnContent.replace("{" + i + "}", parameters[i].toString());
    }

    return returnContent;
  }
}
