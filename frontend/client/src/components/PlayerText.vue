<template>
  <v-col v-if="player.text">
    <!-- Add something before the player content -->
    <slot name="prepend" />
    <slot :text="player.text">
      <p v-html="player.text" />
    </slot>
    <!-- Add something after the player content -->
    <slot name="append" />
  </v-col>
</template>

<script lang="ts">
  import Vue from 'vue'

  /**
   * A container for the HTML content shown to the player
   */
  export default Vue.extend({
    name: 'PlayerText',
    props: {
      player: {
        type: Object,
        required: true
      }
    }
  })
</script>
