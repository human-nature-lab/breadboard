isFirstPlayer = true
onJoinStep = stepFactory.createNoUserActionStep()

onJoinStep.run = { playerId->
  println "onJoinStep.run"
  def player = g.getVertex(playerId)
  player.active = true
  now = new Date().getTime() / 1000
  if(isFirstPlayer){
  	startAt = now + tutorialTime
    isFirstPlayer = false
    println "startAt " + startAt
    println "tutorialTime " + tutorialTime
    new Timer().runAfter(tutorialTime*1000, {
      initStep.start()
    })
    // Remove in production
  	g.addAI(a, 8)
  	g.star()
  }
  timerDuration = startAt - now
  if(timerDuration > 0){
  	g.addTimer(player: player, time: timerDuration as Integer, timerText:"Game will begin in: ")
  }

}
onJoinStep.done = {
  println "onJoinStep.done"
}