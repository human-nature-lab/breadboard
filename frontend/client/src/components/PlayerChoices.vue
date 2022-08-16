<template>
  <v-col v-if="hasChoices" class="player-choices">
    <v-row class="choices-container justify-space-between">
      <template v-for="choice of player.choices">
        <slot name="choice" :choice="choice" :submit="submit">
          <Choice :choice="choice" :key="choice.uid" @click="submit(choice)" />
        </slot>
      </template>
    </v-row>
  </v-col>
</template>

<script lang="ts">
import Vue from 'vue'
import { PlayerChoice } from '@human-nature-lab/breadboard-core'
import Choice from './Choice.vue'

/**
 * A container for all of the player choices
 */
export default Vue.extend({
  name: 'PlayerChoices',
  components: { Choice },
  props: {
    player: {
      type: Object,
      required: true
    },
    choiceFilter: {
      type: Function
    }
  },
  data() {
    return {
      choicesAreEnabled: true
    }
  },
  updated() {
    this.choicesAreEnabled = true
    // console.log('player choices updated')
  },
  methods: {
    submit (choice: PlayerChoice) {
      if (choice.params) {
        window.Breadboard.sendChoice(choice.uid, choice.params)
      } else {
        window.Breadboard.sendChoice(choice.uid)
      }
      this.choicesAreEnabled = false
    },
    /**
     * This filter allows to easily replace only certain choices and keep the other choices by default
     */
    useDefaultChoice (choice: { [key: string]: any }): boolean {
      if (!this.$slots.choice && !this.$scopedSlots.choice) {
        return true
      } else if (!this.choiceFilter) {
        return false
      } else {
        return !this.choiceFilter(choice)
      }
    }
  },
  computed: {
    hasChoices (): boolean {
      return !!this.defaultChoices.length || !!this.slotChoices.length
    }
  }
})
</script>
