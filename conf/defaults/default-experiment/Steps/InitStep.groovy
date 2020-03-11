// From default-experiment
initStep = stepFactory.createStep("InitStep")

initStep.run = {
  println "initStep.run"
}

initStep.done = {
  println "initStep.done"
}
