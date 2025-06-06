import { Hook } from "./hook"

// TODO: add reconnect logic w/ exponential backoff
export class Socket {
  private socket?: WebSocket
  state = 'closed'
  private listeners: ((event: MessageEvent) => void)[] = []
  onOpen = new Hook()

  constructor(public url: string) {
    this.connect()
  }

  connect() {
    this.socket = new WebSocket(this.url)
    this.socket.addEventListener('open', () => {
      this.state = 'open'
      this.onOpen.emit()
    })
    this.socket.addEventListener('close', () => {
      this.state = 'closed'
    })
    this.socket.addEventListener('error', (event) => {
      this.state = 'error'
    })
    this.socket.onmessage = (event) => {
      console.log('onmessage', event.data)
      for (const listener of this.listeners) {
        listener(event)
      }
    }
  }

  onmessage(listener: (event: MessageEvent) => void) {
    this.listeners.push(listener)
  }

  send(data: string | ArrayBufferLike | Blob | ArrayBufferView) {
    if (!this.socket) {
      return
    }
    this.socket.send(data)
  }

  close() {
    if (!this.socket) {
      return
    }
    this.socket.close()
  }
}