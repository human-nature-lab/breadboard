distributionStep = stepFactory.createStep("DistributionStep")

distributionStep.run = {
  captainId = seniorityOrder[currentPirateIndex]
  g.V.filter{it.active}.each{
  	it.isCaptain = false
    it.text = "Your captain is deciding how to distribute the booty. Please wait while they make their decision."
  }
  g.V.filter({
    it.id == captainId
  }).each({v->
    v.isCaptain = true
  	a.add(v, [
      name: "Evenly distribute to " + activeCount() + " pirates",
      result: {
        g.V.each{pirate->
          pirate.booty = reward / activeCount()
        }
      }
    ])
  })
  println "distributionStep.run"
}

distributionStep.done = {
  println "distributionStep.done"
  decisionStep.start()
}