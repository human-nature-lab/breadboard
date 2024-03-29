<template>
  <v-col class="pa-0">
    <!-- Replace all the the timers with a custom timer -->
    <slot name="timer" :timer="timer" v-for="timer in player.timers">
      <Timer
        :key="timer.id"
        :timer="timer" />
    </slot>
  </v-col>
</template>

<script lang="ts">
  import Vue, { PropType } from 'vue'
  import { PlayerTimer, PlayerData } from '@human-nature-lab/breadboard-core'
  import Timer from './Timer.vue'

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
    components: { Timer },
    props: {
      /**
       * The player object 
       */
      player: {
        type: Object as PropType<PlayerData>,
        required: true
      }
    },
    computed: {
      timers (): PlayerTimer[] {
        const timers: PlayerTimer[] = []
        if (this.player.timers) {
          for (const key in this.player.timers) {
            timers.push(this.player.timers[key])
          }
        }
        timers.sort((a, b) => a.order - b.order)
        return timers
      }
    }
  })
</script>

<style lang="sass" scoped>

</style>
