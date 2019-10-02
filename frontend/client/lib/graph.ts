import { Emitter } from 'goodish'
import { BreadboardClass } from '../../core/breadboard'
import { BreadboardGraphData, Edge, GraphEvents, LinkData, Node, NodeData } from '../../core/breadboard.types'

export class Graph extends Emitter implements GraphEvents {

  public edges: Edge[] = []
  private edgeIdMap: Map<number, Edge> = new Map()
  public nodes: Node[] = []
  private nodeIdMap: Map<string, Node> = new Map()
  private breadboard!: BreadboardClass

  constructor () {
    super()
    this.handleGraphChanges = this.handleGraphChanges.bind(this)
  }

  attachToBreadboard (breadboard: BreadboardClass) {
    this.breadboard = breadboard
    breadboard.on('graph', this.handleGraphChanges)
  }

  releaseFromBreadboard () {
    if (this.breadboard) {
      this.breadboard.off('graph', this.handleGraphChanges)
    }
  }

  addNode (data: NodeData) {
    const node = {
      id: data.id,
      data,
      x: 0,
      y: 0,
    }
    this.nodes.push(node)
    this.nodeIdMap.set(node.id, node)
    this.emit('addNode', node)
  }

  addEdge (data: LinkData) {
    const source = this.nodeIdMap.get(data.sourceId)
    const target = this.nodeIdMap.get(data.targetId)
    if (source && target) {
      // delete data.source
      // delete data.target
      const edge = { id: data.id, source, target, data, index: this.edges.length - 1 }
      this.edges.push(edge)
      this.edgeIdMap.set(data.id, edge)
      this.emit('addEdge', edge)
    }
  }

  clearEdges () {
    this.edges = []
    this.edgeIdMap.clear()
    this.emit('clearEdges')
  }

  clearNodes () {
    this.nodes = []
    this.nodeIdMap.clear()
    this.emit('clearNodes')
  }

  removeEdge (edge: Edge): void
  removeEdge (edgeId: number): void
  removeEdge (edgeOrId: Edge | number): void {
    const edgeId = typeof edgeOrId === 'object' ? edgeOrId.id : edgeOrId
    const index = this.edges.findIndex(e => e.id === edgeId)
    if (index !== -1) {
      const edge = this.edges.splice(index, 1)[0]
      this.edgeIdMap.delete(edgeId)
      this.emit('removeEdge', edge)
    }
  }

  removeNode (node: Node): void
  removeNode (nodeId: string): void
  removeNode (nodeOrId: Node | string): void {
    const nodeId = typeof nodeOrId === 'object' ? nodeOrId.id : nodeOrId
    const index = this.nodes.findIndex(n => n.id === nodeId)
    if (index !== -1) {
      const node = this.nodes.splice(index, 1)[0]
      this.nodeIdMap.delete(nodeId)
      this.emit('removeNode', node)
    }
  }

  /**
   * Diff algorithm:
   *  - Check if all nodes were removed
   *  - Check if all edges were removed
   *  - Check for removed nodes
   *  - Check if any nodes need updated
   *  - Check for removed edges
   *  - Check if any edges need updated
   *  - Check for new nodes
   *  - Check for new edges
   */
  handleGraphChanges (data: BreadboardGraphData) {
    if (!data.nodes || !data.nodes.length) {
      this.clearNodes()
      this.clearEdges()
      return
    }
    if (!data.links || !data.links.length) {
      this.clearEdges()
    }

    for (const node of this.nodes) {
      // Check for old nodes
      const existingNode = data.nodes.find(n => n.id === node.id)
      if (!existingNode) {
        this.removeNode(node)
      } else if (existingNode !== node.data) {
        // TODO: update the existing node
        node.data = existingNode
        // this.emit('updateNode', node)
      }
    }

    for (const edge of this.edges) {
      const existingEdge = data.links.find(l => l.id === edge.id)
      if (!existingEdge) {
        this.removeEdge(edge)
      } else if (existingEdge !== edge.data) {
        // TODO: Update the existing edge
        // this.emit('updateEdge', edge)
      }
    }

    for (const node of data.nodes) {
      const nodeAlreadyExists = this.nodeIdMap.has(node.id)
      if (!nodeAlreadyExists) {
        this.addNode(node)
      }
    }

    for (const link of data.links) {
      const edgeAlreadyExists = this.edgeIdMap.has(link.id)
      if (!edgeAlreadyExists) {
        link.sourceId = data.nodes[link.source].id
        link.targetId = data.nodes[link.target].id
        this.addEdge(link)
      }
    }

  }

}
