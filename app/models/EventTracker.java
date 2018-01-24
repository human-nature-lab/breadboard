package models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import play.Logger;

import java.util.*;

public class EventTracker {

  @JsonIgnore
  private ExperimentInstance experimentInstance;
  private boolean enabled = true;

  public ExperimentInstance getExperimentInstance() {
    return experimentInstance;
  }

  public void enable() {
    enabled = true;
  }

  public void disable() {
    enabled = false;
  }

  public void setExperimentInstance(ExperimentInstance experimentInstance) {
    this.experimentInstance = experimentInstance;
  }

  public void track(String name, LinkedHashMap<Object, Object> data) {
    List<Map<String, String>> nameValues = new ArrayList<Map<String, String>>();

    for (Object key : data.keySet()) {
      Map<String, String> dataMap = new HashMap<String, String>();
      dataMap.put("name", key.toString());
      dataMap.put("value", (data.get(key) == null) ? "null" : data.get(key).toString());
      nameValues.add(dataMap);
    }

    this.track(name, nameValues);
  }

  public void track(String name, List<Map<String, String>> nameValues) {
    if (!enabled) {
      return;
    }
    if (experimentInstance == null) {
      Logger.warn("No experimentInstance. Skips event tracking.");
      return;
    }
    Event event = new Event();
    event.name = name;
    event.experimentInstance = experimentInstance;
    //event.save();
    for (Map<String, String> nameValue : nameValues) {
      EventData eventData = new EventData();
      eventData.name = nameValue.get("name");
      eventData.value = nameValue.get("value");
      event.addEventData(eventData);
    }
    event.save();
  }
}
