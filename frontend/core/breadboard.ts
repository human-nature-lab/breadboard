import 'core-js'
import 'regenerator-runtime'
import { http } from './http'
import { Mutex } from 'async-mutex'

type BreadboardState = {
  [key: string]: string | number,
  connectSocket: string,
  clientGraph: string,
  clientHtml: string
}

export class BreadboardClass {

  private socket!: WebSocket
  private state: BreadboardState
  private socketMutex = new Mutex()
  private stateMutex = new Mutex()

  /**
   * Load the client config.
   * @returns {Promise<object>}
   */
  async loadConfig (): Promise<BreadboardState> {
    const release = await this.stateMutex.acquire()
    if (this.state) {
      release()
      return this.state
    }
    try {
      const res = await http.get<BreadboardState>('state')
      this.state = await res.json()
     } finally {
      release()
    }
    return this.state
  }

  /**
   * Returns the websocket that's connected to the server.
   * @returns {Promise<WebSocket>}
   */
  async connect (): Promise<WebSocket> {
    const release = await this.socketMutex.acquire()
    if (this.socket) {
      release()
      return this.socket
    }
    try {
      const config = await this.loadConfig()
      this.socket = new WebSocket(config.connectSocket)
    } finally {
      release()
    }
    return this.socket
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
      script.onload = () => resolve()
      script.onerror = reject
      document.body.appendChild(script)
    })
  }

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

}

// @ts-ignore export globally
window.Breadboard = new BreadboardClass()
