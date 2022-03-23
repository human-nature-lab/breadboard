<template>
  <v-flex v-if="block" v-html="block.content" />
</template>

<script lang="ts">
  import Vue from 'vue'
  import { HtmlQuestion } from '../form.types'

  export default Vue.extend({
    name: 'HtmlBlock',
    props: {
      block: {
        type: Object as () => HtmlQuestion,
        required: true
      }
    }
  })
</script>

<style lang="sass">
  
</style>