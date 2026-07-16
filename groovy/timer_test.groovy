// Tests for timer.groovy's BBScheduledTimer -- the Akka-scheduler-backed timer that SharedTimer uses
// instead of one native java.util.Timer thread per experiment timer. Registered through the `test`
// DSL bound by ScriptTestHarness; ScriptLoader skips the `_test.groovy` suffix in production. Run via:
//   sbt -java-home <jdk8> "testOnly GroovyScriptTests"
//
//   test("name") { ... }                            -> synchronous
//   test.async("name", timeoutMs) { done -> ... }   -> async; signal completion with done()
//
// These exercise BBScheduledTimer directly (the waiting_room / groups / recruitment suites only cover
// it incidentally, through SharedTimer). The focus is the resource-leak contract this class exists to
// provide: a cancelled timer -- whether cancelled on its own or swept up by timers.cancel() on a
// script reload -- must never fire again and must leave the registry clean. In the harness the timer
// schedules on the injected standalone ActorSystem (see ScriptTestHarness.injectTimerScheduler); in
// production the same code path runs on the app's Akka.system() scheduler.
//
// Groovy 1.8.6 notes: counters are java.util.concurrent.atomic types (callbacks run on the shared
// callback pool, off the test thread); a fixed-rate task is built with `new GroovyTimerTask(closure:)`
// because Groovy 1.8.6 won't coerce a closure to a Java-8 functional interface; and waits use a plain
// BBTimer (timers.newTimer()) -- the java.util.Timer path, which needs no scheduler injection.

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean

// --- registry bookkeeping: no scheduling, no waiting --------------------------------------------

test("a BBScheduledTimer registers itself on creation and unregisters on cancel") {
  def before = timers.scheduledTimers.size()
  def t = new BBScheduledTimer()
  assert timers.scheduledTimers.size() == before + 1 : "creating a BBScheduledTimer should register it"
  assert timers.scheduledTimers.contains(t)

  t.cancel()
  assert !timers.scheduledTimers.contains(t) : "cancel() must unregister the timer"
  assert timers.scheduledTimers.size() == before : "registry should return to its baseline after cancel()"
}

// --- scheduling actually fires (on the injected Akka scheduler) ---------------------------------

test.async("runAfter fires its callback exactly once after the delay", 3000) { done ->
  def fired = new AtomicInteger(0)
  def t = new BBScheduledTimer()
  t.runAfter(60) { fired.incrementAndGet() }

  // Well past the 60ms delay: it must have fired, and a one-shot must not repeat.
  timers.newTimer().runAfter(500) {
    t.cancel()
    done {
      assert fired.get() == 1 : "runAfter should fire exactly once, got ${fired.get()}"
    }
  }
}

test.async("scheduleAtFixedRate ticks repeatedly, and cancel() stops any further ticks", 4000) { done ->
  def ticks = new AtomicInteger(0)
  def t = new BBScheduledTimer()
  t.scheduleAtFixedRate(new GroovyTimerTask(closure: { ticks.incrementAndGet() }), 0, 80)

  timers.newTimer().runAfter(400) {
    def duringRun = ticks.get()
    t.cancel()
    def atCancel = ticks.get()
    // Give the scheduler ample time to fire again if cancel didn't actually stop it.
    timers.newTimer().runAfter(400) {
      done {
        assert duringRun >= 2 : "a fixed-rate timer should tick repeatedly; got only ${duringRun} in 400ms"
        assert ticks.get() == atCancel :
          "no tick may fire after cancel(), but the count grew from ${atCancel} to ${ticks.get()}"
      }
    }
  }
}

// --- the leak contract: a cancelled timer never fires -------------------------------------------

test.async("cancel() prevents a still-pending runAfter callback from ever firing", 3000) { done ->
  def fired = new AtomicInteger(0)
  def t = new BBScheduledTimer()
  t.runAfter(400) { fired.incrementAndGet() }   // scheduled well into the future...
  t.cancel()                                    // ...then cancelled before it can fire

  timers.newTimer().runAfter(700) {             // past the original 400ms delay
    done {
      assert fired.get() == 0 : "a cancelled runAfter must never fire, got ${fired.get()}"
    }
  }
}

test.async("timers.cancel() stops every scheduled timer and clears the registry (reload contract)", 4000) { done ->
  def ticks = new AtomicInteger(0)
  def t = new BBScheduledTimer()
  t.scheduleAtFixedRate(new GroovyTimerTask(closure: { ticks.incrementAndGet() }), 0, 80)

  timers.newTimer().runAfter(300) {
    timers.cancel()                             // what ScriptBoard.resetEngine does on a reload
    def atCancel = ticks.get()
    assert timers.scheduledTimers.isEmpty() : "timers.cancel() must empty the scheduled-timer registry"
    // A fresh BBTimer (registered after the cancel) is fine as the waiter; it lands in `timers`, not
    // `scheduledTimers`, so it doesn't disturb the assertion above.
    timers.newTimer().runAfter(400) {
      done {
        assert atCancel > 0 : "sanity: the timer should have ticked before the reload"
        assert ticks.get() == atCancel :
          "timers.cancel() must stop all ticks, but the count grew from ${atCancel} to ${ticks.get()}"
      }
    }
  }
}

// --- #3: a slow tick must be skipped, not stacked (the tryLock in scheduleAtFixedRate) ----------
// scheduleAtFixedRate wraps each run in a tryLock(): if the previous tick is still executing, the
// next one returns immediately instead of blocking a callback thread. So a callback that runs longer
// than the period must (a) never overlap itself, and (b) cause intervening ticks to be dropped rather
// than queued up to fire in a burst. Timings are deliberately loose (300ms work vs 50ms period over a
// ~1.2s window) so the assertions hold regardless of the scheduler's tick granularity.

test.async("scheduleAtFixedRate skips overlapping ticks instead of stacking them", 5000) { done ->
  def inFlight = new AtomicInteger(0)
  def overlapped = new AtomicBoolean(false)
  def completed = new AtomicInteger(0)
  def t = new BBScheduledTimer()

  t.scheduleAtFixedRate(new GroovyTimerTask(closure: {
    if (inFlight.incrementAndGet() > 1) overlapped.set(true)
    try {
      Thread.sleep(300)               // far longer than the 50ms period
      completed.incrementAndGet()
    } finally {
      inFlight.decrementAndGet()
    }
  }), 0, 50)

  timers.newTimer().runAfter(1200) {
    t.cancel()
    done {
      assert !overlapped.get() : "ticks must never run concurrently; tryLock should serialize them"
      assert completed.get() > 0 : "sanity: at least one slow tick should complete"
      // ~1200ms / 300ms-per-run => ~4 completions. If ticks were queued rather than skipped this
      // would trend toward 1200/50 = 24, so a generous ceiling still distinguishes the two.
      assert completed.get() <= 8 :
        "slow ticks must be skipped, not queued; got ${completed.get()} completions"
    }
  }
}

// --- BBTimers.removePlayer: registry-level per-player cleanup ------------------------------------
// When a player leaves the experiment the platform calls timers.removePlayer(player) to pull them
// out of every shared timer, so no timer keeps per-player state (or the player vertex) alive after
// they're gone. It delegates to SharedTimer.removePlayer on each registered shared timer;
// BBTimer/BBScheduledTimer hold no per-player state and are left untouched. These are synchronous:
// the timers use a long (3600s) duration so nothing fires during the test, and both are cancelled in
// a finally so the shared-timer registry returns to baseline.

test("timers.removePlayer removes the player from every shared timer, leaving other players intact") {
  def a = g.addPlayer('tr-a')
  def b = g.addPlayer('tr-b')
  def t1 = new SharedTimer([time: 3600, name: 'tr-t1', players: [a, b]])   // a and b
  def t2 = new SharedTimer([time: 3600, name: 'tr-t2', players: [a]])      // a only
  try {
    assert t1.players*.id as Set == ['tr-a', 'tr-b'] as Set
    assert t2.players*.id == ['tr-a']
    assert a.timers.containsKey('tr-t1') && a.timers.containsKey('tr-t2')

    timers.removePlayer(a)

    assert t1.players*.id == ['tr-b'] : "a removed from t1, b left in place; got ${t1.players*.id}"
    assert t2.players.isEmpty()       : "a removed from t2; got ${t2.players*.id}"
    assert !a.timers.containsKey('tr-t1') && !a.timers.containsKey('tr-t2') :
      "a's per-timer entries should be cleared, got ${a.timers.keySet()}"
    assert b.timers.containsKey('tr-t1') : "b's entry on t1 must survive a's removal"

    // Idempotent: a is already gone, so a second removal must not throw or disturb b.
    timers.removePlayer(a)
    assert t1.players*.id == ['tr-b']
  } finally {
    t1.cancel()
    t2.cancel()
  }
}

test("timers.removePlayer is a no-op (never throws) for a player in no shared timer") {
  def a = g.addPlayer('tr-none')
  def t1 = new SharedTimer([time: 3600, name: 'tr-none-t1', players: []])
  try {
    timers.removePlayer(a)          // a was never added to any timer
    assert t1.players.isEmpty()
  } finally {
    t1.cancel()
  }
}

// --- SharedTimer.hasPlayer: membership query by vertex or by id ----------------------------------

test("SharedTimer.hasPlayer answers by vertex and by player id") {
  def a = g.addPlayer('hp-a')
  def b = g.addPlayer('hp-b')
  def t = new SharedTimer([time: 3600, name: 'hp-t', player: a])
  try {
    assert t.hasPlayer(a)       : "a was added, hasPlayer(vertex) should be true"
    assert t.hasPlayer('hp-a')  : "a was added, hasPlayer(id) should be true"
    assert !t.hasPlayer(b)      : "b was never added"
    assert !t.hasPlayer('hp-b')

    t.removePlayer(a)
    assert !t.hasPlayer(a)      : "a was removed, hasPlayer(vertex) should now be false"
    assert !t.hasPlayer('hp-a') : "a was removed, hasPlayer(id) should now be false"
  } finally {
    t.cancel()
  }
}

// --- removePlayer endIfEmpty: end the timer once its last player leaves --------------------------
// onDone runs synchronously inside end(), so these need no async harness -- the flag is set on the
// same thread before removePlayer returns.

test("SharedTimer.removePlayer(player, true) ends the timer only once the last player is gone") {
  def a = g.addPlayer('rpe-a')
  def b = g.addPlayer('rpe-b')
  def ended = new AtomicBoolean(false)
  def t = new SharedTimer([time: 3600, name: 'rpe-t', players: [a, b]])
  t.onDone { ended.set(true) }
  try {
    t.removePlayer(a, true)
    assert !ended.get()   : "one player still remains; the timer must not end yet"
    assert t.isRunning()
    assert t.players*.id == ['rpe-b']

    t.removePlayer(b, true)
    assert ended.get()    : "the last player left with endIfEmpty=true; the timer should have ended"
    assert !t.isRunning() : "an ended timer must no longer be running"
  } finally {
    t.cancel()
  }
}

test("SharedTimer.removePlayer defaults to endIfEmpty=false: emptying the timer leaves it running") {
  def a = g.addPlayer('rpd-a')
  def ended = new AtomicBoolean(false)
  def t = new SharedTimer([time: 3600, name: 'rpd-t', player: a])
  t.onDone { ended.set(true) }
  try {
    t.removePlayer(a)     // no endIfEmpty flag -> keep running even with no players left
    assert t.players.isEmpty()
    assert !ended.get()   : "the default removePlayer must not end the timer"
    assert t.isRunning()
  } finally {
    t.cancel()
  }
}

test("timers.removePlayer(player, true) ends every shared timer left empty, keeping the rest") {
  def a = g.addPlayer('tre-a')
  def b = g.addPlayer('tre-b')
  def t1ended = new AtomicBoolean(false)
  def t2ended = new AtomicBoolean(false)
  def t1 = new SharedTimer([time: 3600, name: 'tre-t1', players: [a]])       // a only -> empties
  def t2 = new SharedTimer([time: 3600, name: 'tre-t2', players: [a, b]])    // a and b -> keeps b
  t1.onDone { t1ended.set(true) }
  t2.onDone { t2ended.set(true) }
  try {
    timers.removePlayer(a, true)
    assert t1ended.get()  : "t1 held only a; removing a with endIfEmpty should end it"
    assert !t1.isRunning()
    assert !t2ended.get() : "t2 still holds b; it must not end"
    assert t2.players*.id == ['tre-b']
    assert t2.isRunning()
  } finally {
    t1.cancel()
    t2.cancel()
  }
}

// --- endPlayer null-guard: don't NPE when a player's per-timer map is already gone ---------------

test("removePlayer does not throw when a player's timers map is missing (endPlayer null-guard)") {
  def a = g.addPlayer('ep-guard-a')
  def t = new SharedTimer([time: 3600, name: 'ep-guard-t', player: a])
  try {
    a.removeProperty('timers')   // simulate a player whose per-timer map was already torn down
    t.removePlayer(a)            // endPlayer() must bail out instead of NPEing on a null timers map
    assert !t.hasPlayer(a)       : "a should still be removed from the timer's player list"
  } finally {
    t.cancel()
  }
}

// --- setDuration: floor the duration at 2000ms --------------------------------------------------

test("SharedTimer.setDuration floors durations below 2000ms and passes larger ones through") {
  def a = g.addPlayer('sd-a')
  def t = new SharedTimer([time: 3600, name: 'sd-t', player: a])
  try {
    t.setDuration(500)
    assert t.playerTimer.duration == 2000 :
      "a sub-floor duration should clamp to 2000, got ${t.playerTimer.duration}"
    t.setDuration(5000)
    assert t.playerTimer.duration == 5000 :
      "a duration at/above the floor should pass through unchanged, got ${t.playerTimer.duration}"
  } finally {
    t.cancel()
  }
}
