import { Graph } from '../lib/graph'
import { isEqual, isEqualWith } from 'lodash'

export default {
  data () {
    return {
      graph: new Graph(),
      player: {
        text: 'Loading...',
        choices: []
      },
      config: null,
      gremlinsStarted: false
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
      // First check if anything has changed before updating the view
      const playerHasChanged = !isEqualWith(this.player, player, (a, b, key) => {
        // Ignore the fact the player id is not included in the payload... It would be great if it were included by default?
        if (key === 'id' && a.hasOwnProperty('timers') && b.hasOwnProperty('timers')) {
          return true
        } else {
          return isEqual(a, b)
        }
      })

      if (playerHasChanged) {
        this.player = player
        this.player.id = this.config.clientId
      } else {
        return
      }

      this.checkGremlins()

    })

    this.graph.attachToBreadboard(window.Breadboard)

  },
  beforeDestroy () {
    this.graph.releaseFromBreadboard()
  },
  methods: {
    async checkGremlins () {
      // Inject gremlins if in test mode
      let stopGremlins
      let startGremlins
      if (!this.gremlinsStarted && this.player.testmode) {
        this.gremlinsStarted = true
        let d = await import(/* webpackChunkName: "gremlins" */'../gremlins/gremlins.js')
        startGremlins = d.startGremlins
        stopGremlins = d.stopGremlins()
        const restartGremlins = () => {
          startGremlins(this.player, restartGremlins)
        }
        restartGremlins()
      } else if (this.gremlinsStarted && stopGremlins) {
        stopGremlins()
      }
    }
  }
}
