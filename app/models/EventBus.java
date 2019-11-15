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

  public EventHandler<T> register (String eventName) {
    EventHandler<T> handler = new EventHandler();
    this.events.put(eventName, handler);
    return handler;
  }

  public void unregister (String eventName) {
    this.events.remove(eventName);
  }

  public void on (String eventName, Closure closure) {
    EventHandler<T> event = this.events.get(eventName);
    // Automatically register the event if it hasn't been registered yet
    if (event == null) {
      event = register(eventName);
    }
    event.addClosure(closure);
  }

  public void off (String eventName, Closure closure) {
    EventHandler<T> event = this.events.get(eventName);
    if (event != null) {
      event.removeClosure(closure);
    }
  }

  public void emit (String eventName, T... payload) {
    EventHandler<T> event = this.events.get(eventName);
    event.emit(payload);
  }

  public void clear () {
    events.clear();
  }

}