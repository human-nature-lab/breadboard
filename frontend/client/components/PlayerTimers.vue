<template>
  <v-flex>
    <Timer v-for="timer in player.timers"
           :key="timer.id"
           :timer="timer" />
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
