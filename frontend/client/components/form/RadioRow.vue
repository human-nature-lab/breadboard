<template>
  <tr>
    <td>{{label}}</td>
    <td 
      v-for="option in options" :key="option" 
      class="centered">
      <v-icon @click="$emit('input', option)" :disabled="disabled">
        {{value === option ? 'mdi-radiobox-marked' : 'mdi-radiobox-blank'}}
      </v-icon>
    </td>
  </tr>
</template>

<script lang="ts">
  import Vue from 'vue'

  export default Vue.extend({
    name: 'RadioRow',
    props: {
      value: [String, Number],
      label: String,
      options: Array as () => (string | number)[],
      disabled: {
        type: Boolean,
        default: false
      }
    }
  })
</script>

<style lang="sass">
  .centered
    text-align: center
  .invalid
    color: red
</style>