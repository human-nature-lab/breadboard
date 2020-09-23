<template>
  <Transform  :transform="transform">
    <div ref="container">
      <img :src="src" alt="" class="player opacity-50 w-32 h-32">
      <Transform ref="item" class="absolute top-0 left-0" :transform="envelopeTransform" :visible="showItem" v-if="hasItem">
        <div class="absolute w-32 h-32" :style="{transform: `translate(${boxOffset.x}px, ${boxOffset.y}px)`, zIndex: boxOffset.zIndex}">
          <Envelope closed v-if="envelope" />
          <MoneyStack v-else :value="4" :locked="true" />
        </div>
      </Transform>
    </div>
  </Transform>
</template>

<script lang="ts">
  import Vue, { PropOptions } from 'vue'
  import gsap from 'gsap'
  import { images } from '../images'
  import { delay } from '../../../core/delay'
  import { Transform } from '../steps'

  export default Vue.extend({
    name: 'Player',
    props: {
      transform: {
        type: Object,
        required: true
      } as PropOptions<Transform>,
      itemInBox: {
        type: Boolean,
        required: true
      },
      showItem: {
        type: Boolean,
        default: true
      },
      hasItem: {
        type: Boolean,
        default: true
      },
      envelope: {
        type: Boolean,
        default: true
      },
      boxLoc: {
        type: Object,
        required: true
      } as PropOptions<{ x: number, y: number }>,
      boxOffset: {
        type: Object,
        required: true
      } as PropOptions<Transform>
    },
    data () {
      return {
        src: images.person
      }
    },
    mounted () {
      this.detachItem()
    },
    beforeDestroy () {
      this.attachItem()
    },
    methods: {
      detachItem () {
        // @ts-ignore
        if (this.hasItem && this.$refs.item.$el) document.getElementById('game').appendChild(this.$refs.item.$el)
      },
      attachItem () {
        // @ts-ignore
        if (this.hasItem && this.$refs.item.$el) this.$refs.container.appendChild(this.$refs.item.$el)
      }
    },
    computed: {
      envelopeTransform (): Transform {
        return this.itemInBox ? { ...this.boxLoc, scale: .5 } : { x: this.transform.x, y: this.transform.y, scale: .5 }
      }
    }
  })
</script>

<style lang="sass">
  // .player
  //   transform-origin: top left
</style>