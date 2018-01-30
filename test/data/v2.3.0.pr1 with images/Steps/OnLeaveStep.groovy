onLeaveStep = stepFactory.createNoUserActionStep()

onLeaveStep.run = {
  println "onLeaveStep.run"
}
onLeaveStep.done = {
  println "onLeaveStep.done"
}