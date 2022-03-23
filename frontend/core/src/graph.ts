import { Emitter } from 'goodish'
import { BreadboardClass } from './breadboard'
import { BreadboardGraphData, Edge, GraphEvents, LinkData, Node, NodeData } from './breadboard.types'
import { isEqual } from './isEqual'

export class Graph extends Emitter implements GraphEvents {

  public edges: Edge[] = []
  private edgeIdMap: Map<number, Edge> = new Map()
  public nodes: Node[] = []
  private nodeIdMap: Map<string, Node> = new Map()
  private breadboard!: BreadboardClass

  constructor() {
    super()
    this.handleGraphChanges = this.handleGraphChanges.bind(this)
  }

  attachToBreadboard(breadboard: BreadboardClass) {
    this.breadboard = breadboard
    breadboard.on('graph', this.handleGraphChanges)
  }

  releaseFromBreadboard() {
    if (this.breadboard) {
      this.breadboard.off('graph', this.handleGraphChanges)
    }
  }

  addNodes(data: NodeData[]) {
    const addedNodes = []
    for (const datum of data) {
      const node = {
        id: datum.id,
        data: datum,
        x: 0,
        y: 0,
      }
      this.nodes.push(node)
      this.nodeIdMap.set(node.id, node)
      addedNodes.push(node)
    }
    this.emit('addNodes', addedNodes)
  }

  addEdges(data: LinkData[]) {
    let addedEdges = []
    for (const datum of data) {
      const source = this.nodeIdMap.get(datum.sourceId)
      const target = this.nodeIdMap.get(datum.targetId)
      if (source && target) {
        // delete datum.source
        // delete datum.target
        const edge = { id: datum.id, source, target, data: datum, index: this.edges.length - 1 }
        this.edges.push(edge)
        this.edgeIdMap.set(datum.id, edge)
        addedEdges.push(edge)
      }
    }
    this.emit('addEdges', addedEdges)
  }

  clearEdges() {
    this.edges = []
    this.edgeIdMap.clear()
    this.emit('clearEdges')
  }

  clearNodes() {
    this.nodes = []
    this.nodeIdMap.clear()
    this.emit('clearNodes')
  }

  removeEdges(edges: Edge[]): void
  removeEdges(edgeIds: number[]): void
  removeEdges(edgesOrIds: (Edge | number)[]): void {
    const removedEdges = []
    for (const edgeOrId of edgesOrIds) {
      const edgeId = typeof edgeOrId === 'object' ? edgeOrId.id : edgeOrId
      const index = this.edges.findIndex(e => e.id === edgeId)
      if (index !== -1) {
        const edge = this.edges.splice(index, 1)[0]
        this.edgeIdMap.delete(edgeId)
        removedEdges.push(edge)
      }
    }
    this.emit('removeEdges', removedEdges)
  }

  removeNodes(nodes: Node[]): void
  removeNodes(nodeIds: string[]): void
  removeNodes(nodesOrIds: (Node | string)[]): void {
    const removedNodes = []
    for (const nodeOrId of nodesOrIds) {
      const nodeId = typeof nodeOrId === 'object' ? nodeOrId.id : nodeOrId
      const index = this.nodes.findIndex(n => n.id === nodeId)
      if (index !== -1) {
        const node = this.nodes.splice(index, 1)[0]
        this.nodeIdMap.delete(nodeId)
        removedNodes.push(node)
      }
    }
    this.emit('removeNodes', removedNodes)
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
  handleGraphChanges(data: BreadboardGraphData) {
    if (!data.nodes || !data.nodes.length) {
      this.clearNodes()
      this.clearEdges()
      return
    }
    if (!data.links || !data.links.length) {
      this.clearEdges()
    }

    const removedNodes = []
    const updatedNodes = []
    for (const node of this.nodes) {
      // Check for old nodes
      const existingNodeData = data.nodes.find(n => n.id === node.id)
      if (!existingNodeData) {
        removedNodes.push(node)
      } else if (!isEqual(node.data, existingNodeData, ['timers', 'timerUpdatedAt'])) {
        // TODO: Why is this happening?
        // console.log('node change')
        // console.log('old node',  JSON.stringify(node.data))
        // console.log('new node', JSON.stringify(existingNodeData))
        node.data = existingNodeData
        // console.log('updated node', JSON.stringify(node.data))
        updatedNodes.push(node)
      }
    }
    if (removedNodes.length) this.removeNodes(removedNodes)
    if (updatedNodes.length) this.emit('updateNodes', updatedNodes)

    const removedEdges = []
    const updatedEdges = []
    for (const edge of this.edges) {
      const existingEdgeData = data.links.find(l => l.id === edge.id)
      if (!existingEdgeData) {
        removedEdges.push(edge)
      } else if (!isEqual(existingEdgeData, edge.data)) {
        edge.data = existingEdgeData
        updatedEdges.push(edge)
      }
    }
    if (removedEdges.length) this.removeEdges(removedEdges)
    if (updatedEdges.length) this.emit('updateEdges', updatedEdges)

    const addedNodes = []
    for (const node of data.nodes) {
      const nodeAlreadyExists = this.nodeIdMap.has(node.id)
      if (!nodeAlreadyExists) {
        addedNodes.push(node)
      }
    }
    if (addedNodes.length) this.addNodes(addedNodes)

    const addedEdges = []
    for (const link of data.links) {
      const edgeAlreadyExists = this.edgeIdMap.has(link.id)
      if (!edgeAlreadyExists) {
        link.sourceId = data.nodes[link.source].id
        link.targetId = data.nodes[link.target].id
        addedEdges.push(link)
      }
    }
    if (addedEdges.length) this.addEdges(addedEdges)

  }
}
