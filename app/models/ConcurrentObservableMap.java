package models;

import groovy.util.ObservableMap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An {@link ObservableMap} backed by a {@link ConcurrentHashMap} instead of the default
 * {@code LinkedHashMap}.
 *
 * <p>WHY: vertex "private" vars (and edge inProps/outProps) get mutated from experiment code that can
 * run off the main ScriptBoard actor thread - e.g. timer callbacks run on their own thread pool (see
 * groovy/timer.groovy). Every mutation fires a change event whose listener chain
 * ({@code Admin.vertexPropertyChanged}) iterates that same map to re-serialize it to the admin
 * socket. With the default {@code LinkedHashMap}, mutating on one thread while the serialization loop
 * iterated on another threw {@link java.util.ConcurrentModificationException}. A ConcurrentHashMap's
 * iterator is weakly consistent, so the loop never throws. NOTE: this makes THIS map safe - it does
 * not make the program thread-safe; every shared structure would need the same treatment (or all
 * mutation confined to one thread).
 *
 * <p>This lives in Java rather than in graph.groovy on purpose: Groovy 1.8.6 subclassing a
 * Map-implementing class on Java 8 generates an illegal {@code super$1$compute} bridge to the
 * {@code Map.compute} default method, which fails bytecode verification (VerifyError). javac emits
 * correct bytecode.
 *
 * <p>ConcurrentHashMap forbids null keys/values, so {@code v.private.foo = null} (which used to just
 * store null) would otherwise NPE at the setter. We keep the old read semantics - an absent key reads
 * back as null in Groovy - by treating a null write as a remove instead of crashing experiment code.
 */
public class ConcurrentObservableMap extends ObservableMap {

  public ConcurrentObservableMap() {
    super(new ConcurrentHashMap<Object, Object>());
  }

  @Override
  public Object put(Object key, Object value) {
    if (value == null) {
      return remove(key);
    }
    return super.put(key, value);
  }

  // ObservableMap.putAll writes straight to the backing map, bypassing the put() above - route each
  // entry through put() so a null value in the batch is handled there rather than hitting the CHM.
  @Override
  public void putAll(Map m) {
    if (m != null) {
      for (Object o : m.entrySet()) {
        Map.Entry e = (Map.Entry) o;
        put(e.getKey(), e.getValue());
      }
    }
  }
}
