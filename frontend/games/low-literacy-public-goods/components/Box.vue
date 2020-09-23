<template>
  <div class="box relative" :class="{ grow }">
    <transition name="back">]
      <img class="absolute z-0" v-show="open" :src="src.back" alt="">
    </transition>
    <!-- <transition name="fade">
      <div class="relative z-10" v-show="open">
        <transition name="envelope" :key="i" v-for="i in items">
          <div class="absolute w-64 h-64" v-if="envelope && showContents">
            <Envelope
              :closed="true"
              :style="envStyle(i)" />
          </div>
          <div ref="stacks" class="absolute w-64 h-64" :style="envStyle(i)" v-else-if="showContents">
            <MoneyStack :value="4" :locked="true" />
          </div>
        </transition>
      </div>
    </transition> -->
    <img class="absolute z-20" :src="src.front" alt="">
    <transition name="drop">
      <img class="absolute z-30" v-show="!open" :src="src.lid" alt="">
    </transition>
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
      }
    },
    data () {
      return {
        src: images.box,
        open: true,
        envelope: true,
        grow: false
      }
    },
    watch: {
      visible (visible: boolean, oldVisible: boolean) {
        if (visible && visible !== oldVisible) {
          this.envelope = true
          this.grow = false
        }
      }
    },
    methods: {
      async double () {
        this.open = false
        await delay(1500)
        this.envelope = false
        this.grow = true
        await delay(1500)
        this.open = true
        await delay(2500)
      }
    }
  })
</script>

<style lang="sass" scoped>
  $dur: 1s
  .grow
    transition: all $dur
    transform: scale(2) translateY(-50%)
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