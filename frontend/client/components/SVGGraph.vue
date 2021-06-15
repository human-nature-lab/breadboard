<template>
  <div class="svg-container flex flex-grow-0 flex-shrink-0" ref="container">
    <svg ref="svg" viewBox="0 0 600 600" width="100%" height="100%">
      <g>
        <!-- Replace the edge with your own edge. This could be used to replace lines with Bezier curves or arrows.
                   Positioning has to be done manually. -->
        <slot name="edge" v-for="edge in graph.edges" :edge="edge">
          <line
              class="edge"
              :key="edge.id + '-line'"
              @click="edgeClick(edge, $event)"
              :stroke="evaluateProp('edgeStroke', edge)"
              :stroke-width="evaluateProp('edgeStrokeWidth', edge)"
              :stroke-opacity="evaluateProp('edgeStrokeOpacity', edge)"
              :x1="edge.source.x"
              :y1="edge.source.y"
              :x2="edge.target.x"
              :y2="edge.target.y">
          </line>
          <g :transform="`translate(${(edge.source.x + edge.target.x) / 2}, ${(edge.source.y + edge.target.y) / 2})`"
             :key="edge.id + '-label'"
             @click="edgeLabelClick(edge, $event)">
            <!-- Add an element at the center of the edge-->
            <slot name="edge-label" :edge="edge"/>
          </g>
        </slot>
        <g :transform="`translate(${node.x}, ${node.y})`" @click="nodeClick(node, $event)" v-for="node in graph.nodes" :key="node.id">
          <!-- Replace the entire node with your own node. Positioning is done automagically. This might be used to change the circle to an image or a square -->
          <slot
              name="node"
              :node="node">
            <circle
                v-bind="filteredObject(node.data, ignoredProps)"
                class="node"
                :class="{ ego : node.id === player.id }"
                :r="evaluateProp('nodeRadius', node)"
                :stroke="evaluateProp('nodeStroke', node)"
                :stroke-width="evaluateProp('nodeStrokeWidth', node)"
                :fill="evaluateProp('nodeFill', node)">
            </circle>
          </slot>
          <!-- Add something inside the node. This object will be positioned relative to the origin of the node (upper left)
                     and must be centered manually -->
          <slot name="node-content" :node="node" />
        </g>
      </g>
    </svg>
  </div>
</template>

<script lang="ts">
  import Vue, { PropOptions } from 'vue'
  import { forceSimulation, forceLink, forceManyBody, forceCenter, Simulation, forceRadial } from 'd3-force'
  import { Edge, Node, PlayerData } from '../../core/breadboard.types'
  import { Graph } from '../lib/graph'

  type ObjMapFunc<A, T> = (obj: A, player: PlayerData) => T
  type LayoutOptions = {
    chargeStrength?: number
    linkDistance?: number
    linkStrength?: number
    centerRepel?: number
    friction?: number
  }
  /**
   * The SVG Graph component shows the graph to the player and updates in real time. It allows you to easily react to
   * click events on nodes and edges within the graph, trivially transform the graph by modifying node sizes and colors,
   * customize the appearance of the graph with images and text and customize the force-directed layout algorithm without
   * modifying the component itself.
   */
  export default Vue.extend({
    name: 'SVGGraph',
    props: {
      /**
       * The player object sent up by breadboard
       * @type PlayerData
       */
      player: {
        type: Object as () => PlayerData,
        required: true
      },
      /**
       * The client graph object
       * @type Graph
       */
      graph: {
        type: Object as () => Graph,
        required: true
      },
      /**
       * The force-directed layout options
       * @type LayoutOptions
       */
      layout: {
        type: Object as () => LayoutOptions,
        default: () => ({
          linkDistance: 100,
          chargeStrength: -500,
          centerRepel: 500
        } as LayoutOptions)
      },
      /**
       * A boolean indicating whether or not the ego should be centered on the screen
       */
      centerEgo: {
        type: Boolean,
        default: true
      },
      /**
       * Change the color of each node border by supplying a new color or a mapping function
       * @type string | ObjMapFunc<Node, string>
       */
      nodeStroke: {
        type: [String, Function],
        default: 'black'
      },
      /**
       * Change the size of each node border by supplying a new size or a mapping function
       * @type number | ObjMapFunc<Node, number>
       */
      nodeStrokeWidth: {
        type: [Number, Function],
        default: 2
      },
      /**
       * Change the fill color of each node using a color string or a mapping function
       * @type string | ObjMapFunc<Node, string>
       */
      nodeFill: {
        type: [String, Function],
        default: 'grey'
      },
      /**
       * Change the size of each node using a number or a mapping function
       * @type number | ObjMapFunc<Node, number>
       */
      nodeRadius: {
        type: [Number, Function],
        default: 30
      },
      /**
       * Change the size of each edge using a number or a mapping function
       * @type number | ObjMapFunc<Edge, number>
       */
      edgeStrokeWidth: {
        type: [Number, Function],
        default: 2
      },
      /**
       * Change the opacity of each edge using a number or a mapping function. This could be used to show decay of edges.
       * @type number | ObjMapFunc<Edge, number>
       */
      edgeOpacity: {
        type: [Number, Function],
        default: 1
      },
      /**
       * Change the color of each edge using a number or a mapping function.
       * @type string | ObjMapFunc<Edge, string>
       */
      edgeStroke: {
        type: [String, Function],
        default: '#999'
      },
      /**
       * How much space to try to keep around the edge of the graph. Nodes will try to stay at least this far away from
       * the borders of the graph
       */
      graphPadding: {
        type: Number,
        default: 10
      },
      /**
       * Any properties that should not be assigned as attributes on the nodes
       */
      ignoredProps: {
        type: Array as () => String[],
        default: () => ['text', 'choices', 'x', 'y', 'timers', 'timerUpdatedAt']
      }
    },
    data () {
      return {
        width: 600,
        height: 600,
        listenerIds: {} as { [key: string]: number }
      }
    },
    created () {
      this.setupSimulation()
    },
    methods: {
      filteredObject (obj: { [key: string]: any }, keys: string[]) {
        let o = Object.assign(obj)
        for (const key of keys) {
          delete o[key]
        }
        return o
      },
      setupSimulation () {
        this.restartSimulation()
        this.graph.on('addNodes', (nodes: Node[]) => {
          for (const node of nodes) {
            // @ts-ignore
            node.isEgo = node.id === this.player.id
            if (this.centerEgo && node.id === this.player.id) {
              // @ts-ignore
              node.fx = this.center.x; node.fy = this.center.y
            }
          }
          console.log('addNodes')
          this.restartSimulation()
          console.log('graph', JSON.parse(JSON.stringify(this.graph)))
        })
        this.graph.on('addEdges', () => {
          console.log('addEdges')
          this.restartSimulation()
          this.updateLinkForce()
        })
        this.graph.on('removeEdges', () => {
          console.log('removeEdges')
          this.restartSimulation()
        })
        this.graph.on('removeNodes', () => {
          console.log('removeNodes')
          this.restartSimulation()
        })
        this.graph.on('updateEdges', (edges: Edge[]) => console.log('updateEdges', edges))
        this.graph.on('updateNodes', (nodes: Node[]) => console.log('updateNodes', nodes))
      },
      restartSimulation (updateForces = true) {
        console.log('restarting simulation')
        // @ts-ignore
        let simulation: Simulation = this.simulation
        if (!simulation) {
          simulation = forceSimulation()
          // @ts-ignore
          this.simulation = simulation
        }
        simulation.nodes(this.graph.nodes)
        if (updateForces) {
          simulation.stop()
          this.updateForces()
        }
        simulation.alpha(1).restart()
      },
      updateLinkForce () {
        const linkForce = forceLink(this.graph.edges)
        if (this.layout.linkDistance) {
          linkForce.distance(this.layout.linkDistance)
        }
        // @ts-ignore
        this.simulation.force('link', linkForce)
      },
      updateForces () {
        console.log('updating simulation forces')
        // @ts-ignore
        const simulation: Simulation = this.simulation

        this.updateLinkForce()

        const manyBody = forceManyBody()
        if (this.layout.chargeStrength) {
          manyBody.strength(this.layout.chargeStrength)
        }

        simulation.force('charge', manyBody)
          // .force('center', forceCenter(this.center.x, this.center.y))

        if (this.layout.centerRepel) {
          simulation.force('center-repel', forceRadial(this.layout.centerRepel, this.center.x, this.center.y))
        }

        if (this.layout.friction) {
          simulation.velocityDecay(this.layout.friction as number)
        }
      },
      evaluateProp (key: keyof Vue, obj: object): string | number {
        const res = typeof this[key] === 'function' ? this[key](obj, this.player) : this[key]
        // console.log('evaluating prop', key, 'returned', res, 'for', obj)
        return res
      },
      resize () {
        if (this.$refs.container instanceof Element) {
          // this.width = this.$refs.container.clientWidth || 600
          // this.height = this.$refs.container.clientHeight || 600
          this.restartSimulation(true)
        }
      },
      nodeClick (node: Node, e: MouseEvent) {
        if (e.isTrusted) {
          this.$emit('nodeClick', node, e)
        }
      },
      edgeClick (edge: Edge, e: MouseEvent) {
        if (e.isTrusted) {
          this.$emit('edgeClick', edge, e)
        }
      },
      edgeLabelClick (edge: Edge, e: MouseEvent) {
        if (e.isTrusted) {
          this.$emit('edgeLabelClick', edge, e)
        }
      }
    },
    computed: {
      linkDistance (): number {
        return (Math.min(this.width, this.height) / 2) - (2 * this.graphPadding) - 50
      },
      center (): {x: number, y: number} {
        return {
          x: this.width / 2,
          y: this.height / 2
        }
      }
    }
  })
</script>

<style lang="sass" scoped>
  .svg-container
    svg
      width: 100%
      height: 100%
</style>
