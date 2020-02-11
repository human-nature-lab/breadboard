<template>
  <v-flex v-if="player.text">
    <v-container>
      <slot name="prepend" />
      <slot :text="player.text">
        <p v-html="player.text" />
      </slot>
      <slot name="append" />
    </v-container>
  </v-flex>
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
