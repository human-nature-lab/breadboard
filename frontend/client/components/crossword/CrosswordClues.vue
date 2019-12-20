<template>
  <v-layout :column="!row" class="clues align-start">
    <v-flex class="down pad flex-grow-0 w-full" :class="{'max-h-50': !row}">
      <v-container class="h-full">
        <h3 class="clue-type">
          Down <ClueLock v-if="player" :unlocked="player.unlockedDown" />
        </h3>
        <div class="clues-container h-full" v-if="downClues.length">
          <div class="clue" 
            :class="{active: isActive(clue)}"
            v-for="clue in downClues" 
            :key="clue.label + '-down'" 
            @click="selectClue(DIRECTION.DOWN, clue.label)">
            <b>{{clue.label}}.</b> <span v-html="clue.text" />
          </div>
        </div>
        <div class="clues-container" v-else>
          No down clues right now
        </div>
      </v-container>
    </v-flex>
    <v-flex class="across pad w-full" :class="{'max-h-50': !row}">
      <v-container class="h-full">
        <h3 class="clue-type">
          Across <ClueLock v-if="player" :unlocked="player.unlockedAcross" />
        </h3>
        <div class="clues-container h-full" v-if="acrossClues.length">
          <div class="clue" 
            :class="{active: isActive(clue)}"
            v-for="clue in acrossClues" 
            :key="clue.label + '-across'" 
            @click="selectClue(DIRECTION.ACROSS, clue.label)">
            <b>{{clue.label}}.</b> <span v-html="clue.text" />
          </div>
        </div>
        <div class="clues-container" v-else>
          No across clues right now 
        </div>
      </v-container>
    </v-flex>
  </v-layout>
</template>

<script lang="ts">
  import Vue, { VueConstructor } from 'vue'
  import { Clue, DIRECTION, Label, Position } from './crossword.types'

  export default Vue.extend({
    name: 'CrosswordClues',
    props: {
      player: {
        type: Object as () => {data: { unlockedDown: boolean, unlockedAcross: boolean }}
      },
      labels: {
        type: Array as () => Label[],
        required: true
      },
      clues: {
        type: Array as () => Clue[],
        required: true
      },
      value: {
        type: Object as () => Position,
        // required: true
      },
      direction: {
        type: String as () => DIRECTION,
        // required: true
      },
      row: {
        type: Boolean,
        default: false
      }
    },
    data () {
      return {
        labelMap: new Map(),
        DIRECTION
      }
    },
    created () {
      for (const label of this.labels) {
        this.labelMap.set(label.text, label)
      }
    },
    computed: {
      downClues (): Clue[] {
        return this.clues.filter(c => c.type === DIRECTION.DOWN)
      },
      acrossClues (): Clue[] {
        return this.clues.filter(c => c.type === DIRECTION.ACROSS)
      }
    },
    methods: {
      selectClue (dir: DIRECTION, labelId: string) {
        const label = this.labelMap.get(labelId)
        this.$emit('input', {
          row: label.row,
          col: label.col
        })
        this.$emit('update:direction', dir)
      },
      isActive (clue: Clue): boolean {
        // TODO: This needs to account for spacial location of words. Should just be computed ahead of time probably.
        return false
        if (this.value.row === -1 || this.value.col === -1) return false
        const label = this.labelMap.get(clue.label)
        return label.row === this.value.row || label.col === this.value.col
      }
    }
  })
</script>

<style lang="sass" scoped>
  .pad
    margin-bottom: 10px
  .clues-container
    overflow: auto
    margin-bottom: 5px
  .clue-type
    border-bottom: 1px solid black
    // text-decoration: underline
  .clue
    transition: all 0.3s
    cursor: pointer
    &:hover, &.active
      background: lightgrey
</style>