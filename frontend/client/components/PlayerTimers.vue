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
    }
  })
</script>

<style lang="sass" scoped>

</style>
