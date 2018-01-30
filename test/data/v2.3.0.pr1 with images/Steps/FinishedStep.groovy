finishedStep = stepFactory.createStep("FinishedStep")

finishedStep.run = {
  println "finishedStep.run"
  g.V.each{pirate->
  	pirate.text = c.get("Finished", pirate.booty) 
  }
}

finishedStep.done = {
  println "finishedStep.done"
}