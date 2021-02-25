<template>
  <Transform :transform="transform">
    <div ref="container">
      <CanvasImage :src="src" class="player opacity-50 w-32 h-32" />
      <PlayerEnvelope
        :value="value"
        :envelope="envelope"
        group="other player envelope"
        :transform="transform"
        :boxLoc="boxLoc"
        :boxOffset="itemInBox ? boxOffset : { x: 50, y: 50 }"
        :itemInBox="itemInBox"
        :visible="showItem"
        :locked="locked"
        />
    </div>
  </Transform>
</template>

<script lang="ts">
  import Vue, { PropOptions } from 'vue'
  import { images } from '../images'
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
      boxOffset: Object as PropOptions<Transform>,
      value: Number,
      locked: Boolean
    },
    data () {
      return {
        src: images.person
      }
    }
  })
</script>

<style lang="sass">
  // .player
  //   transform-origin: top left
</style>