import { Graph } from '../lib/graph'

export default {
  data () {
    return {
      graph: new Graph(),
      player: {
        text: 'Loading...',
        choices: []
      },
      config: null
    }
  },
  async created () {
    const [_, __, config] = await Promise.all([window.Breadboard.connect(), window.Breadboard.login(), window.Breadboard.loadConfig()])
    this.config = config
    this.player.id = config.clientId
    // window.Breadboard.on('data', console.log)
    window.Breadboard.on('style', contents => {
      if (contents.length) {
        window.Breadboard.addStyleFromString(contents)
      }
    })
    window.Breadboard.on('player', player => {
      // TODO: First check if anything has changed before updating
      this.player = player
      this.player.id = this.config.clientId
    })
    this.graph.attachToBreadboard(window.Breadboard)
  },
  beforeDestroy () {
    this.graph.releaseFromBreadboard()
  }
}
