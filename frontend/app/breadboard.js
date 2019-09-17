import { http } from './util/http'
import { Mutex } from 'async-mutex'

export class Breadboard {

  constructor () {
    this.socket = null
    this.state = null
    this.socketMutex = new Mutex()
    this.stateMutex = new Mutex()
  }

  async loadConfig () {
    const release = await this.stateMutex.acquire()
    if (this.state) {
      release()
      return this.state
    }
    try {
      const res = await http.get('state')
      this.state = res.json()
     } finally {
      release()
    }
    return this.state
  }

  /**
   * Returns the websocket used to talk to the server.
   * @returns {Promise<WebSocket>}
   */
  async connect () {
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

}
