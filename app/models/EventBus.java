package models;

import groovy.lang.Closure;
import java.util.ArrayList;
import java.util.HashMap;

class EventHandler<A> {
  public ArrayList<Closure<A>> closures = new ArrayList();

  public void addClosure (Closure<A> closure) {
    this.closures.add(closure);
  }

  public void removeClosure (Closure<A> closure) {
    this.closures.remove(closure);
  }

  public void emit (A... data) {
    for (Closure<A> closure : this.closures) {
      closure.call(data);
    }
  }

}

public class EventBus <T> {

  private HashMap<String, EventHandler<T>> events = new HashMap();

  public void register (String eventName) {
    this.events.put(eventName, new EventHandler());
  }

  public void unregister (String eventName) {
    this.events.remove(eventName);
  }

  public void on (String eventName, Closure closure) throws Exception {
    EventHandler<T> event = getEvent(eventName);
    event.addClosure(closure);
  }

  private EventHandler<T> getEvent (String eventName) throws Exception {
    EventHandler<T> event = this.events.get(eventName);
    if (event == null) {
      throw new Exception("Event " + eventName + " must be registered before it can be used");
    }
    return event;
  }

  public void off (String eventName, Closure closure) throws Exception {
    EventHandler<T> event = getEvent(eventName);
    event.removeClosure(closure);
  }

  public void emit (String eventName, T... payload) throws Exception {
    EventHandler<T> event = getEvent(eventName);
    event.emit(payload);
  }

  public void clear () {
    events.clear();
  }

}