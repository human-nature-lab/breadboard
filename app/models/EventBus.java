package models;

import play.Logger;
import groovy.lang.Closure;
import java.lang.*;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collections;

class EventHandler<A> {
  public List<Closure<A>> closures = Collections.synchronizedList(new ArrayList());

  public void addClosure (Closure<A> closure) {
    this.closures.add(closure);
  }

  public void removeClosure (Closure<A> closure) {
    this.closures.remove(closure);
  }

  public void emit (A... data) {
    synchronized (this.closures) {
      for (Closure<A> closure : this.closures) {
        closure.call(data);
      }
    }
  }

  public void clear () {
    this.closures.clear();
  }

}

public class EventBus <T> {

  private Map<String, EventHandler<T>> events = Collections.synchronizedMap(new HashMap());

  public EventHandler<T> register (String eventName) {
    synchronized (this.events) {
      EventHandler<T> handler = new EventHandler();
      this.events.put(eventName, handler);
      return handler;
    }
  }

  public void unregister (String eventName) {
    synchronized (this.events) {
      this.events.remove(eventName);
    }
  }

  public void on (String eventName, Closure closure) {
    synchronized (this.events) {
      EventHandler<T> event = this.events.get(eventName);
      // Automatically register the event if it hasn't been registered yet
      if (event == null) {
        event = register(eventName);
      }
      event.addClosure(closure);
      this.logThread("on"); 
    }
  }

  public void off (String eventName) {
    synchronized (this.events) {
      this.events.remove(eventName);
      this.logThread("off 1");
    }
  }
  
  public void off (String eventName, Closure closure) {
    synchronized (this.events) {
      EventHandler<T> event = this.events.get(eventName);
      if (event != null) {
        event.removeClosure(closure);
      }
      this.logThread("off 2");
    }
  }

  public void emit (String eventName, T... payload) {
    synchronized (this.events) {
      EventHandler<T> event = this.events.get(eventName);
      if (event != null) {
        event.emit(payload);
      }
      this.logThread("emit");
    }
  }

  private void logThread (String name) {
    // Logger.debug(name + " thread " + Thread.currentThread().getId() + " size " + this.events.keySet().size());
  }

  public void clear () {
    synchronized (this.events) {
      this.events.clear();
      this.logThread("clear");
    }
  }

}