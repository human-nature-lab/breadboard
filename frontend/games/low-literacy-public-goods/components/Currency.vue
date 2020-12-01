<template>
  <CanvasImage
    :src="stack ? stackSrc : singleSrc"
    draggable="false" />
</template>

<script lang="ts">
  import Vue from 'vue'
  import { images } from '../images'

  export default Vue.extend({
    name: 'Currency',
    props: {
      stack: {
        type: Boolean,
        default: false
      }
    },
    data () {
      return {
        singleSrc: images.currency.single,
        stackSrc: images.currency.stack
      }
    }
  })
</script>

<style lang="sass">
  
</style>