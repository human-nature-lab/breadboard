// Tests for waiting_room.groovy (the recruitment / waiting-room state machine), written as Groovy
// and registered through the `test` DSL bound by ScriptTestHarness. Production's ScriptLoader skips
// any file ending in `_test.groovy`, so this never loads into a real experiment. Run via:
//   sbt -java-home <jdk8> "testOnly GroovyScriptTests"
//
//   test("name") { ... }                            -> synchronous
//   test.async("name", timeoutMs) { done -> ... }   -> async; signal completion with done()
//   test.skip("name") { ... }                       -> reported as ignored, not run
//
// `g`, `a`, `events`, `timers` etc. are the same engine bindings the platform scripts see;
// WaitingRoom / WaitingRoomReadyUp / RecruitmentController come from waiting_room.groovy, already
// loaded (it's a core script). `a.addEvent` forwards to the disabled EventTracker here, so the
// not-enough-players event path runs without persisting anything.
//
// A client "presses Ready" exactly like the real path: a CustomEvent on the bus -> events.groovy
// routes it to the per-player listener WaitingRoomReadyUp.startPlayer installed via Vertex.once.
// The orchestration tests can't know the precise instant ready-up opens, so they poll each
// player's public `step` and (re)send the ready event until the player flips to ready. Re-sending
// is idempotent -- `once` removes its listener after the first delivery -- and it closes the tiny
// race between `player.step = "ready-up"` and the `once(...)` registration that follows it.
//
// Groovy 1.8.9 target: no closure->functional-interface coercion, no lambdas / `::` refs.

// Press the client-side "Ready" button for a player: drives the real CustomEvent -> per-player
// listener path. clientId must match playerId or events.groovy rejects it as an impersonation.
def pressReady = { player ->
  events.emit("CustomEvent",
    [playerId: player.id, eventName: "waiting-room:ready", data: [:]],
    [clientId: player.id])
}

// Start a 50ms poller that readies up every player in `players` as soon as it enters the ready-up
// phase, retrying until the player is marked ready. Returns the timer so the caller can let the
// harness cancel it at teardown. See the header note on why re-sending is safe and necessary.
def autoReadyUp = { players ->
  def t = timers.newTimer()
  t.scheduleAtFixedRate({
    players.each { p ->
      def wr = p._system.waitingRoom
      // Read `step` via getProperty: a bare `p.step` read resolves to the Gremlin pipeline
      // step() method on a graph element and throws, so it must go through getProperty.
      if (p.getProperty("step") == 'ready-up' && wr != null && !wr.isReady) {
        pressReady(p)
      }
    }
  } as GroovyTimerTask, 50, 50)
  return t
}

// --- WaitingRoom: public API, no timers needed ------------------------------------------------

test("start() requires both the onGroupReady and onReadyUpFailure callbacks") {
  def wr1 = new WaitingRoom(2, 3)
  def threw1 = false
  try { wr1.start() } catch (Exception e) { threw1 = true }
  assert threw1 : 'start() without onGroupReady must throw'

  def wr2 = new WaitingRoom(2, 3)
  wr2.onGroupReady({ players, groupId -> })
  def threw2 = false
  try { wr2.start() } catch (Exception e) { threw2 = true }
  assert threw2 : 'start() with onGroupReady but no onReadyUpFailure must throw'
}

test("addPlayers de-duplicates by id and seeds each player's waiting-room state") {
  def wr = new WaitingRoom(2, 3)
  def a = g.addPlayer('dup-a')
  def b = g.addPlayer('dup-b')
  wr.addPlayers(a, a)        // same player passed twice in one call
  wr.addPlayer(b)
  wr.addPlayer(b)            // ...and again in a later call

  assert wr.waitingPlayers.size() == 2
  assert wr.waitingPlayers*.id as Set == ['dup-a', 'dup-b'] as Set
  assert wr.addedPlayers as Set == ['dup-a', 'dup-b'] as Set

  // The seeded per-player state the loop and ready-up phase rely on.
  assert a._system.waitingRoom.state == 'waiting-room'
  assert a._system.waitingRoom.priority == 0
  assert a._system.waitingRoom.readyUpFailures == 0
  assert a._system.waitingRoom.notChosenForGroup == false
  assert a._system.waitingRoom.notEnoughReadyForGroup == false
  // The default stage callback writes the top-level `step` the client reads (not private.step).
  assert a.getProperty("step") == 'waiting-room'
}

test("removePlayer takes a player out of the room, clears its state, and can be re-added") {
  def wr = new WaitingRoom(2, 3)
  def a = g.addPlayer('rm-a')
  def b = g.addPlayer('rm-b')
  wr.addPlayers(a, b)

  wr.removePlayer(a)
  assert wr.waitingPlayers*.id == ['rm-b']
  assert !wr.addedPlayers.contains('rm-a')
  assert a._system.waitingRoom == null        // _system.remove("waitingRoom")

  wr.addPlayer(a)                              // removal also cleared addedPlayers, so re-add works
  assert wr.waitingPlayers*.id as Set == ['rm-a', 'rm-b'] as Set
  assert a._system.waitingRoom.state == 'waiting-room'
}

// --- WaitingRoomReadyUp: the ready-up sub-phase in isolation -----------------------------------

test("ready-up puts players into the ready-up state and a ready event flips them") {
  def p1 = g.addPlayer('rus-1')
  p1._system.waitingRoom = [:]
  def ru = new WaitingRoomReadyUp([p1], 100)   // long window; the timer is cancelled at teardown
  ru.run({ ready, notReady -> })

  assert p1._system.waitingRoom.state == 'ready-up'
  // `step` is read via getProperty throughout: a bare `.step` read resolves to the Gremlin
  // pipeline step() method on a graph element and throws (production only ever writes step).
  assert p1.getProperty("step") == 'ready-up'  // public mirror that shows the client the Ready button
  assert p1._system.waitingRoom.isReady == false

  pressReady(p1)
  assert p1._system.waitingRoom.isReady == true
  assert p1.getProperty("step") == 'waiting-room'   // button hidden once they've readied up
}

test("a player added mid ready-up joins the round and can ready up") {
  def p1 = g.addPlayer('rua-1')
  p1._system.waitingRoom = [:]
  def ru = new WaitingRoomReadyUp([p1], 100)
  ru.run({ ready, notReady -> })

  def p2 = g.addPlayer('rua-2')
  p2._system.waitingRoom = [:]
  ru.addPlayer(p2)                             // > 5s remain, so the late joiner is accepted

  assert ru.players*.id.contains('rua-2')
  assert p2.getProperty("step") == 'ready-up'
  pressReady(p2)
  assert p2._system.waitingRoom.isReady == true
}

test.async("ready-up partitions ready vs not-ready players and resets state when the window closes", 5000) { done ->
  def p1 = g.addPlayer('ruw-1')
  def p2 = g.addPlayer('ruw-2')
  p1._system.waitingRoom = [:]
  p2._system.waitingRoom = [:]
  def ru = new WaitingRoomReadyUp([p1, p2], 1)   // 1-second window

  ru.run({ readyPlayers, notReadyPlayers ->
    done {
      assert readyPlayers*.id == ['ruw-1']
      assert notReadyPlayers*.id == ['ruw-2']
      // Everyone is reset once the window closes: ready flag cleared, step back to the lobby.
      assert p1._system.waitingRoom.isReady == false
      assert p1.getProperty("step") == 'waiting-room'
      assert p2.getProperty("step") == 'waiting-room'
    }
  })

  pressReady(p1)                                 // p1 readies up; p2 stays idle
  check { assert p1._system.waitingRoom.isReady == true }
}

// --- WaitingRoom: full orchestration through the real loop and timers --------------------------

test.async("a full cycle fills the room, runs ready-up, and starts the group", 12000) { done ->
  def wr = new WaitingRoom(2, 3)
  wr.readyStartDelaySeconds = 1                 // wait after finding a group before opening ready-up
  wr.readyUpSeconds = 1                         // ready-up window
  wr.groupStartDelaySeconds = 1                 // countdown shown before the group "starts"

  def started = []
  wr.onGroupReady({ players, groupId ->
    started << [ids: players*.id as Set, groupId: groupId]
    done {
      assert started.size() == 1
      assert started[0].ids == ['cyc-a', 'cyc-b'] as Set
      assert started[0].groupId == '1'          // id is exposed as a String (matches groupCompleted)
      assert wr.waitingPlayers.isEmpty()        // the selected players left the room
    }
  })
  wr.onReadyUpFailure({ p -> })
  wr.start()

  def a = g.addPlayer('cyc-a')
  def b = g.addPlayer('cyc-b')
  wr.addPlayers(a, b)
  autoReadyUp([a, b])                           // both ready up as soon as ready-up opens
}

test.async("a player who fails to ready up is dropped, and a short group cannot start", 12000) { done ->
  def wr = new WaitingRoom(2, 3)
  wr.readyStartDelaySeconds = 1
  wr.readyUpSeconds = 1
  wr.groupStartDelaySeconds = 1
  wr.readyUpFailureLimit = 1                    // a single miss is enough to be dropped

  def dropped = []
  def groupStarted = false
  wr.onGroupReady({ players, groupId -> groupStarted = true })
  wr.onReadyUpFailure({ p -> dropped << p.id })
  wr.start()

  def a = g.addPlayer('fl-a')
  def b = g.addPlayer('fl-b')
  wr.addPlayers(a, b)
  autoReadyUp([a])                             // only 'a' ever readies up; 'b' never does

  // After the first ready-up window: 'b' missed -> dropped (room falls below minPlayers, so no
  // further rounds run); 'a' was ready but alone -> flagged not-enough and bumped in priority.
  timers.newTimer().runAfter(6000) {
    done {
      assert dropped == ['fl-b']
      assert !wr.waitingPlayers.contains(b)
      assert wr.waitingPlayers*.id == ['fl-a']
      assert groupStarted == false
      assert a._system.waitingRoom.notEnoughReadyForGroup == true
      assert a._system.waitingRoom.priority == 1
    }
  }
}

// --- RecruitmentController: client/game state tracking ----------------------------------------

test("RecruitmentController tracks a client through its lifecycle without duplicating it") {
  def rc = new RecruitmentController()
  rc.clientPending("c1")
  assert rc.clients.find { it.id == "c1" }.state == "pending"
  rc.clientWaiting("c1")
  assert rc.clients.find { it.id == "c1" }.state == "waiting"
  rc.clientCompleted("c1")                  // regression: this method previously didn't exist
  assert rc.clients.find { it.id == "c1" }.state == "completed"
  assert rc.clients.size() == 1            // same client updated in place (no clients[-1] dup)
}

test("RecruitmentController.gameStarted/gameCompleted move clients and count completed games") {
  def rc = new RecruitmentController()
  rc.clientWaiting("g1")
  rc.clientWaiting("g2")

  rc.gameStarted("game-1", ["g1", "g2"])
  assert rc.clients.find { it.id == "g1" }.state == "active"
  assert rc.clients.find { it.id == "g1" }.gameId == "game-1"
  assert rc.activeGames.containsKey("game-1")

  rc.gameCompleted("game-1")
  assert rc.clients.find { it.id == "g1" }.state == "completed"
  assert rc.clients.find { it.id == "g1" }.gameId == null
  assert !rc.activeGames.containsKey("game-1")
  assert rc.completedGames == 1
}

test("WaitingRoom.clientCompleted delegates to the recruitment controller without throwing") {
  def wr = new WaitingRoom(2, 3)
  wr.clientCompleted("x")                   // regression: was a MissingMethodException
  assert wr._recruitmentController.clients.find { it.id == "x" }.state == "completed"
}
