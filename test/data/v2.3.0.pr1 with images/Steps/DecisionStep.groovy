decisionStep = stepFactory.createStep("DecisionStep")

decisionStep.run = {
  println "decisionStep.run"
  g.V.filter{it.id == captainId}.each{captain->
  	captain.accepted = 1 
  }
  g.V.filter{it.id != captainId && it.active}.each{pirate->
    pirate.accepted = -1
    pirate.text = c.get("Offer", pirate.booty)
   	a.add(pirate, [
      name: "Accept this offer",
      result: {
        pirate.text = c.get("AcceptedOffer", pirate.booty)
      	pirate.accepted = 1 
      }
    ], [
      name: "Throw them overboard",
      result: {
        pirate.text = c.get("ThrowThemOverboard", pirate.booty)
      	pirate.accepted = 0 
      }
    ])
  }
}

decisionStep.done = {
  println "decisionStep.done"
  weHaveConsensus = true
  g.V.filter{it.active}.each{pirate->
    if(pirate.accepted == 0){
      weHaveConsensus = false
    }
  }
  if(weHaveConsensus){
  	finishedStep.start()
  } else {
    throwOverboard(seniorityOrder[currentPirateIndex]) 
  }
}


def throwOverboard(id){
  println "Throwing " + id + " overboard"
  g.V.filter({it.id == id}).each{pirate ->
  	pirate.active = false
    pirate.text = "You've been thrown overboard. Better luck next time!"
   	pirate.booty = overboardReward
    g.removeEdges(pirate)
  }
  currentPirateIndex ++
  if(currentPirateIndex >= seniorityOrder.size()){
    finishedStep.start() 
  } else {
    distributionStep.start() 
  }
}