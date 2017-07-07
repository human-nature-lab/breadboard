package models;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang.StringEscapeUtils;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.node.ArrayNode;
import org.codehaus.jackson.node.ObjectNode;
import play.Logger;
import play.libs.Json;

public class ContentFetcher {
  public Experiment selectedExperiment;

  public ContentFetcher(Experiment selectedExperiment) {
    this.selectedExperiment = selectedExperiment;
  }

  public String get(String name, Object... parameters) {
    ObjectNode contentObject = Json.newObject();
    ArrayNode contentArray = contentObject.putArray("contentArray");

    List<Content> contentList = selectedExperiment.getContentByName(name);
    for(int i = 0; i < contentList.size(); i++) {
      Content c = contentList.get(i);
      String contentString = c.html;

      for (int j = 0; j < parameters.length; j++) {
        try {
          // if parameters[j] is a JSON string, select the appropriate localized fill
          ObjectMapper objectMapper = new ObjectMapper();
          //JsonNode parameterObject = objectMapper.readTree(parameters[j].toString());

          JsonNode rootNode = objectMapper.readTree(parameters[j].toString());
          ArrayNode childContentArray = (ArrayNode) rootNode.get("contentArray");
          if (childContentArray == null) {
            contentString = contentString.replace("{" + j + "}", parameters[j].toString());
          } else {
            Iterator<JsonNode> childContentIterator = childContentArray.getElements();
            while (childContentIterator.hasNext()) {
              JsonNode childContentNode = childContentIterator.next();
              String language = childContentNode.get("language").toString();
              String text = childContentNode.get("text").toString();
              // Strip quotes
              text = text.substring(1, text.length() - 1);
              language = language.substring(1, language.length() - 1);
              if (language.equals(c.language)) {
                contentString = contentString.replace("{" + j + "}", StringEscapeUtils.unescapeJava(text));
              }
            }
          }
        } catch (IOException ioe) {
          // If unable to parse as JSON, do simple string replacement
          contentString = contentString.replace("{" + j + "}", parameters[j].toString());
        }
      }

      ObjectNode co = Json.newObject();
      co.put("language", c.language);
      co.put("text", contentString);
      contentArray.add(co);
    }
    return contentObject.toString();
  }

  public class ContentArray {
    @JsonProperty("contentArray")
    private List<ContentNode> contentArray;

    @JsonCreator
    public ContentArray(@JsonProperty("contentArray")List<ContentNode> contentArray) {
      this.contentArray = contentArray;
    }

    public List<ContentNode> getContentArray() {
      return contentArray;
    }

    public void setContentArray(@JsonProperty("contentArray")List<ContentNode> contentArray) {
      this.contentArray = contentArray;
    }

    @Override
    public String toString() {
      return contentArray.toString();
    }
  }

  public static class ContentNode {
    @JsonProperty("language")
    private String language;
    @JsonProperty("text")
    private String text;

    @JsonCreator
    public ContentNode(@JsonProperty("language")String language, @JsonProperty("text")String text) {
      this.language = language;
      this.text = text;
    }

    public String getLanguage() {
      return language;
    }

    public void setLanguage(@JsonProperty("language")String language) {
      this.language = language;
    }

    public String getText() {
      return text;
    }

    public void setText(@JsonProperty("text")String text) {
      this.text = text;
    }
  }
}
