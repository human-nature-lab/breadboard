<template>
  <v-flex>
    <div v-if="choice.custom" v-html="choice.custom" />
    <v-btn :disabled="disabled" @click="$emit('click', $event)">
      {{choice.name}}
    </v-btn>
  </v-flex>
</template>

<script lang="ts">
  import Vue from 'vue'

  export default Vue.extend({
    name: 'Choice',
    props: {
      choice: Object,
      disabled: {
        type: Boolean,
        default: false
      }
    }
  })
</script>
