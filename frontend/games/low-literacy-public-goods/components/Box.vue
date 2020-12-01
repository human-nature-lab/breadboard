<template>
  <div class="box absolute h-full w-full" :class="{ grow: double }">
    <transition name="back">
      <CanvasImage class="absolute z-0 max-w-full max-h-full" :src="src.back" />
    </transition>
    <CanvasImage class="absolute z-20 max-w-full max-h-full" :src="src.front" />
    <transition name="drop">
      <CanvasImage v-if="!open" ref="lid" class="absolute z-30 max-w-full max-h-full" :src="src.lid" />
    </transition>
    <div v-if="showValue" class="absolute value z-40 text-bold text-2xl text-center">
      <AnimatedInt :value="value" />
    </div>
  </div>
</template>

<script lang="ts">
  import Vue, { PropOptions } from 'vue'
  import { images } from '../images'
  import { delay } from '../../../core/delay'

  export default Vue.extend({
    name: 'Box',
    props: {
      visible: {
        type: Boolean,
        default: true
      },
      showContents: {
        type: Boolean,
        default: false
      },
      items: {
        type: Number,
        default: 0
      },
      value: {
        type: Number
      },
      double: Boolean,
      showValue: Boolean,
      open: Boolean
    },
    data () {
      return {
        src: images.box,
        envelope: true,
      }
    },
    watch: {
      visible (visible: boolean, oldVisible: boolean) {
        if (visible && visible !== oldVisible) {
          this.envelope = true
        }
      },
    },
  })
</script>

<style lang="sass" scoped>
  .value
    top: 10%
    width: 100%
    // left: 50%

  $dur: 1s
  .grow
    transition: all $dur
    transform: scale(2)
    // transform-origin: 50% 100%
  .drop-enter-active, .drop-leave-active
    transition: all $dur
  .drop-enter, .drop-leave-to
    transform: translateY(-500px)
  .back-leave-active
    transition: opacity $dur
    transition-delay: $dur
  .back-leave-to
    opacity: 0
  .envelope-enter-active, .envelope-leave-active
    transition: all $dur
  .envelope-enter
    transform: translateY(-500px)
  .envelope-enter-to
    transform: translateY(0)
  .envelope-leave-to
    opacity: 0
</style>