import { BreadboardClass } from './breadboard'

declare global {
  interface Window {
    Breadboard: BreadboardClass
  }
}

export interface Exports<T> extends SimpleMap<T> {
  default: T
}

export type SimpleMap<T> = {
  [key: string]: T
}

export type Loadable = (core: BreadboardClass, config: BreadboardConfig) => Promise<any>

export interface BreadboardConfig extends SimpleMap<string | number> {
  connectSocket: string
  clientGraph: string
  clientHtml: string
  clientId: string
  assetsRoot: string
}

export interface VueLoadOpts {
  vueVersion?: string
  vuetifyVersion?: string
  mdiVersion?: string
  useDev?: boolean
  withVuetify?: boolean
  withCore?: boolean
}

export interface PlayerChoice {
  name: string
  uid: string
  params?: Record<string, any>
  class?: string
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
  order: number
}

export interface PlayerData extends SimpleMap<any> {
  id: string
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
  on (event: string, callback: (...d: any[]) => any, context?: object): void
}

export interface Edge {
  id: number
  source: Node
  target: Node
  data: SimpleMap<any>
}

export interface Node<T = any> {
  id: string
  x: number
  y: number
  data: T
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
  on (event: 'addNodes', cb: (nodes: Node[]) => void, context?: object): void
  on (event: 'removeNodes', cb: (nodes: Node[]) => void, context?: object): void
  on (event: 'addEdges', cb: (edges: Edge[]) => void, context?: object): void
  on (event: 'removeEdges', cb: (edges: Edge[]) => void, context?: object): void
  on (event: 'updateNodes', cb: (nodes: Node[]) => void, context?: object): void
  on (event: 'updateEdges', cb: (edges: Edge[]) => void, context?: object): void
}
