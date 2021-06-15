<template>
  <v-flex v-if="player && player.crossword">
    <Crossword 
      v-model="solution"
      :active="active"
      :direction="direction"
      :size="size"
      @update:cell="onCellUpdate"
      @update:active="$emit('update:active', $event)"
      @update:direction="$emit('update:direction', $event)"
      :crossword="player.crossword" />
  </v-flex>
</template>

<script lang="ts">
  import Vue from 'vue'
  import debounce from 'lodash/debounce'
  import { Crossword, DIRECTION, Label, Position } from './crossword.types'

  interface Player {
    crossword: Crossword
    lastBatchId: number
  }

  type CellUpdate = {
    val: string
    row: number
    col: number
  }

  type Batch = {
    id: number
    updates: CellUpdate[]
  }

  /**
   * A synchronized crossword puzzle that can be worked on by multiple players.
   */
  export default Vue.extend({
    name: 'BreadboardCrossword',
    props: {
      /**
       * A reference to the player node
       */
      player: {
        type: Object as () => Player,
        required: true
      },
      /**
       * The name of the event used to pass data for this crossword puzzle
       */
      eventKey: {
        type: String,
        default: 'crossword'
      },
      /**
       * The name of the property where crossword data is stored on the player.
       */
      propName: {
        type: String,
        default: 'crossword'
      },
      /**
       * How frequently to send data to the server
       */
      throttleRate: {
        type: Number,
        default: 2000
      },
      maxWait: {
        type: Number,
        default: 5000
      },
      /**
       * Whether or not to use client-side interpolation
       */
      clientSideInterpolation: {
        type: Boolean,
        default: true
      },
      /**
       * Which cell is active
       */
      active: {
        type: Object as () => Position,
        required: true
      },
      /**
       * Which direction they keyboard is typing in
       */
      direction: {
        type: String as () => DIRECTION,
        required: true
      },
      size: {
        type: Number,
        required: true
      }
    },
    data () {
      return {
        removeInitWatch: null as Function | null,
        isInitialized: false,
        updateQueue: [] as CellUpdate[],
        sentBatches: [] as Batch[],
        batchId: 0
      }
    },
    created () {
      this.initialize()
      this.removeInitWatch = this.$watch('player.' + this.propName, this.initialize)
      // @ts-ignore
      this.throttledUpdates = debounce(this.sendUpdates.bind(this), this.throttleRate, {
        maxWait: this.maxWait
      })
      this.$watch('player.' + this.propName + '.lastBatchId', () => {
        // @ts-ignore
        const state = this.player[this.propName]

        // Remove processed batches by iterating in reverse
        for (let i = this.sentBatches.length - 1; i >= 0; i--) {
          if (this.sentBatches[i].id <= state.lastBatchId) {
            this.sentBatches.splice(i, 1)
          }
        }
      })
    },
    beforeDestroy () {
      // Push down changes before the crossword gets destroyed
      this.sendUpdates()
    },
    computed: {
      solution: {
        get (): string[][] {
          let solution: string[][] = []
          if (!this.player || !this.player.crossword || !this.player.crossword.solution) {
            return solution
          }

          solution = this.player.crossword.solution
          if (this.clientSideInterpolation) {
            // Interpolate sent batches
            for (const batch of this.sentBatches) {
              for (const up of batch.updates) {
                solution[up.row][up.col] = up.val
              }
            }
            // Interpolate queued updates
            for (const up of this.updateQueue) {
              solution[up.row][up.col] = up.val
            }
          }
          
          return solution
        },
        set (val: string[][]) {
          this.player.crossword.solution = val
        }
      }
    },
    methods: {
      initialize () {
        // @ts-ignore
        const state: Crossword = this.player[this.propName]

        if (!state) return

        if (this.isInitialized) {
          if (this.removeInitWatch) {
            this.removeInitWatch()
            this.removeInitWatch = null
          }
          return
        }
        this.batchId = state.lastBatchId
        this.isInitialized = true
      },
      onCellUpdate (val: string, pos: Position): void {
        this.updateQueue.push({
          val: val,
          row: pos.row,
          col: pos.col
        })
        // @ts-ignore
        this.throttledUpdates()
      },
      sendUpdates (): void {
        if (this.updateQueue.length) {
          this.batchId++
          const batch = {
            id: this.batchId,
            updates: this.updateQueue
          }
          window.Breadboard.send(this.eventKey, batch)
          this.sentBatches.push(batch)
          this.updateQueue = []
        }
      },
      initSolution (): void {
        if (this.removeInitWatch) {
          this.solution = new Array(this.player.crossword.size.rows).fill(0).map(() => new Array(this.player.crossword.size.cols).fill(''))
          this.removeInitWatch()
        }
      },
      setActiveFromLabel (label: Label): void {
        this.active.row = label.row
        this.active.col = label.col
      }
    }
  })
</script>

<style lang="sass">
  
</style>