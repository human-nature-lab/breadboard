<template>
  <v-icon :color="unlocked ? 'success' : 'error'">
    {{unlocked ? 'mdi-lock-open-variant' : 'mdi-lock'}}
  </v-icon>
</template>

<script lang="ts">
  import Vue from 'vue'

  export default Vue.extend({
    name: 'ClueLock',
    props: {
      unlocked: Boolean
    }
  })
</script>