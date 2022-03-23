import { Breadboard, Graph } from '@human-nature-lab/breadboard-core'
import { Mutex } from 'async-mutex'

const gremlinsMutex = new Mutex()
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
    const [_, config] = await Promise.all([Breadboard.login(), Breadboard.loadConfig()])
    this.config = config
    this.player.id = config.clientId

    Breadboard.on('style', contents => {
      try {
        if (contents.length) {
          window.Breadboard.addStyleFromString(contents)
        }
      } catch (err) {
        console.error('Unable to apply custom style to Breadboard')
      }
    })

    Breadboard.on('player', async player => {
      this.player = player
      this.player.id = this.config.clientId
      this.checkGremlins()
    })

    this.graph.attachToBreadboard(Breadboard)

  },
  beforeDestroy () {
    this.graph.releaseFromBreadboard()
  },
  methods: {
    log (...args) {
      console.log(...args)
    },
    async checkGremlins () {
      // Inject gremlins if in test mode
      let stopGremlins
      let startGremlins
      if (this.player.testmode) {
        const release = await gremlinsMutex.acquire()
        if (startGremlins) {
          release()
        } else {
          try {
            let d = await import(/* webpackChunkName: "gremlins" */'../gremlins/gremlins.js')
            startGremlins = d.startGremlins
            stopGremlins = d.stopGremlins
          } finally {
            release()
          }
        }
        // The gremlins script takes care of checking for overlapping
        const restartGremlins = () => {
          startGremlins(this.player, restartGremlins)
        }
        restartGremlins()
      } else if (stopGremlins) {
        stopGremlins()
      }
    }
  }
}
