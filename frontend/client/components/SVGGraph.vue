<template>
  <div class="svg-container flex flex-grow-0 flex-shrink-0" v-resize="resize" ref="container">
    <svg ref="svg" viewBox="0 0 600 600" width="100%" height="100%">
      <g>
        <slot
            name="edge"
            v-for="edge in graph.edges">
          <line
              class="edge"
              :stroke="evaluateProp('edgeStroke', edge)"
              :stroke-width="evaluateProp('edgeStrokeWidth', edge)"
              :x1="edge.source.x"
              :y1="edge.source.y"
              :x2="edge.target.x"
              :y2="edge.target.y">
            <slot name="edge-content" />
          </line>
        </slot>
        <slot
            name="node"
            v-bind:node="node"
            v-for="node in graph.nodes">
          <circle
              v-bind="node.data"
              class="node"
              :class="{ ego : node.id === player.id }"
              :r="evaluateProp('nodeRadius', node)"
              :cx="node.x"
              :cy="node.y"
              :fill="evaluateProp('nodeFill', node)">
            <slot node="node-content" />
          </circle>
        </slot>
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
  export default Vue.extend({
    name: 'SVGGraph',
    props: {
      player: {
        type: Object as () => PlayerData,
        required: true
      },
      graph: {
        type: Object as () => Graph,
        required: true
      },
      layout: {
        type: Object as () => LayoutOptions,
        default: () => ({
          chargeStrength: -10000,
          linkStrength: 10,
          centerRepel: 10,
          friction: 0.8
        } as LayoutOptions)
      },
      nodeStroke: <PropOptions<string | ObjMapFunc<Node, string>>>{
        type: [String, Function],
        default: 'lightblue'
      },
      nodeStrokeWidth: <PropOptions<number | ObjMapFunc<Node, number>>>{
        type: [Number, Function],
        default: 2
      },
      nodeFill: <PropOptions<string | ObjMapFunc<Node, string>>>{
        type: [String, Function],
        default: 'grey'
      },
      nodeRadius: <PropOptions<number | ObjMapFunc<Node, number>>>{
        type: [Number, Function],
        default: 30
      },
      edgeStrokeWidth: <PropOptions<number | ObjMapFunc<Edge, number>>>{
        type: [Number, Function],
        default: 2
      },
      edgeOpacity: <PropOptions<number | ObjMapFunc<Edge, number>>>{
        type: [Number, Function],
        default: 1
      },
      edgeStroke: <PropOptions<string | ObjMapFunc<Edge, string>>>{
        type: [String, Function],
        default: '#999'
      },
      graphPadding: {
        type: Number,
        default: 10
      },
      ignoredProps: {
        type: Array as () => String[],
        default: () => ['text', 'choices', 'x', 'y']
      }
    },
    data () {
      return {
        width: 600,
        height: 600
      }
    },
    created () {
      this.setupSimulation()
    },
    methods: {
      setupSimulation () {
        console.log('nodes', this.graph.nodes.length)
        const simulation = forceSimulation(this.graph.nodes)
        // @ts-ignore
        this.simulation = simulation
        this.updateForces()

        this.graph.on('addNode', (node: Node) => {
          // @ts-ignore
          node.isEgo = node.id === this.player.id
          console.log('addNode', node)
          this.restartSimulation()
        })
        this.graph.on('addEdge', () => {
          console.log('addEdge')
          this.restartSimulation()
        })
        this.graph.on('removeEdge', () => {
          console.log('removeEdge')
          this.restartSimulation()
        })
        this.graph.on('removeNode', () => {
          console.log('removeNode')
          this.restartSimulation()
        })
        this.graph.on('updateEdge', () => console.log('updateEdge'))
        this.graph.on('updateNode', () => console.log('updateNode'))
      },
      restartSimulation () {
        // @ts-ignore
        const simulation: Simulation = this.simulation
        simulation.stop()
        const nodes = simulation.nodes(this.graph.nodes)
        const playerId = this.player.id
        for (const node of nodes) {
          if (node.id === playerId) {
            node.fixed = true
          }
        }
        simulation.force('link').links(this.graph.edges);
        simulation.alpha(1).restart()
      },
      updateForces () {
        // @ts-ignore
        const simulation: Simulation = this.simulation
        simulation.force('link', forceLink(this.graph.edges).distance(this.layout.linkDistance || this.linkDistance))
          .force('charge', forceManyBody().strength(this.layout.chargeStrength as number))
          // .force('center-repel', forceRadial(this.layout.centerRepel as number, this.width, this.height))
          .force('center', forceCenter(this.width / 2, this.height / 2))
          .velocityDecay(this.layout.friction as number)
      },
      evaluateProp (key: keyof Vue, node: Node): string | number {
        // console.log('evaluating', key)
        return typeof this[key] === 'function' ? this[key](node, this.player) : this[key]
      },
      resize () {
        if (this.$refs.container instanceof Element) {
          this.width = this.$refs.container.clientWidth || 600
          this.height = this.$refs.container.clientHeight || 600
          this.updateForces()
          this.restartSimulation()
        }
      }
    },
    computed: {
      linkDistance (): number {
        return (Math.min(this.width, this.height) / 2) - (2 * this.graphPadding) - 50
      }
    },
  })
</script>

<style lang="sass" scoped>
  .svg-container
    svg
      width: 100%
      height: 100%
</style>
