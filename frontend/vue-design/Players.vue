<template>
  <div class="players flex h-full overflow-hidden">
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
      <template v-if="selectedNode">
        <vue-json-pretty :data="filteredNode" :deep="1" />
        <div class="client-content" v-html="selectedNode.text" />
        <PlayerChoices :player="selectedNode" />
      </template>
      <div v-else>
        Select a player to view their data...
      </div>
    </div>
  </div>
</template>

<script lang="ts">
  import Vue, { PropOptions } from 'vue'
  // @ts-ignore
  import VueJsonPretty from 'vue-json-pretty'
  import { AdminGraph, Node } from './AdminGraph'
  import PlayerChoices from '../client/components/PlayerChoices.vue'

  export default Vue.extend({
    name: 'App',
    components: { VueJsonPretty, PlayerChoices },
    props: {
      propBlacklist: <PropOptions<string[]>>{
        type: Array,
        default: () => ['text', 'choices']
      }
    },
    data () {
      return {
        expression: '',
        graph: new AdminGraph(),
        selectedNode: null as Node | null | undefined
      }
    },
    created () {
      this.graph.attachToBreadboard(window.Breadboard)
      window.Breadboard.on('graph-select', this.selectNode)
    },
    beforeDestroy () {
      this.graph.releaseFromBreadboard()
      window.Breadboard.off('graph-select', this.selectNode)
    },
    methods: {
      selectNode (nodeId: string, emit = false) {
        console.log('select', nodeId, this.selectedNode)
        this.selectedNode = this.selectedNode && this.selectedNode.id === nodeId ? null : this.graph.nodes.find(n => n.id === nodeId)
        if (emit) {
          window.Breadboard.emit('player-select', this.selectedNode ? nodeId : null)
        }
      }
    },
    computed: {
      nodes (): Node[] {
        const exp = this.expression
        const operators = ['=', '<', '>', '~']
        let operator = '='
        for (const op of operators) {
          if (exp.includes(op)) {
            operator = op
            break
          }
        }
        if (exp.includes(operator)) {
          const parts = exp.split(operator)
          const expected = parts[1].trim()
          const query = parts[0].split('.').map(k => k.trim())
          console.log(query, expected)
          return this.graph.nodes.filter(n => {
            let v: any = n
            for (let i = 0; i < query.length; i++) {
              const key = query[i]
              if (v !== null && typeof v === 'object') {
                v = v[key]
              } else {
                return false
              }
            }
            let res: boolean = false
            switch (operator) {
              case '=':
                res = v == expected
                break 
              case '<':
                res = v < expected
                break
              case '>':
                res = v > expected
                break
              case '~': {
                if (typeof v !== 'string') {
                  v = v.toString()
                }
                res = v.toLowerCase().includes(expected.toLowerCase())
                break
              }
            }
            console.log(n, v, operator, res)
            return res
          })
        } else if (exp.length) {
          return this.graph.nodes.filter(n => n.id && n.id.includes(exp))
        } else {
          return this.graph.nodes
        }
      },
      filteredNode (): Node | null {
        if (this.selectedNode) {
          const d: Node = { id: this.selectedNode.id }
          for (const key in this.selectedNode) {
            if (!this.propBlacklist.includes(key)) {
              d[key] = this.selectedNode[key]
            }
          }
          return d
        } else {
          return null
        }
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
  .m-1
    margin: .8em
  .m-2
    margin: 1em
  .m-3
    margin: 1.2em
  .p-1
    padding: .8em
  .p-2
    padding: 1em
  .p-3
    padding: 1.2em
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