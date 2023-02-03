<template>
  <div @click="onClick" :class="!val && 'hover:opacity-75'">
    <CanvasImage v-if="val" :src="lock.closed" />
    <CanvasImage v-else :src="lock.open" />
  </div>
</template>

<script lang="ts">
  import Vue from 'vue'
  import { images } from '../images'

  export default Vue.extend({
    name: 'Lock',
    props: {
      value: Boolean
    },
    data () {
      return {
        lock: images.lock, 
        val: this.value
      }
    },
    methods: {
      onClick () {
        this.$emit('click', !this.value)
        this.val = !this.value
      }
    }
  })
</script>

<style lang="sass">
  
</style>