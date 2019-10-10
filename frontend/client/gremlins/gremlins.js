import gremlins from 'gremlins-ts'
import targetedClickerGremlin from './TargetedClickerGremlin'

let horde
export function startGremlins (player, doneCb) {
  console.log('start gremlins')
  if (player.droprate !== 0) {
    const r = Math.random()
    if (r < player.droprate || r < 0.2) return
  }
  if (player.testmode) {

    horde = gremlins.createHorde()
      .gremlin(gremlins.species.formFiller())
      .gremlin(targetedClickerGremlin(['button', 'circle', 'edge', 'input']))
      // .gremlin(gremlins.species.reloader())
      .gremlin(gremlins.species.typer())
      // .gremlin(gremlins.species.toucher().canTouch(e => {
      //   return acceptedTypes.indexOf(e.tagName) > -1 || e.className.includes('svg-container')
      // }))
      .gremlin(gremlins.species.scroller())
      .mogwai(gremlins.mogwais.gizmo())
      .mogwai(gremlins.mogwais.alert())
      .strategy(gremlins.strategies.distribution([0.4, 0.4, 0.2]).delay(100))
/*      .logger({
        log: () => {},
        info: () => {},
        error: () => {},
        warn: () => {}
      })*/
      .after(() => {
        console.log('stop gremlins')
        if (horde) {
          horde.stop()
        }
        doneCb()
      })
      .unleash()
  }
}

export function stopGremlins () {
  if (horde) {
    horde.stop()
  }
}
