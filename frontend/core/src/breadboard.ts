import { http } from './http'
import { Mutex } from 'async-mutex'
import { Emitter } from 'goodish'
import { BreadboardConfig, BreadboardMessages, Loadable, SimpleMap } from './breadboard.types'
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
      return this.config!
    } finally {
      release()
    }
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
      if (url[0] !== '/') {
        url = '/' + url
      }
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

  // Load a module
  async load (loader: Loadable) {
    const config = await this.loadConfig()
    return loader(this, config)
  }
   
  /**
   * Get an instance of the client graph
   */
  public async getGraph () {
    const v = (await import('./graph'))
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


export const Breadboard = new BreadboardClass()
window.Breadboard = Breadboard
