package models;

import java.util.Map;

public interface PlayerActionsInterface
{
	void choose(String uid, String params);
	void remove(String pid);

    void turnAIOff();
    void turnAIOn();
}
