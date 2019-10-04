import gremlins from 'gremlins-ts'

export function startGremlins (player, doneCb) {
  if (player.droprate !== 0) {
    const r = Math.random()
    if (r < player.droprate || r < 0.2) return
  }
  if (player.testmode) {
    const horde = gremlins.createHorde()
      .gremlin(gremlins.species.formFiller().triggerInputEvent(true))
      .gremlin(gremlins.species.targetedClicker()
        .clickTypes(['click'])
        .interestingElements(['button'])
        .percentRandom(0))
      .gremlin(gremlins.species.reloader())
      .mogwai(gremlins.mogwais.gizmo())
      .mogwai(gremlins.mogwais.alert())
      .strategy(gremlins.strategies.distribution([0.4, 0.4, 0.2]).delay(100))
      .logger({
        log: () => {},
        info: () => {},
        error: () => {},
        warn: () => {}
      })
      .after(() => {
        horde.stop()
        doneCb()
      })
      .unleash()
  }
}
