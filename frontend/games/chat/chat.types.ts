export interface ChatState {
  isEnabled: boolean
  recordEvents: boolean
  allowedRecipients: string[]
  maxLength: number
  messageBufferSize: number
  messages: Message[]
}

export interface Message {
  id: string
  sender: string
  recipients: string[]
  text: string
}