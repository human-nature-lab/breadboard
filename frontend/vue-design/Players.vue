<template>
  <div class="players flex m-2 h-full overflow-hidden">
    <div class="flex-grow-0 h-full overflow-auto flex-shrink-0 p-1">
      <h5>Players {{numFilteredNodes}} / {{totalNodes}}</h5>
      <input type="text" v-model="expression" placeholder="Filter..." />
      <div v-for="node in nodes" :key="node.id" 
        class="player p-1" 
        :class="{'selected': (selectedNode && node.id === selectedNode.id)}"
        @click="selectNode(node.id, true)">
        {{node.id}}
      </div>
    </div>
    <div class="data p-1 h-full overflow-auto">
      <pre v-if="selectedNode">
        <code>{{selectedNode}}</code>
      </pre>
      <div v-else>
        Select a player to view their data...
      </div>
    </div>
  </div>
</template>

<script lang="ts">
  import Vue, { PropOptions } from 'vue'
  import { AdminGraph, Node } from './AdminGraph'

  export default Vue.extend({
    name: 'App',
    data () {
      return {
        expression: '',
        graph: new AdminGraph(),
        selectedNode: null as Node | null | undefined
      }
    },
    created () {
      this.graph.attachToBreadboard(window.Breadboard)
      window.Breadboard.on('player-select', this.selectNode)
    },
    beforeDestroy () {
      this.graph.releaseFromBreadboard()
      window.Breadboard.off('player-select', this.selectNode)
    },
    methods: {
      selectNode (nodeId: string, emit = false) {
        this.selectedNode = this.graph.nodes.find(n => n.id === nodeId)
        if (emit) {
          window.Breadboard.emit('player-select', nodeId)
        }
      }
    },
    computed: {
      nodes (): Node[] {
        if (this.expression)
        // TODO: Filter nodes
        return this.graph.nodes
      },
      totalNodes (): number {
        return this.graph.nodes.length
      },
      numFilteredNodes (): number {
        return this.nodes.length
      }
    }
  })
</script>

<style lang="sass" scoped>
  .overflow-auto
    overflow: auto
  .overflow-hidden
    overflow: hidden
  .h-full
    height: 100%
  .m-2
    margin: 1.3em
  .p-1
    margin: 1em
  .p-2
    margin: 1.3em
  .flex
    display: flex
    flex: 1 1 auto
  .flex-grow-0
    flex-grow: 0
  .flex-shrink-0
    flex-shrink: 0
  .selected
    background-color: lightgrey
</style>