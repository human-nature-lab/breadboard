import { SimpleMap } from '../client/types'

export interface BreadboardConfig extends SimpleMap<string | number> {
  connectSocket: string,
  clientGraph: string,
  clientHtml: string
}

export interface VueLoadOpts {
  vueVersion?: string
  vuetifyVersion?: string
  useDev?: boolean
}

export interface PlayerChoice {
  name: string
  uid: string
}

export interface PlayerTimer {
  id: string
  duration: number
  appearance: 'success' | 'danger' | 'warn' | 'info'
  elapsed: number
  direction: 'down' | 'up'
  type: 'default' | 'currency' | 'percent'
  currencyAmount: number
  timerText: string
}

export interface PlayerData extends SimpleMap<any> {
  text: string
  choices: PlayerChoice[]
  timers?: PlayerTimer[]
}

export interface BreadboardMessages {
  on (event: 'graph', callback: (d: BreadboardGraphData) => any, context?: object): void
  on (event: 'message', callback: (ev: Event) => any, context?: object): void
  on (event: 'player', callback: (d: PlayerData) => any, context?: object): void
  on (event: 'style', callback: (d: string) => any, context?: object): void
  on (event: 'data', callback: (d: object) => any, context?: object): void
}

export interface Edge {
  id: number
  source: Node
  target: Node
  data: SimpleMap<any>
}

export interface Node {
  id: string
  x: number
  y: number
  data: SimpleMap<any>
}

export interface NodeData extends SimpleMap<any> {
  ai: number
  id: string
}

export interface LinkData extends SimpleMap<any> {
  id: number
  source: number
  target: number
  name: string
}

export type BreadboardGraphData = {
  nodes: NodeData[]
  links: LinkData[]
}

export interface GraphEvents {
  on (event: 'addNode', cb: (node: Node) => void, context?: object): void
  on (event: 'removeNode', cb: (node: Node) => void, context?: object): void
  on (event: 'addEdge', cb: (edge: Edge) => void, context?: object): void
  on (event: 'removeEdge', cb: (edge: Edge) => void, context?: object): void
  on (event: 'updateNode', cb: (node: Node) => void, context?: object): void
  on (event: 'updateEdge', cb: (edge: Edge) => void, context?: object): void
}
