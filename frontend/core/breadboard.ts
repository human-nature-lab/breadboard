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
import { Socket } from './socket'

const MAKE_CHOICE = 'MakeChoice'
const CUSTOM_EVENT = 'CustomEvent'

export class BreadboardClass extends Emitter implements BreadboardMessages {

  private socket!: Socket
  private config!: BreadboardConfig | null
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
   * Returns the connected Socket instance.
   * @returns {Promise<Socket>}
   */
  async connect (): Promise<Socket> {
    const release = await this.socketMutex.acquire()
    if (this.socket) {
      release()
      return this.socket
    }
    try {
      const config = await this.loadConfig()
      this.socket = new Socket(config.connectSocket)
      // Propagate all socket events out to the Breadboard object
      for (const event of ['connect', 'open', 'close', 'error', 'retry']) {
        this.socket.on(event, (...args: any) => this.emit(event, ...args))
      }
      this.attachParser()
    } finally {
      release()
    }
    return this.socket
  }

  /**
   * Disconnect the WebSocket
   */
  disconnect () {
    this.config = null
    if (!this.socket) return
    this.socket.close()
    this.removeListeners()
  }

  /**
   * Send data to breadboard server
   * @param action
   * @param data
   */
  sendType (action: string, data: SimpleMap<any> = {}) {
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
  sendChoice (uuid: string, params: SimpleMap<any> = {}) {
    const data: any = {
      choiceUID: uuid
    }
    const customParams = this.getCustomParams()
    params = Object.assign(params, customParams)
    if (params && typeof params !== 'string') {
      data.params = JSON.stringify(params)
    }
    return this.sendType(MAKE_CHOICE, data)
  }

  getCustomParams () {
    const map: { [key: string]: any } = {}
    const customInputs = Array.from(document.querySelectorAll('.param'))
    for (const inp of customInputs) {
      const name = inp.getAttribute('name')
      if (name) {
        const type = inp.getAttribute('type')
        if (inp instanceof HTMLInputElement) {
          if (type === 'checkbox') {
            if (!map.hasOwnProperty(name)) {
              map[name] = []
            }
            if (inp.checked!) {
              map[name].push(inp.value)
            }
          } else {
            map[name] = inp.value
          }
        } else if (inp instanceof HTMLTextAreaElement) {
          map[name] = inp.value
        } else {
          console.log(`.param class isn't on an input field`, inp)
        }
      } else {
        console.log('skipped field without a name', inp)
      }
    }
    return map
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
    await this.connect()
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
  }

  /**
   * Load a script from a URL
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
    const config = await this.loadConfig()
    opts = Object.assign({
      vueVersion: '2.6.11',
      vuetifyVersion: '2.3.7',
      mdiVersion: '5.4.55',
      useDev: false,
      withVuetify: true,
      withCore: true
    }, opts)
    console.log('config', config)
    this.addStyleFromURL('https://fonts.googleapis.com/css?family=Roboto:100,300,400,500,700,900')
    this.addStyleFromURL(`${config.assetsRoot}/bundles/client.css`)
    if (opts.withVuetify) {
      this.addStyleFromURL(`https://cdn.jsdelivr.net/npm/@mdi/font@${opts.mdiVersion}/css/materialdesignicons.min.css`)
      this.addStyleFromURL(`https://cdn.jsdelivr.net/npm/vuetify@${opts.vuetifyVersion}/dist/vuetify.min.css`)
    }
    this.addStyleFromURL(`${config.assetsRoot}/bundles/vue-components.css`)
    await this.addScriptFromURL(`https://cdnjs.cloudflare.com/ajax/libs/vue/${opts.vueVersion}/vue.${opts.useDev ? 'common.dev.' : 'min.'}js`)
    if (opts.withVuetify) {
      await this.addScriptFromURL(`https://cdn.jsdelivr.net/npm/vuetify@${opts.vuetifyVersion}/dist/vuetify.js`)
      const Vue = window.Vue
      const Vuetify = window.Vuetify
  
      // Register Vuetify components
      Vue.use(Vuetify)
      if (opts.withCore) {
        await this.addScriptFromURL(`${config.assetsRoot}/bundles/vue-components.js`)
      }
    }
  }

  /**
   * Create default Vue instance
   * @param template
   */
  async createDefaultVue (template: string, mixin?: object): Promise<VueType> {
    const Vue = window.Vue
    const Vuetify = window.Vuetify
    const mixins = [DefaultView]
    if (mixin) {
      // @ts-ignore
      mixins.push(mixin)
    }
    return new Vue({
      vuetify: new Vuetify({
        icons: {
          iconfont: 'mdi'
        }
      }),
      mixins: mixins,
      template: template
    }).$mount('#app')
  }

  /**
   * Loads the legacy, angular.js client code. Replaces the SPA anchor with the old angular ng-app code
   */
  async loadAngularClient () {
    const config = await this.loadConfig()
    this.addStyleFromURL(`${config.assetsRoot}/bundles/client.css`)
    this.addStyleFromURL(`${config.assetsRoot}/bundles/client-angular.css`)
    this.addStyleFromURL('https://fonts.googleapis.com/css?family=Open+Sans:700,400')
    this.addStyleFromURL(`${config.assetsRoot}/css/bootstrap.min.css`)
    this.addStyleFromURL(`${config.assetsRoot}/css/font-awesome-4.7.0/css/font-awesome.min.css`)
    await Promise.all([
      this.addScriptFromURL('https://cdnjs.cloudflare.com/ajax/libs/jquery/1.7.2/jquery.min.js'),
      this.addScriptFromURL(`${config.assetsRoot}/bundles/client-angular.js`)
    ])
    await Promise.all([
      this.addScriptFromURL('https://cdnjs.cloudflare.com/ajax/libs/d3/2.10.0/d3.v2.js'),
      this.addScriptFromURL('https://cdnjs.cloudflare.com/ajax/libs/angular-ui-bootstrap/0.12.1/ui-bootstrap-tpls.js')
    ])
    // @ts-ignore
    const ang = angular; const init = window.bbClientInit
    ang.element(document).ready(function() {
      const t = document.createElement('div')
      const app = document.createElement('app')
      app.innerText = 'Loading...'
      document.body.setAttribute('ng-app', 'breadboard.client')
      const c = document.getElementById('app')
      if (!c) throw new Error('Unable to initialize app')
      document.body.replaceChild(app, c)
      init()
      ang.bootstrap(document, ['breadboard.client'])
    })
  }

  /**
   * Get an instance of the client graph
   */
  public async getGraph () {
    const v = (await import('../client/lib/graph'))
    const Graph = v.Graph
    const graph = new Graph()
    graph.attachToBreadboard(this)
    return graph
  }

  /**
   * Handle parsing breadboard socket events
   */
  private attachParser () {
    this.socket.on('message', (d: string) => {
      this.emit('message', d)
      const data = JSON.parse(d)
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
