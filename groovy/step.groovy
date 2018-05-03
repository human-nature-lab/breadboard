import java.beans.PropertyChangeListener
import com.fasterxml.jackson.annotation.JsonIgnore

class Step {
  @JsonIgnore
  PlayerActions playerActions
  String name
  @JsonIgnore
  boolean ignoreUserAction = false

  Closure run = { println "run" }
  Closure done = {
    println "done"
    //assume this is the last step since no chain step
    finish()
  }
  @JsonIgnore
  def gameListener
  def params = [:]
  @JsonIgnore
  def eventTracker
  private def emptyListener

  def propertyMissing(String name, value) {
    params[name] = value
  }

  def propertyMissing(String name) {
    return params[name]
  }

  def start(String player = null) {

    // Record the start time of each step.
    if (name != null) {
      eventTracker.track(name + "Start", ["startStep": name])
    }

    run(player)

    boolean hasUserActions = !playerActions.actions.isEmpty()

    if (hasUserActions) {
      //println("playerActions.actions.size() = " + playerActions.actions.size())

      if (!ignoreUserAction) {
        //if has user actions, the done should be triggered after all the actions are completed
        emptyListener = { event ->
          if (event instanceof ObservableMap.PropertyRemovedEvent) {
            //println("playerActions.actions.size() = " + playerActions.actions.size())
            if (playerActions.actions.isEmpty()) {
              playerActions.removeActionPropertyChangeListener(emptyListener)
              done(player)
            }
          }
        } as PropertyChangeListener

        playerActions.addActionPropertyChangeListener(emptyListener)
      } else {
        done(player)
      }
    } else {
      done(player)
    }
  }

  def finish() {
    eventTracker.track("Finished", [])
    gameListener.finish()
  }
}

class StepFactory {

  PlayerActions playerActions
  def gameListener
  def eventTracker

  Step createStep(String name) {
    return new Step(name: name, playerActions: playerActions, gameListener: gameListener, eventTracker: eventTracker)
  }

  Step createNoUserActionStep(String name) {
    return new Step(name: name, playerActions: playerActions, gameListener: gameListener, eventTracker: eventTracker, ignoreUserAction: true)
  }

}

stepFactory = new StepFactory(playerActions: a, gameListener: gameListener, eventTracker: eventTracker)
