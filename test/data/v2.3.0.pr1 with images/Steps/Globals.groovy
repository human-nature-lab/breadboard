// Several variables are injected into the engine when a HIT is submitted, but not during local testing. This sets those variables.
try {
   tutorialTime
} catch (MissingPropertyExceptionmpe) {
   tutorialTime = 5
}

try {
   startAt
} catch (MissingPropertyExceptionmpe) {
   startAt = Math.round((new Date()).getTime() / 1000 + tutorialTime)
}