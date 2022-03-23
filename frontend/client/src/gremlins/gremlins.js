import gremlins from 'gremlins-ts'
import targetedClickerGremlin from './TargetedClickerGremlin'

let horde
let isStarted = false
export function startGremlins (player, doneCb) {
  if (isStarted) return
  isStarted = true
  if (player.testmode) {
    if (player.droprate) {
      const r = Math.random()
      if (r < player.droprate) {
        console.log('dropping player')
        return
      }
    }
    console.log('start gremlins')
    horde = gremlins.createHorde()
      .gremlin(gremlins.species.formFiller())
      .gremlin(targetedClickerGremlin(['button', 'circle', 'edge', 'input']))
      // .gremlin(gremlins.species.reloader())
      .gremlin(gremlins.species.typer())
      // .gremlin(gremlins.species.toucher().canTouch(e => {
      //   return acceptedTypes.indexOf(e.tagName) > -1 || e.className.includes('svg-container')
      // }))
      .mogwai(gremlins.mogwais.gizmo())
      .mogwai(gremlins.mogwais.alert())
      .strategy(gremlins.strategies.distribution([0.4, 0.4, 0.2]).delay(1000))
      .logger({
        log: null,
        info: null,
        error: null,
        warn: null
      })
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
  if (isStarted && horde) {
    console.log('stop gremlins')
    horde.stop()
  }
}
