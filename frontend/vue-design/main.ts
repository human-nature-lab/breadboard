// import Vuetify from 'vuetify'
import Vue from 'vue'
import Players from './Players.vue'
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

const playerSelector = '#vue-player-div'
poll(playerSelector)
  .then(() => {
    console.log('mounting players')
    new Players({
      // vuetify: new Vuetify()
    }).$mount(playerSelector)
  })
  .catch(err => {
    debugger
  })