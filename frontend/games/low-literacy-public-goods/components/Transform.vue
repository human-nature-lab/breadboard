<template>
  <div class="trans absolute" :style="style">
    <transition name="fade">
      <slot v-if="visible" />
    </transition>
  </div>
</template>

<script lang="ts">
  import Vue from 'vue'

  export default Vue.extend({
    name: 'Transform',
    props: {
      transform: Object,
      visible: {
        type: Boolean,
        default: true
      }
    },
    computed: {
      style () {
        const t = Object.assign({}, this.transform)
        const style: any = {
          transform: '',
        }
        if (t.x !== undefined) {
          style.transform += ` translateX(${t.x}vw)`
          delete t.x
        }
        if (t.y !== undefined) {
          style.transform += ` translateY(${t.y}vh)`
          delete t.y
        }
        if (t.scale) {
          style.transform += ` scale(${t.scale})`
          delete t.scale
        }
        return {
          ...style,
          ...t
        }
      }
    }
  })
</script>

<style lang="sass">
  .fade-enter-active, .fade-leave-active
    transition: opacity .5s
  .fade-enter, .fade-leave-to
    opacity: 0
  .trans
    transition: all 1s
    transform-origin: center
</style>