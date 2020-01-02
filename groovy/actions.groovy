import com.tinkerpop.blueprints.Vertex
import groovy.json.JsonSlurper
import java.beans.PropertyChangeListener
import java.util.concurrent.ConcurrentHashMap
import com.fasterxml.jackson.annotation.JsonIgnore

class PlayerActions {
  // To handle player AI
  def ai = new PlayerAI(playerActions: this)

  def eventTracker
  def idleTime
  def warnTime
  def dropTime
  def dropPlayers = false

  Closure dropPlayerClosure

  //all actions across all players
  def actions = new ObservableMap(new ConcurrentHashMap())
  //def actions = [:] as ObservableMap

  //map the player to the player's action queue in a round
  def playerToActionQueue = [:]

  def setIdleTime(idleTime) {
    this.idleTime = idleTime
  }

  def setWarnTime(warnTime) {
    this.warnTime = warnTime
  }

  def setDropTime(dropTime) {
    this.dropTime = dropTime
  }

  def setIdleTimer(playerAction) {
    if ((idleTime != null || (warnTime != null && dropTime != null)) && (dropPlayers != null && dropPlayers == true)) {
      def idleTimer1 = [:]
      idleTimer1.player = playerAction.player
      idleTimer1.timer = new Timer()
      idleTimer1.fired = false

      def idleTimer2 = [:]
      idleTimer2.player = playerAction.player
      idleTimer2.timer = new Timer()

      def time1 = (warnTime != null) ? warnTime : idleTime
      def time2 = (dropTime != null) ? dropTime : idleTime
      idleTimer1.timer.runAfter(time1 * 1000) {

        idleTimer1.fired = true

        def timerName = "dropTimer"

        def dropTimer = [
            name          : "dropTimer",
            type          : "time",
            elapsed       : 0,
            duration      : time2 * 1000,
            timerText     : "You will be dropped in: ",
            direction     : "down",
            currencyAmount: "0",
            appearance    : "warning",
            order         : -1
        ]

        if (playerAction.player.timers == null) {
          playerAction.player.timers = [:]
        }

        playerAction.player.timers[timerName] = dropTimer
        // TODO: Find out why modifying the timers map doesn't trigger a client update without the next line
        playerAction.player.lastupdated = System.currentTimeMillis()

        idleTimer2.timer.runAfter(time2 * 1000) {
          idleTimer2.timer.cancel()

          if (dropPlayerClosure) {
            dropPlayerClosure(playerAction.player)
          }
          playerAction.player.timers.remove("dropTimer")
        }

        def timerUpdateRate = 1000
        idleTimer2.timer.scheduleAtFixedRate({
          playerAction.player.timers["dropTimer"].elapsed += timerUpdateRate
        } as GroovyTimerTask, timerUpdateRate, timerUpdateRate)

      }

      playerAction.idleTimer1 = idleTimer1
      playerAction.idleTimer2 = idleTimer2
    }
  }

  def setDropPlayers(dropPlayers) {
    this.dropPlayers = dropPlayers
    if (dropPlayers) {
      // Now dropping players, set timers for any players with active choices
      actions.each {
        if (it.value.player) {
          if (it.value.player.getProperty("choices")) {
            setIdleTimer(it.value)
          }
        }
      }
    } // TODO: if setting dropPlayers to false we should disable the timers for all players.
  }

  def setDropPlayerClosure(Closure dropPlayerClosure) {
    this.dropPlayerClosure = dropPlayerClosure
  }

  def addEvent(name, data) {
    eventTracker.track(name, data)
  }

  def empty() {
    actions.each {
      if (it.value.player)
        it.value.player.removeProperty("choices")
    }

    def propertyChangeListeners = actions.propertyChangeListeners?.clone()
    propertyChangeListeners.each {
      actions.removePropertyChangeListener(it)
    }
    actions.clear()
    playerToActionQueue.clear()

    ai.timer.cancel()
    ai = new PlayerAI(playerActions: this)

  }

  def remove(Vertex p) {
    remove(p.id.toString())
  }

  def remove(String pid) {
    def removedChoices = false
    def pActions = getActions(pid)
    def pActionsKeys = pActions.keySet()
    pActionsKeys.each {
      def action = actions.get(it)
      if (! removedChoices && action.hasProperty("player")) {
        playerToActionQueue[action.player] = [] as Queue
        action.player.choices = []
        removedChoices = true
      }

      actions.remove(it)
    }
  }

  def add(Vertex player, HashMap... choices) {
    add(player, null, choices)
    // TODO:  Should this be 'add(player, {}, choices)'?
  }

  def add(Vertex player, Closure init, HashMap... choices) {
    //make sure the queue is ready for each player
    if (playerToActionQueue[player] == null) {
      playerToActionQueue[player] = [] as Queue
    }

    //action is queue up for the individual player
    playerToActionQueue[player] << {

      def choiceArray = []
      int i = 1

      if (init) {
        init()
      }

      def playerAction = new PlayerAction(player: player, eventTracker: eventTracker)

      setIdleTimer(playerAction)

      for (HashMap c : choices) {
        // If result is not provided, default to empty closure
        def result = {}
        if (c.result) {
          result = c.result
        } else if (c.results) {
          // Let's accept both result and results
          result = c.results
        }

        def choiceMap = [:]

        // Name
        choiceMap.name = (c.name) ? c.name : "Option " + i

        // Class
        if (c.class)
          choiceMap.class = c.class

        // Custom question type
        if (c.custom)
          choiceMap.custom = c.custom

        // UUID
        String uuid = UUID.randomUUID().toString()
        playerAction.putResult(uuid, result)
        playerAction.putName(uuid, choiceMap.name)
        if (c.event) {
          playerAction.putEvent(uuid, c.event);
        }

        choiceMap.uid = uuid

        //all the uuid point to the same playerAction in a round
        actions[uuid] = playerAction

        // Add the choiceMap to the choiceArray
        choiceArray.add(choiceMap)

        i++
      }
      String jsonString = new groovy.json.JsonBuilder(choiceArray).toString()

      player.setProperty("choices", choiceArray)
      return jsonString
    }

    if (playerToActionQueue[player].size() == 1) {
      def playerFirstAction = playerToActionQueue[player][0]

      playerFirstAction()
    }

    // When we add new actions, start the AI
    if (player.getProperty("ai") == 1) {
      ai.choose(player);
    }
  }

  def choose(String uid) {
    choose(uid, "")
  }

  def choose(String uid, String params) {
    PlayerAction action = actions[uid]

    if (action != null) {
      // Perform the action
      def parsedParams = [:]
      if (params != null && params != "") {
        def jsonSlurper = new JsonSlurper()
        parsedParams = jsonSlurper.parseText(params) as Map
      }
      choose(uid, parsedParams)
    }
  }

  def choose(String uid, Map parsedParams) {
    PlayerAction action = actions[uid]

    //if (params != null) println("params: " + params)

    if (action != null) {
      def choiceArray = action.player.removeProperty("choices")

      action.execute(uid, parsedParams)

      //remove the head action now
      playerToActionQueue[action.player]?.poll()

      def nextAction = playerToActionQueue[action.player].peek()
      if (nextAction) {
        nextAction()

        // If there is more than one action, we need to start the AI on the next action
        if (action.player.getProperty("ai") == 1)
          ai.choose(action.player);
      }

      // Remove this round of action
      choiceArray.each { actions.remove(it.uid) }
    }
  }


  def size() {
    return actions.size()
  }

  def getActions(String pid) {
    return actions.findAll { a -> a.value.player.id == pid }
  }

  def addActionPropertyChangeListener(PropertyChangeListener listener) {
    actions.addPropertyChangeListener(listener)
  }

  def removeActionPropertyChangeListener(PropertyChangeListener listener) {
    actions.removePropertyChangeListener(listener)
  }

  def turnAIOff() {
    ai.off()
  }

  def turnAIOn() {
    ai.on()
  }

  /**
   * Each round the player might need to make a series of choices.
   * The PlayerAction hold one choice which associates with multiple actions.
   */
  class PlayerAction {

    Vertex player
    def eventTracker
    def idleTimer1
    def idleTimer2

    //player will choose only one of the choices... which means only one result closure should be executed
    def uidToAction = [:]
    def uidToName = [:]
    def uidToEvent = [:]

    def putResult(String uid, Closure result) {
      uidToAction[uid] = result
    }

    def putName(String uid, String name) {
      uidToName[uid] = name
    }

    def putEvent(String uid, def event) {
      uidToEvent[uid] = event
    }

    def execute(String uid, Object params) {
      uidToAction[uid](params)
      if (eventTracker) {
        def event = uidToEvent[uid]
        if (event) {
          eventTracker.track(event.name, event.data)
        }
      }

      /*
      if (idleTimer1 != null && idleTimer1.fired) {
          if (idleTimer1.player.tempTimer) {
              idleTimer1.player.timer = idleTimer1.player.tempTimer;
              idleTimer1.player.removeProperty("tempTimer");
          } else {
              idleTimer1.player.timer = ""
          }
      }
      */

      if (idleTimer1) {
        if (idleTimer1.timer) {
          idleTimer1.timer.cancel()
        }
        if (idleTimer1.player) {
          if (idleTimer1.player.timers) {
            idleTimer1.player.timers.remove("dropTimer")
            // TODO: Find out why modifying the timers map doesn't trigger a client update without the next line
            idleTimer1.player.lastupdated = System.currentTimeMillis()
          }
        }
      }

      if (idleTimer2) {
        idleTimer2.timer.cancel()
      }
    }

    def getNameByUid(String uid) {
      return uidToName[uid]
    }
  }
}

class PlayerAI {
  @JsonIgnore
  PlayerActions playerActions

  @JsonIgnore
  def r = new Random()

  @JsonIgnore
  def defaultBehavior = { player ->
    def randomDelay = 1000 + r.nextInt(3000)
    try {
      def task = new Timer().runAfter(randomDelay) {
        if (player.getProperty("choices")) {
          def choices = player.getProperty("choices")
          def choice = choices[r.nextInt(choices.size())]
          playerActions.choose(choice.uid, null)
        }
      }
    } catch (IllegalStateException e) {
      // This is most likely a side effect of a.remove()
      println "Caught side effect of a.remove(): " + e
    }
  }

  // A timer so we can stagger AI actions
  def timer = new Timer()

  // Is the AI behavior globally turned on?
  // Changed to default to true
  def isOn = true

  // We have the ability to assign custom AI behavior for each ai player
  // Map of Vertex player : Closure behavior
  def behaviors = [:]

  def off() {
    this.isOn = false
  }

  def on() {
    this.isOn = true
  }

  def add(Vertex player, Closure behavior = defaultBehavior) {
    def newAi = false
    if (player.getProperty("ai") != 1) {
      newAi = true
    }

    player.setProperty("ai", 1)
    behaviors[player] = behavior

    if (newAi) {
      choose(player)
    }
    /*
    playerActionListener = { event ->
        if (event instanceof ObservableMap.PropertyUpdatedEvent) {
        println ("event.getKey() " + event.getKey())
            if (event.getKey() == player)
                choose(player)
        }
    } as PropertyChangeListener
    */
  }

  def remove(Vertex player) {
    // Remove the "ai" property
    player.removeProperty("ai")
    // If a custom behavior was defined, remove it
    if (behaviors.containsKey(player))
      behaviors.remove(player)
  }

  def choose(Vertex player) {
    if (!this.isOn)
      return

    if (behaviors.containsKey(player)) {
      behaviors[player](player)
    }
  }
}


a = new PlayerActions(eventTracker: eventTracker)

def makeChoice(String uid) {
  a.choose(uid)
}
