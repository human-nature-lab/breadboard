<template>
  <v-flex>
    <v-container>
      <v-flex v-html="player.text" />
    </v-container>
  </v-flex>
</template>

<script lang="ts">
  import Vue from 'vue'

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
