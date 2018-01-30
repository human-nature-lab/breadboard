initStep = stepFactory.createNoUserActionStep("InitStep")
seniorityOrder = []
currentPirateIndex = 0
captainId = null
def activeCount(){
  return g.V.count() - currentPirateIndex
}
initStep.run = {
  println "initStep.run"
  // g.addTimer(time: gameLength, timerText:"Time remaining: ")
  g.complete()
  seniorityOrder = g.V.transform({it.id}).shuffle().toList()
  seniorityOrder.each{
  	println it 
  }
  println "Starting with pirate: " + seniorityOrder[currentPirateIndex]
  
  distributionStep.start()
  
}
initStep.done = {
  println "initStep.done"
}