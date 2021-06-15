<template>
  <v-flex>
    <!-- Replace all the the timers with a custom timer -->
    <slot name="timer" :timer="timer" v-for="timer in player.timers">
      <Timer
        :key="timer.id"
        :timer="timer" />
    </slot>
  </v-flex>
</template>

<script lang="ts">
  import Vue from 'vue'
  import { PlayerTimer } from '../../core/breadboard.types'

  /**
   * The container for all of the player's timers. Multiple timers can be running
   * at the same time and this component creates them.
   * 
   * ### Basic usage:
   * ```vue
   * <template>
   *   <PlayerTimers :player="player" />
   * </template>
   * ```
   * 
   * ### Custom timer:
   * ```vue
   * <template>
   *   <PlayerTimers :player="player">
   *     <template #timer="{ timer }">
   *       <MyCustomTimerComponent :timer="timer" />
   *     </template>
   *   </PlayerTimers>
   * </template>
   * ```
   */
  export default Vue.extend({
    name: 'PlayerTimers',
    props: {
      /**
       * The player object 
       */
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
