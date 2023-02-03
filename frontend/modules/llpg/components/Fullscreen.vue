<template>
  <div class="relative min-h-full min-w-full bg-white" ref="container">
    <slot />
    <v-btn 
      class="absolute top-0 right-0 m-3 z-50"
      @click="toggleFullscreen"
      v-if="!isFullscreen"
      icon>
      <v-icon>mdi-fullscreen</v-icon>
    </v-btn>
  </div>
</template>

<script lang="ts">
  import Vue from 'vue'

  export default Vue.extend({
    name: 'Fullscreen',
    data () {
      return {
        isFullscreen: false
      }
    },
    created () {
      document.addEventListener('fullscreenchange', this.onFullscreenChange)
    },
    beforeDestroy () {
      document.removeEventListener('fullscreenchange', this.onFullscreenChange)
    },
    methods: {
      async toggleFullscreen () {
        if (!this.isFullscreen && this.$refs.container instanceof HTMLElement) {
          await document.body.requestFullscreen()
        } else {
          await document.exitFullscreen()
        }
      },
      onFullscreenChange (event: Event) {
        console.log(event)
        this.isFullscreen = document.fullscreenElement === this.$refs.container
      }
    }
  })
</script>

<style lang="sass">
  
</style>