import com.tinkerpop.blueprints.Vertex
import groovy.transform.Synchronized

public class Tutorial {
  def isStarted = false
  def isEnded = false
  def useStepper = true
  def readyUp = true
  def readySeconds = 30
  def readyButtonText = "Ready"
  def readyTextKey = "Ready"
  def removeActions = true
  def removeEdges = true
  def removeEvents = true
  def isReadyUpComplete = false
  def players = []
  def steps = []
  def onReadyCbs = []
  def stateKey = "tutorial"
  def readyUpTimer
  def graph, actions, content

  Tutorial (graph, actions, content) {
    this.graph = graph
    this.actions = actions
    this.content = content
  }

  /**
   * Start the tutorial for all players. Players added aftaer the tutorial has started
   * will automatically start the tutorial.
   */
  public start () {
    this.isStarted = true
    // Add the tutorial properties to all players and start the sequence
    for (player in this.players) {
      this.startPlayer(player)
    }
  }

  /**
   * End the tutorial for all players. Removes all tutorial properties and perform
   * other cleanup operations.
   */
  public end () {
    this.isEnded = true

    // Remove the tutorial props from each player and do any additional cleanup
    this.players.each{player ->
      this.endPlayer(player)
    }

    if (this.readyUp) {
      this.startReadyUp()
    } else {
      this.cleanup()
    }
    
  }

  /**
   * Begin the ready up sequence. This is started automatically when the `tutorial.end()` method is called
   * if `tutorial.readyUp == true`
   */
  public startReadyUp () {
    this.readyUpTimer = new Timer().runAfter(this.readySeconds * 1000) {
      this.completeReadyUp()
    }
    this.graph.addTimer([
      name: "ready-up",
      time: this.readySeconds,
      timerText: "Game begins in "
    ])
    this.players.each{player -> 
      player.ready = false
      this.actions.add(player, [
        name: this.readyButtonText,
        result: {
          println "player ready"
          try {
            player.ready = true
            this.checkPlayersReady()  
          } catch (Exception e) {
            println "ready result exception"
            e.printStackTrace()
          }
        }
      ])
    }
  }

  private cleanup () {
    // TODO: Cleanup
    // Cleanup unused memory
    if (this.readyUpTimer) {
      this.readyUpTimer.cancel()
    }
    this.players = null
    this.steps = null
    this.onReadyCbs = null
  }

  /**
   * Check if all players have pressed the ready button
   */
  @Synchronized
  private checkPlayersReady () {
    println "checking players ready"
    try {
      // Prevent ready up completion from being called twice from various race conditions
      if (this.isReadyUpComplete) return
      for (def i = 0; i < this.players.size(); i++) {
        if (!this.players[i].ready) {
          return false
        }
      }
      println "all players ready"
      this.completeReadyUp()
    } catch (Exception e) {
      println "allPlayersReady exception"
      e.printStackTrace()
    }
  }

  /**
   * Perform post ready-up cleanup operations. Removes players who haven't
   * hit the ready button in the alotted time.
   */
  private completeReadyUp () {
    this.isReadyUpComplete = true
    // TODO: Deactivate players that aren't ready
    this.players.each{ player -> 
      if (player.timers) {
        player.timers.remove("ready-up")
      }
    }
    println "complete ready up"
    this.onReadyCbs.each{ cb -> cb()}
    this.cleanup()
  }

  /**
   * Perform tutorial ending functions
   */
  private endPlayer (Vertex player) {
    println "ending player tutorial for " + player.id
    if (this.removeEdges) {
      // TODO: Remove the edges for all players
    }
    if (this.removeActions) {
      // TODO: Remove the actions for all players
    }
    // Remove events for all players
    if (this.removeEvents) {
      player.clear()
    }

    if (this.readyUp) {
      player.text = this.content.get(this.readyTextKey)
    } else {
      player.text = "Please wait..."
    }

    // Cleanup tutorial events
    player.off("tutorial-next")
    player.off("tutorial-prev")
    player.private.remove(this.stateKey)
    player.timerUpdatedAt = 1
  }

  /**
   * Add a custom tutorial step.
   * @param {Map} step
   * @param {String} [step.title]
   * @param {Closure} [step.onExitNext]
   * @param {Closure} [step.onExitPrev]
   * @param {Closure} [step.onEnterNext]
   * @param {Closure} [step.onEnterPrev]
   * @param {Closure} [step.onEnter]
   * @param {Closure} [step.onExit]
   */
  public addStep (Map step) {
    if (this.isStarted) {
      throw new Exception("Can't add a tutorial step after the tutorial has started")
    }
    if (!step.title) {
      step.title = "Tutorial " + this.steps.size()
    }
    this.steps << step
  }

  /**
   * Add a content tutorial step by passing in the content key.
   * @param {Map} opts - Same options as addStep + the following
   * @param {String} opts.contentKey - The content key to be used
   * @param {List} [opts.fills] - Any fills to be used by the content engine
   * @param {String} [opts.prevText]
   * @param {String} [opts.nextText]
   */
  public addContent (Map opts) {
    def newOpts = opts + [
      title: opts.title ?: opts.contentKey,
      onEnter: {player, state ->
        if ("fills" in opts) {
          player.text = this.content.get(opts.contentKey, *opts.fills)
        } else {
          player.text = this.content.get(opts.contentKey)
        }
        if ("onEnter" in opts) {
          opts.onEnter(player, state)
        }
      }
    ]
    this.addStep(newOpts)
  }

  /**
   * Handle next result closure in the tutorial
   */
  public onNextClosure (Vertex player) {
    try {
      def state = this.getTutorialState(player)
      def nextIndex = state.index + 1
      def currentStep = this.steps[state.index]
      if ("onExitNext" in currentStep) {
        step.onExitNext(player, state)
      }
      this.exitStep(player, currentStep)
      if (nextIndex < this.steps.size()) {
        def nextStep = this.steps[nextIndex]
        state.index = nextIndex
        this.enterStep(player, nextStep)
        if ("onEnterNext" in nextStep) {
          nextStep.onEnterNext(player, state)
        }
      } else {
        this.endPlayer(player)
      }
    } catch (Exception e) {
      println "onNextClosure exception"
      e.printStackTrace()
    }
  }

  /**
   * Handle previous result closure in tutorial
   */
  public onPrevClosure (Vertex player) {
    def state = this.getTutorialState(player)
    def prevIndex = state.index - 1
    def currentStep = this.steps[state.index]
    if ("onExitPrev" in currentStep) {
      currentStep.onPrev(player, state)
    }
    this.exitStep(player, currentStep)
    if (prevIndex >= 0) {
      def nextStep = this.steps[prevIndex]
      state.index = prevIndex
      this.enterStep(player, nextStep)
      if ("onEnterPrev" in nextStep) {
        nextStep.onEnterPrev(player, state)
      }
    }
  }

  /**
   * Perform all step exit operations
   */
  private exitStep (Vertex player, Map step) {
    def state = this.getTutorialState(player)
    println "exit step " + step.toString()
    if ("onExit" in step) {
      step.onExit(player, state)
    }
    // TODO: Clean up the graph, actions, etc. depending on the options for this step
  }

  /**
   * Handle shared operations when entering a step
   */
  private enterStep (Vertex player, Map step) {
    // TODO: Run any enter operations for this step depending on the step options
    if ("onEnter" in step) {
      step.onEnter(player, this.getTutorialState(player))
    }
  }

  /**
   * Assign this tutorial to a player vertex.
   * @param {Vertex} player
   */
  public addPlayer (Vertex player) {
    if (this.isEnded) {
      return
    }
    this.players << player
    if (this.isStarted) {
      this.startPlayer(player)
    }
  }

  /**
   * Add the tutorial properties to the player and apply the tutorial step.
   * @param {Vertex} player
   */
  public startPlayer (Vertex player) {
    def startIndex = 0
    def tutorialState = [
      index: startIndex,
      maxSteps: this.steps.size(),
      titles: this.steps.collect{ it.title },
      useStepper: this.useStepper
    ]
    player.private[this.stateKey] = tutorialState
    if (startIndex < this.steps.size()) {
      def step = this.steps[startIndex]
      if ("onEnterNext" in step) {
        step.onEnterNext(player, tutorialState)
      }
      this.enterStep(player, step)
    }
    // Use the stepper component with navigation events to step through the tutorial
    if (this.useStepper) {
      println "adding player w/ stepper"
      // player.off("tutorial-next")
      // player.off("tutorial-prev")
      player.on("tutorial-next", {v, params ->
        println "tutorial next"
        this.onNextClosure(player)
      })
      player.on("tutorial-prev", {v, params ->
        println "tutorial prev"
        this.onPrevClosure(player)
      })
    }
  }

  /**
   * Return the tutorial state for this player
   */
  private getTutorialState (Vertex player) {
    return player.private[this.stateKey]
  }

  /**
   * Assign this tutorial to several players.
   * @param {Vertex[]} players
   */
  public addPlayers (Vertex[] players) {
    for (player in players) {
      this.addPlayer(player)
    }
  }

  /**
   * Add a closure to be called once the tutorial has ended.
   * @param {Closure} cb - A closure without any arguments
   */
  public onReady (Closure cb) {
    this.onReadyCbs << cb
  }
}
