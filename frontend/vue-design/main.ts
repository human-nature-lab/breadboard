// import Vuetify from 'vuetify'
import Vue from 'vue'
import { Breadboard } from '@human-nature-lab/breadboard-core'
import Players from './Players.vue'
import { AdminGraph } from './AdminGraph'

function poll (selector: string, interval = 500): Promise<void> {
  return new Promise(resolve => {
    const id = setInterval(() => {
      if (document.querySelector(selector)) {
        clearInterval(id)
        resolve()
      }
    }, interval)
  })
}

// Vue.use(Vuetify)

// Create the data store eagerly and attach it to Breadboard at module load —
// this runs before Angular connects the socket and sends LogIn, so the store
// accumulates the live event stream regardless of when the Vue view mounts.
// (Imported from the package rather than window.Breadboard so we share the
// exact singleton design.js assigns, without depending on import timing.)
const graph = new AdminGraph()
graph.attachToBreadboard(Breadboard)

const playerSelector = '#vue-player-div'
poll(playerSelector)
  .then(() => {
    console.log('mounting players')
    new Players({
      propsData: { graph }
      // vuetify: new Vuetify()
    }).$mount(playerSelector)
  })
  .catch(err => {
    debugger
  })