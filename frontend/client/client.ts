import { BreadboardConfig } from '../core/breadboard.types'
import './client.sass'

const Breadboard = window.Breadboard

async function client () {
  let config: BreadboardConfig
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

  // TODO: Move this to client graph
  await Breadboard.loadVueDependencies({
    useDev: true
  })
  await Breadboard.createDefaultVue(`<v-app>
    <v-layout row>
      <SVGGraph 
        class="w-50 h-screen"
        :player="player" 
        :graph="graph"
        :nodeBorderWidth="node => node.isEgo ? 2 : 1"
        :nodeFill="node => node.isEgo ? 'blue' : 'red'"
        :nodeRadius="node => node.isEgo ? 50 : 30">  
      </SVGGraph>
      <v-flex class="w-50 h-screen">
        <v-container style="background: #ebebeb" class="h-full">
          <v-layout column>
            <PlayerTimers :player="player"></PlayerTimers>
            <PlayerText :player="player"></PlayerText>
            <PlayerChoices :player="player"></PlayerChoices>
          </v-layout>
        </v-container>
      </v-flex>
    </v-layout>
  </v-app>`)

}

client()
