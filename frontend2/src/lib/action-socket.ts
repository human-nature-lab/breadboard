import { Socket } from "./socket"

export class ActionSocket extends Socket {
  private listeners: Map<string, (data: any) => void> = new Map()

  constructor(url: string) {
    super(url)
    this.onmessage(this.handleMessage)
  }

  private handleMessage(event: MessageEvent) {
    const data = JSON.parse(event.data)
    if (this.listeners.has(data.action)) {
      this.listeners.get(data.action)(data.data)
    }
  }

  on(action: string, listener: (data: any) => void) {
    super.onmessage((event) => {
      const data = JSON.parse(event.data)
      if (data.action === action) {
        listener(data.data)
      }
    })
  }

  public send(action: string, data: any) {
    const payload = { ...data, action }
    super.send(JSON.stringify(payload))
  }
}