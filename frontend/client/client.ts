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
        :nodeFill="node => node.isEgo ? 'blue' : 'lightgrey'"
        :nodeRadius="node => node.data.score * 2 || 10">
        <template v-slot:node-content="{ node }">
          <text text-anchor="middle" v-if="node.isEgo" fill="white">{{node.id}}</text> <!-- centered label inside the node-content slot -->
          <image v-else-if="node.data.score < 15" href="/images/33" width="50" height="50" x="-25" y="-25" /> <!-- centered image in the node-content slot -->
<!--          <image v-else href="https://cdn.imgbin.com/0/24/23/imgbin-cat-kitten-face-tr-s-N5RBq5XrXbz24bjNrUfYMM9zA.jpg" width="50" height="50" x="-25" y="-25" /> &lt;!&ndash; external image &ndash;&gt;-->
          <image v-else href="/images/34" width="50" height="50" x="-25" y="-25" /> <!-- transparent image background -->
        </template>
        <template v-slot:edge-label="{ edge }">
          <text text-anchor="middle">Edge: {{edge.target.id}}</text>  <!-- simple centered edge label -->
        </template>
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
