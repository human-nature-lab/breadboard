package models;

import play.Logger;
import groovy.lang.Closure;
import java.util.Map;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Collections;

class EventHandler<A> {
  public List<Closure<A>> closures = new CopyOnWriteArrayList<>();

  public void addClosure (Closure<A> closure) {
    this.closures.add(closure);
  }

  public boolean removeClosure (Closure<A> closure) {
    return this.closures.remove(closure);
  }

  public void emit (A... data) {
    for (Closure<A> closure : this.closures) {
      closure.call(data);
    }
  }

  public void clear () {
    this.closures.clear();
  }

}

public class EventBus <T> {

  private Map<String, EventHandler<T>> events = new ConcurrentHashMap();

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
    this.logThread("on", eventName); 
  }

   public boolean off (String eventName) {
    EventHandler<T> removed = this.events.remove(eventName);
    boolean wasRemoved = removed != null;
    this.logThread("off 1", eventName);
    return wasRemoved;
  }
  
  public boolean off (String eventName, Closure closure) {
    EventHandler<T> event = this.events.get(eventName);
    boolean wasRemoved = false;
    if (event != null) {
      wasRemoved = event.removeClosure(closure);
    }
    this.logThread("off 2", eventName);
    return wasRemoved;
  }

  public void emit (String eventName, T... payload) {
    EventHandler<T> event = this.events.get(eventName);
    if (event != null) {
      event.emit(payload);
    }
    this.logThread("emit", eventName);
  }

  private void logThread (String methodName, String eventName) {
    EventHandler<T> event = this.events.get(eventName);
    String msg = methodName + " thread " + Thread.currentThread().getId() + " size " + this.events.keySet().size() + " " + eventName;
    if (event != null) {
      msg += " listeners " + event.closures.size();
    }
    Logger.trace(msg);
  }

  private void logThread (String methodName) {
    Logger.trace(methodName + " thread " + Thread.currentThread().getId() + " size " + this.events.keySet().size());
  }

  public void clear () {
    this.events.clear();
    this.logThread("clear");
  }

}