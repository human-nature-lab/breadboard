<template>
  <tr>
    <td>{{label}}</td>
    <td v-for="option in options" :key="option" class="centered">
      <v-checkbox @click="$emit('input', option)" :value="value === option" />
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
      options: Array as () => (string | number)[]
    }
  })
</script>

<style lang="sass">
  .centered
    text-align: center
</style>