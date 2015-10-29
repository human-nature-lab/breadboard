package models;

//basically just copy from groovy.util.ObserableMap and make it as interface
interface UserDataInterface {

    java.util.Map getContent();

    void clear();

    boolean containsKey(java.lang.Object key);

    boolean containsValue(java.lang.Object value);

    java.util.Set entrySet();

    boolean equals(java.lang.Object o);

    java.lang.Object get(java.lang.Object key);

    int hashCode();

    boolean isEmpty();

    java.util.Set keySet();

    java.lang.Object put(java.lang.Object key, java.lang.Object value);

    void putAll(java.util.Map map);

    java.lang.Object remove(java.lang.Object key);

    int size();

    int getSize();

    java.util.Collection values();

    void addPropertyChangeListener(java.beans.PropertyChangeListener listener);

    void addPropertyChangeListener(java.lang.String propertyName, java.beans.PropertyChangeListener listener);

    java.beans.PropertyChangeListener[] getPropertyChangeListeners();

    java.beans.PropertyChangeListener[] getPropertyChangeListeners(java.lang.String propertyName);

    void removePropertyChangeListener(java.beans.PropertyChangeListener listener);

    void removePropertyChangeListener(java.lang.String propertyName, java.beans.PropertyChangeListener listener);

    boolean hasListeners(java.lang.String propertyName);

}

