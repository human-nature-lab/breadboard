import 'core-js'
import 'regenerator-runtime'
import { http } from './http'
import { Mutex } from 'async-mutex'
import { Vue as VueType } from 'vue/types/vue'
import { Emitter } from 'goodish'
// @ts-ignore
import DefaultView from '../client/mixins/DefaultView'
import { BreadboardConfig, BreadboardMessages, VueLoadOpts } from './breadboard.types'
import { SimpleMap } from '../client/types'

export class BreadboardClass extends Emitter implements BreadboardMessages {

  private socket!: WebSocket
  private isConnected: boolean = false
  private config!: BreadboardConfig
  private socketMutex = new Mutex()
  private configMutex = new Mutex()

  /**
   * Load the client config.
   * @returns {Promise<object>}
   */
  async loadConfig (): Promise<BreadboardConfig> {
    const release = await this.configMutex.acquire()
    if (this.config) {
      release()
      return this.config
    }
    try {
      const res = await http.get<BreadboardConfig>('state')
      this.config = await res.json()
    } finally {
      release()
    }
    return this.config
  }

  /**
   * Returns the websocket that's connected to the server.
   * @returns {Promise<WebSocket>}
   */
  async connect (): Promise<WebSocket> {
    const release = await this.socketMutex.acquire()
    if (this.isConnected) {
      release()
      return this.socket
    }
    try {
      const config = await this.loadConfig()
      this.socket = new WebSocket(config.connectSocket)
      this.isConnected = true
      this.attachParser()
    } finally {
      release()
    }
    return this.socket
  }

  /**
   * Disconnect the websocket
   */
  disconnect () {
    this.socket.close()
    this.isConnected = false
    this.removeListeners()
  }

  /**
   * Send data to breadboard server
   * @param action
   * @param data
   */
  send (action: string, data: SimpleMap<any>) {
    if (!this.isConnected) throw new Error('Unable to send data when connection is closed')
    const d = Object.assign(data, {
      action
    })
    this.socket.send(JSON.stringify(d))
  }

  /**
   * Login method
   */
  async login () {
    const config = await this.loadConfig()
    return new Promise(async resolve => {
      await this.connect()
      const sendLogin = () => {
        this.socket.send(JSON.stringify({
          action: 'LogIn',
          clientId: config.clientId,
          referer: config.referer,
          connection: config.connection,
          accept: config.accept,
          acceptLanguage: config.acceptLanguage,
          acceptEncoding: config.acceptEncoding,
          userAgent: config.userAgent,
          host: config.host,
          ipAddress: config.ipAddress,
          requestURI: config.requestURI
        }))
        resolve()
      }
      if (this.socket.readyState === WebSocket.OPEN) {
        sendLogin()
      } else {
        this.socket.addEventListener('open', sendLogin, { once: true })
      }

    })
  }

  /**
   * Load a script from a url.
   * @param src
   * @returns {Promise<boolean>}
   */
  addScriptFromURL (src: string): Promise<void> {
    return new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = src
      script.onload = () => setTimeout(resolve)
      script.onerror = reject
      document.body.appendChild(script)
    })
  }

  /**
   * Load a script from text
   * @param contents
   */
  addScriptFromString (contents: string): Promise<void> {
    return new Promise((resolve, reject) => {
      try {
        var geval = eval
        geval(contents)
        setTimeout(resolve)
      } catch (err) {
        reject(err)
      }
    })
  }

  /**
   * Inject styles from a string
   * @param contents
   */
  addStyleFromString (contents: string): Promise<void> {
    return new Promise(resolve => {
      const style = document.createElement('style')
      const text = document.createTextNode(contents)
      style.appendChild(text)
      document.body.appendChild(style)
    })
  }

  /**
   * Inject CSS from a url using a "link" node
   * @param href
   */
  addStyleFromURL (href: string, type = 'text/css'): Promise<void> {
    return new Promise((resolve, reject) => {
      const link = document.createElement('link')
      link.href = href
      link.rel = 'stylesheet'
      link.type = type
      link.onload = () => resolve()
      link.onerror = reject
      document.head.appendChild(link)
    })
  }

  /**
   * Load Vue, Vuetify and Breadboard component dependencies
   * @param opts
   */
  async loadVueDependencies (opts: VueLoadOpts = {}) {
    opts = Object.assign({
      vueVersion: '2.6.10',
      vuetifyVersion: '2.0.19',
      useDev: false
    }, opts)
    await this.addScriptFromURL(`https://cdnjs.cloudflare.com/ajax/libs/vue/${opts.vueVersion}/vue.${opts.useDev ? 'common.dev.' : ''}js`)
    await Promise.all([
      this.addScriptFromURL(`https://cdnjs.cloudflare.com/ajax/libs/vuetify/${opts.vuetifyVersion}/vuetify.min.js`),
      this.addScriptFromURL('/bundles/vue-components.js'),
      this.addStyleFromURL(`https://cdnjs.cloudflare.com/ajax/libs/vuetify/${opts.vuetifyVersion}/vuetify.min.css`),
      this.addStyleFromURL('/bundles/client.css')
    ])

    const Vue = window.Vue
    const Vuetify = window.Vuetify

    // Register Vuetify components
    Vue.use(Vuetify)

    // Register all Breadboard components globally
    for (const c of window.BreadboardVueComponents) {
      Vue.component(c.name, c.component)
    }
  }

  /**
   * Create default Vue instance
   * @param template
   */
  async createDefaultVue (template: string,): Promise<VueType> {
    const Vue = window.Vue
    const Vuetify = window.Vuetify
    return new Vue({
      vuetify: new Vuetify(),
      mixins: [DefaultView],
      template: template
    }).$mount('#app')
  }

  /**
   * Handle parsing breadboard socket events
   */
  private attachParser () {
    this.socket.addEventListener('message', ev => {
      this.emit('message', ev)
      const data = JSON.parse(ev.data)
      this.emit('data', data)
      for (const key of ['graph', 'player', 'style']) {
        if (data[key]) {
          this.emit(key, data[key])
        }
      }
    })
  }
}

window.Breadboard = new BreadboardClass()
