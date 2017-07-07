package models;

public interface PlayerActionsInterface {
  void choose(String uid, String params);

  void remove(String pid);

  void turnAIOff();

  void turnAIOn();
}
