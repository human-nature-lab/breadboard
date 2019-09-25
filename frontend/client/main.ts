import Vue from 'vue'
import vuetify from '@/plugins/vuetify'
import { Breadboard } from './types'

async function main () {
  let config
  try {
    config = await Breadboard.loadConfig()
  } catch (err) {
    console.error('Breadboard: Unable to load Breadboard')
    throw err
  }

  try {
    await Breadboard.addScriptFromString(config.clientGraph)
  } catch (err) {
    console.error('Breadboard: Unable to run client-graph.js')
    throw err
  }

  // TODO: Move this to client graph instead
  Breadboard.loadConfig().then(config => {
    new Vue({
      vuetify,
      // template: config.clientHtml
      template: `<v-app></v-app>`
    }).$mount('#app')
  })
}

main()
