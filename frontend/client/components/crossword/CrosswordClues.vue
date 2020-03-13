<template>
  <v-layout :column="!row" class="clues align-start h-full">
    <v-flex class="down pad w-full" >
      <v-container>
        <v-layout class="clue-type">
          <v-flex class="flex-grow-0">
            <h3>Down</h3>
          </v-flex>
          <v-flex class="flex-grow-0 clue-lock">
            <ClueLock v-if="player" :unlocked="player.unlockedDown" />
          </v-flex>
          <ClueUnlock ref="downUnlock" @unlock="$emit('unlock', $event)" type="down" :unlocked="player.unlockedDown" />
        </v-layout>
        <div class="clues-container" v-if="downClues.length && player.unlockedDown">
          <div class="clue" 
            :class="{active: isActive(clue)}"
            v-for="clue in downClues" 
            :key="clue.label + '-down'" 
            @click="selectClue(DIRECTION.DOWN, clue.label)">
            <b>{{clue.label}}.</b> <span v-html="clue.text" />
          </div>
        </div>
        <div class="clues-container" v-else>
          Down clues are locked
        </div>
      </v-container>
    </v-flex>
    <v-flex class="across pad w-full">
      <v-container>
        <v-layout class="clue-type">
          <v-flex class="flex-grow-0">
            <h3>Across</h3>
          </v-flex>
          <v-flex class="flex-grow-0 clue-lock">
            <ClueLock v-if="player" :unlocked="player.unlockedAcross" />
          </v-flex>
          <ClueUnlock ref="acrossUnlock" @unlock="$emit('unlock', $event)" type="across" :unlocked="player.unlockedAcross" />
        </v-layout>
        <div class="clues-container" v-if="acrossClues.length && player.unlockedAcross" >
          <div class="clue" 
            :class="{active: isActive(clue)}"
            v-for="clue in acrossClues" 
            :key="clue.label + '-across'" 
            @click="selectClue(DIRECTION.ACROSS, clue.label)">
            <b>{{clue.label}}.</b> <span v-html="clue.text" />
          </div>
        </div>
        <div class="clues-container" v-else>
          Across clues are locked 
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
        type: Object as () => { unlockedDown: boolean, unlockedAcross: boolean }
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
      },
      sendResponse (data: { success: boolean, type: string }) {
        // @ts-ignore
        if (data.type === 'down' && this.$refs.downUnlock && this.$refs.downUnlock.unlockResponse) {
          // @ts-ignore
          this.$refs.downUnlock.unlockResponse(data)
          // @ts-ignore
        } else if (this.$refs.acrossUnlock && this.$refs.acrossUnlock.unlockResponse) {
          // @ts-ignore
          this.$refs.acrossUnlock.unlockResponse(data)
        }
      }
    }
  })
</script>

<style lang="sass" scoped>
  .clues
    overflow: auto
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
  .clue-lock
    padding: 3px
</style>