<template>
  <div class="svg-container flex flex-grow-0 flex-shrink-0" ref="container">
    <svg ref="svg" viewBox="0 0 600 600" width="100%" height="100%">
      <g>
        <slot
            name="edge"
            v-for="edge in graph.edges"
            v-bind:edge="edge">
          <line
              class="edge"
              :key="edge.id"
              :stroke="evaluateProp('edgeStroke', edge)"
              :stroke-width="evaluateProp('edgeStrokeWidth', edge)"
              :stroke-opacity="evaluateProp('edgeStrokeOpacity', edge)"
              :x1="edge.source.x"
              :y1="edge.source.y"
              :x2="edge.target.x"
              :y2="edge.target.y">
          </line>
          <g :transform="`translate(${(edge.source.x + edge.target.x) / 2}, ${(edge.source.y + edge.target.y) / 2})`">
            <slot name="edge-label" v-bind:edge="edge"/>
          </g>
        </slot>
        <slot
            name="node"
            v-bind:node="node"
            v-for="node in graph.nodes">
          <g :transform="`translate(${node.x}, ${node.y})`">
            <circle
                v-bind="node.data"
                :key="node.id"
                class="node"
                :class="{ ego : node.id === player.id }"
                :r="evaluateProp('nodeRadius', node)"
                :stroke="evaluateProp('nodeStroke', node)"
                :stroke-width="evaluateProp('nodeStrokeWidth', node)"
                :fill="evaluateProp('nodeFill', node)">
            </circle>
            <slot name="node-content" v-bind:node="node" />
          </g>
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
      layout: <PropOptions<LayoutOptions>>{
        type: Object,
        default: () => ({
          linkDistance: 100,
          chargeStrength: -500,
          centerRepel: 500
        })
      },
      centerEgo: {
        type: Boolean,
        default: true
      },
      nodeStroke: <PropOptions<string | ObjMapFunc<Node, string>>>{
        type: [String, Function],
        default: 'black'
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
        default: () => ['text', 'choices', 'x', 'y', 'timers']
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
    },
  })
</script>

<style lang="sass" scoped>
  .svg-container
    svg
      width: 100%
      height: 100%
</style>
