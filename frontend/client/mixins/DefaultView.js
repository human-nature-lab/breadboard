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
      try {
        if (contents.length) {
          window.Breadboard.addStyleFromString(contents)
        }
      } catch (err) {
        console.error('Unable to apply custom style to Breadboard')
      }
    })

    window.Breadboard.on('player', async player => {
      // TODO: First check if anything has changed before updating
      this.player = player
      this.player.id = this.config.clientId

      // Inject gremlins if in test mode
      if (player.testmode) {
        const { startGremlins } = await import(/* webpackChunkName: "gremlins" */'../gremlins.js')
        function restartGremlins () {
          startGremlins(player, restartGremlins)
        }
        restartGremlins()
      }


    })
    this.graph.attachToBreadboard(window.Breadboard)
  },
  beforeDestroy () {
    this.graph.releaseFromBreadboard()
  }
}
