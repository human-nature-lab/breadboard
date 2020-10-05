<template>
  <div class="box absolute h-full w-full" :class="{ grow }">
    <transition name="back">
      <img rel="preload" class="absolute z-0" v-show="open" :src="src.back" alt="">
    </transition>
    <img rel="preload" class="absolute z-20" :src="src.front" alt="">
    <transition name="drop">
      <img v-show="!open" rel="preload" ref="lid" class="absolute z-30" :src="src.lid" alt="">
    </transition>
    <div v-if="showValue" class="absolute value z-40">
      {{animatedValue}}
    </div>
  </div>
</template>

<script lang="ts">
  import Vue, { PropOptions } from 'vue'
  import gsap from 'gsap'
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
      }
    },
    data () {
      return {
        src: images.box,
        open: true,
        envelope: true,
        showValue: false,
        grow: false,
        tweenedValue: 0
      }
    },
    watch: {
      visible (visible: boolean, oldVisible: boolean) {
        if (visible && visible !== oldVisible) {
          this.envelope = true
          this.grow = false
        }
      },
      value (newValue: number) {
        this.setValue(newValue)
      }
    },
    methods: {
      async double () {
        this.open = false
        this.showValue = true
        await delay(1500)
        this.envelope = false
        this.grow = true
        this.setValue(this.value * 2)
        await delay(1500)
        this.open = true
        this.showValue = false
        await delay(500)
      },
      setValue (newValue: number) {
        gsap.to(this.$data, { duration: 1.5, tweenedValue: newValue })
      }
    },
    computed: {
      animatedValue (): string {
        return this.tweenedValue.toFixed(0)
      }
    }
  })
</script>

<style lang="sass" scoped>
  .value
    top: 10%
    left: 50%

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