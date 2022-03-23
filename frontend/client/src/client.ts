import { Breadboard, BreadboardConfig } from '@human-nature-lab/breadboard-core'
import './client.sass'

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
}

client()
