import { BreadboardConfig } from '../core/breadboard.types'
import './client.sass'
import { Breadboard } from '../core/breadboard'

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
