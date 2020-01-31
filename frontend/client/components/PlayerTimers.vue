<template>
  <v-flex>
    <!-- @slot Replace the timer with a custom timer -->
    <slot :timer="timer" v-for="timer in player.timers">
      <Timer :key="timer.id"
             :timer="timer" >
      </Timer>
    </slot>
  </v-flex>
</template>

<script lang="ts">
  import Vue from 'vue'
 import { PlayerTimer } from '../../core/breadboard.types'

  /**
   * A container for all of the player's timers
   */
  export default Vue.extend({
    name: 'PlayerTimers',
    props: {
      player: {
        type: Object,
        required: true
      }
    },
    computed: {
      timers () {
        const timers: PlayerTimer[] = Object.values(this.player.timers)
        timers.sort((a, b) => a.order - b.order)
        return timers
      }
    }
  })
</script>

<style lang="sass" scoped>

</style>
