package models;

import com.tinkerpop.blueprints.util.wrappers.event.listener.GraphChangedListener;

public interface BreadboardGraphInterface 
{
	void addListener(GraphChangedListener listener);
	void addPlayer(String id);
	void removePlayer(String id);
}
