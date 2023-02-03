import { BreadboardClass } from '../core/breadboard'
import { Emitter } from 'goodish'
import { Graph } from '../client/lib/graph'

export interface Node {
  id: string
  [key: string]: any
}

interface Edge {}

enum GraphAction {
  ADD_NODE = 'addNode',
  REMOVE_NODE = 'removeNode',
  NODE_PROP_CHANGE = 'nodePropertyChanged',
  NODE_PROP_REMOVE = 'nodePropertyRemoved'
}

interface DataMessage {
  action: GraphAction,
  [key: string]: any
}

export class AdminGraph extends Emitter {

  public nodes: Node[] = []
  public edges: Edge[] = []
  private breadboard!: BreadboardClass

  constructor () {
    super()
    this.handleGraphChanges = this.handleGraphChanges.bind(this)
  }

  public attachToBreadboard (breadboard: BreadboardClass) {
    this.breadboard = breadboard
    breadboard.on('data', this.handleGraphChanges)
  }

  public releaseFromBreadboard () {
    if (this.breadboard) {
      this.breadboard.off('data', this.handleGraphChanges)
    }
  }

  private makeBlankNode (nodeId: string) {
    this.nodes.push({
      id: nodeId
    })
  }

  private removeNode (nodeId: string) {
    const index = this.nodes.findIndex(n => n.id = nodeId)
    if (index > -1) {
      this.nodes.splice(index, 1)
    }
  }

  private updateNode (nodeId: string, key: string, value: any) {
    for (const node of this.nodes) {
      if (node.id === nodeId) {
        if (value === null || value === undefined) {
          delete node[key]
        } else {
          try {
            value = JSON.parse(value)
          } catch (err) {
          } finally {
            node[key] = value
          }
        }
      }
    }
  }

  private handleGraphChanges (data: DataMessage) {
    switch (data.action) {
      case GraphAction.ADD_NODE:
        this.makeBlankNode(data.id)
        break
      case GraphAction.REMOVE_NODE:
        this.removeNode(data.id)
        break
      case GraphAction.NODE_PROP_CHANGE:
      case GraphAction.NODE_PROP_REMOVE:
        this.updateNode(data.id, data.key, data.value)
        break
    }

    if ([GraphAction.ADD_NODE, GraphAction.REMOVE_NODE, GraphAction.NODE_PROP_CHANGE, GraphAction.NODE_PROP_REMOVE].includes(data.action)) {
      this.emit('nodes', this.nodes)
    }

  }

}