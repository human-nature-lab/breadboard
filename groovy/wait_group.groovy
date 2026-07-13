import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock
import groovy.transform.Synchronized

// This is here to make the compiler happy
def doNothing = {}

// Threadsafe way of waiting for N events to occur
public class WaitGroup {
  private activeCount = 0
  private ended = false
  private onDone = []
  private Lock lock = new ReentrantLock(true)

  // Increment the activeCount by n
  public add(int n) {
    this.lock.lock()
    try {
      this.activeCount += n
    } finally {
      this.lock.unlock()
    }
  }

  // Reduces the activeCount by 1 and fires the onDone callbacks if we've reached 0
  public done () {
    this.lock.lock()
    try {
      this.activeCount--
      if (this.activeCount == 0) {
        this.end()
      }
    } finally {
      this.lock.unlock()
    }
  }

  @Synchronized
  public end () {
    if (this.ended) return
    this.ended = true
    for(def i = 0; i < this.onDone.size(); i++) {
      def c = this.onDone[i]
      c()
    }
  }

  // Add one or more closures to call once this group has completed
  public onDone (Closure cb) {
    this.onDone += cb
    return this
  }

}

public class IdleWaitGroup {
  public String name = "idle-wait-group"
  public String dropTimerText = "You will be dropped in"
  public int idleTimeoutSeconds = 15
  public int dropTimeoutSeconds = 15
  private lock = new ReentrantLock(true)
  private List pendingPlayers = []
  private BBTimer idleTimer = null
  private SharedTimer dropTimer = null
  private Closure playersDroppedCb = null
  private Closure onDoneCb = null

  public IdleWaitGroup(int idleTimeoutSeconds, int dropTimeoutSeconds) {
    this.idleTimeoutSeconds = idleTimeoutSeconds
    this.dropTimeoutSeconds = dropTimeoutSeconds
  }

  public IdleWaitGroup onDone(Closure cb) {
    this.onDoneCb = cb
    return this
  }

  public IdleWaitGroup onPlayersDropped(Closure cb) {
    this.playersDroppedCb = cb
    return this
  }

  public addAll(List players) {
    for (def player in players) {
      this.add(player)
    }
  }

  public add(Object player) {
    this.lock.lock()
    try {
      if (this.idleTimer == null) {
        this.idleTimer = new BBTimer()
        this.idleTimer.runAfter(this.idleTimeoutSeconds * 1000, { this.idleTimerFired() })
      }
      this.pendingPlayers.add(player)
    } finally {
      this.lock.unlock()
    }
  }

  public doneAll(List players) {
    for (def player in players) {
      this.done(player)
    }
  }

  public done(Object player) {
    this.lock.lock()
    try {
      this.pendingPlayers.remove(player)
      if (this.dropTimer != null) {
        this.dropTimer.removePlayer(player)
      }
      this.checkDone()
      def remainingCount = this.pendingPlayers.size()
      println "IdleWaitGroup.done: $remainingCount players remaining"
    } catch(Exception e) {
      println "IdleWaitGroup.done: error: $e"
      e.printStackTrace()
    } finally {
      this.lock.unlock()
    }
  }

  private checkDone() {
    if (this.pendingPlayers.size() == 0) {
      if (this.idleTimer != null) {
        this.idleTimer.cancel()
        this.idleTimer = null
      }
      if (this.dropTimer != null) {
        this.dropTimer.cancel()
        this.dropTimer = null
      }
      this.onDoneCb()
      return
    }
  }

  private idleTimerFired () {
    this.lock.lock()
    try {
      this.idleTimer.cancel()
      this.idleTimer = null
      this.dropTimer = new SharedTimer([
        time: this.dropTimeoutSeconds,
        name: this.name,
        timerText: this.dropTimerText,
        players: this.pendingPlayers,
        result: {
          this.dropTimerFired()
        }
      ])
    } finally {
      this.lock.unlock()
    }
  }

  private dropTimerFired () {
    this.lock.lock()
    try {
      this.dropTimer.cancel()
      this.dropTimer = null
      if (this.playersDroppedCb != null && this.pendingPlayers.size() > 0) {
        this.playersDroppedCb(this.pendingPlayers)
      }
      this.pendingPlayers.clear()
      this.checkDone()
    } finally {
      this.lock.unlock()
    }
  }
}
