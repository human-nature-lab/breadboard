import 'core-js'
import 'regenerator-runtime'
import { http } from './http'
import { Mutex } from 'async-mutex'
import VueType from 'vue'
import { Emitter } from 'goodish'
// @ts-ignore
import DefaultView from '../client/mixins/DefaultView'
import { BreadboardConfig, BreadboardMessages, VueLoadOpts } from './breadboard.types'
import { SimpleMap } from '../client/types'

const MAKE_CHOICE = 'MakeChoice'
const CUSTOM_EVENT = 'CustomEvent'

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
   * Returns the websocket that has already been connected to the server.
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
    if (!this.isConnected) return
    this.socket.close()
    this.isConnected = false
    this.removeListeners()
  }

  /**
   * Send data to breadboard server
   * @param action
   * @param data
   */
  sendType (action: string, data: SimpleMap<any> = {}) {
    if (!this.isConnected) throw new Error('Unable to send data when connection is closed')
    const d = Object.assign(data, {
      action
    })
    this.socket.send(JSON.stringify(d))
  }

  /**
   * Shortcut for sending a choice via breadboard. Helps keeps params from throwing silent bugs.
   * @param uuid 
   * @param params 
   */
  sendChoice (uuid: string, params?: SimpleMap<any>) {
    const data: any = {
      choiceUID: uuid
    }
    if (params && typeof params !== 'string') {
      data.params = JSON.stringify(params)
    }
    return this.sendType(MAKE_CHOICE, data)
  }

  /**
   * Shortcut for sending a custom event via breadboard. 
   * @param params 
   */
  sendCustom (params?: SimpleMap<any>) {
    return this.sendType(CUSTOM_EVENT, params)
  }

  /**
   * Send a custom event scoped to the current player. This will also trigger any global custom event handlers.
   * @param params 
   */
  async send (eventName: string, data: SimpleMap<any> = {}) {
    const config = await this.loadConfig()
    return this.sendCustom({
      playerId: config.clientId,
      eventName: eventName,
      data: data
    })
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
   * @param url
   * @returns {Promise<boolean>}
   */
  addScriptFromURL (url: string): Promise<void> {
    if (url.substr(0, 4) !== 'http') {
      url = this.getCoreRoot() + url
    }
    return new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = url
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
    if (href.substr(0, 4) !== 'http') {
      href = this.getCoreRoot() + href
    }
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
      vuetifyVersion: '2.1.9',
      mdiVersion: '4.5.95',
      useDev: false
    }, opts)
    this.addStyleFromURL('https://fonts.googleapis.com/css?family=Roboto:100,300,400,500,700,900')
    this.addStyleFromURL('/bundles/client.css')
    this.addStyleFromURL(`https://cdn.jsdelivr.net/npm/@mdi/font@${opts.mdiVersion}/css/materialdesignicons.min.css`)
    this.addStyleFromURL(`https://cdn.jsdelivr.net/npm/vuetify@${opts.vuetifyVersion}/dist/vuetify.min.css`)
    await this.addScriptFromURL(`https://cdnjs.cloudflare.com/ajax/libs/vue/${opts.vueVersion}/vue.${opts.useDev ? 'common.dev.' : ''}js`)
    await Promise.all([
      this.addScriptFromURL(`https://cdn.jsdelivr.net/npm/vuetify@${opts.vuetifyVersion}/dist/vuetify.js`),
      this.addScriptFromURL('/bundles/vue-components.js')
    ])

    const Vue = window.Vue
    const Vuetify = window.Vuetify

    // Register Vuetify components
    Vue.use(Vuetify)

    // Register all Breadboard components globally
    for (const c of window.BreadboardVueComponents) {
      console.log('Registering component', c.name, c)
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
      vuetify: new Vuetify({
        icons: {
          iconfont: 'mdi'
        }
      }),
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
      if (data.eventName && data.data) {
        console.log('custom event', data)
        this.emit(data.eventName, ...data.data)
      }
    })
  }

  /**
   * Return the path of the breadboard core script
   */
  private getCoreRoot (): String {
    const scriptTag = Array.from(document.querySelectorAll('script')).find(s => /breadboard.*\.js$/.test(s.src))
    if (!scriptTag) {
      return window.location.origin
    }
    return new URL(scriptTag.src).origin
  }
}

window.Breadboard = new BreadboardClass()
