// From default-experiment
onJoinStep = stepFactory.createNoUserActionStep()

onJoinStep.run = { playerId->
  println "onJoinStep.run"
  def player = g.getVertex(playerId)
}

onJoinStep.done = {
  println "onJoinStep.done"
}
