<template>
  <Transform class="absolute top-0 left-0" :transform="envelopeTransform" :visible="visible">
    <div class="absolute w-32 h-32" :style="{transform: `translate(${boxOffset.x}px, ${boxOffset.y}px)`, zIndex: boxOffset.zIndex}">
      <Envelope closed v-if="envelope" />
      <MoneyStack v-else :value="value || 0" :locked="locked" />
    </div>
  </Transform>
</template>

<script lang="ts">
  import Vue, { PropOptions } from 'vue'
  import { PlayerData } from '../../../core/breadboard.types'
  import { Transform } from '../steps'

  export default Vue.extend({
    name: 'PlayerEnvelope',
    props: {
      value: Number,
      envelope: Boolean,
      boxLoc: Object as PropOptions<Transform>,
      boxOffset: Object as PropOptions<Transform>,
      transform: Object as PropOptions<Transform>,
      itemInBox: Boolean,
      locked: {
        type: Boolean,
        default: true
      },
      visible: Boolean
    },
    beforeDestroy () {
      // @ts-ignore
      if (this.timeoutId) clearTimeout(this.timeoutId)
    },
    watch: {
      itemInBox (newVal: boolean, oldVal: boolean) {
        if (newVal !== oldVal) {
          // @ts-ignore
          if (this.timeoutId) clearTimeout(this.timeoutId)
          // @ts-ignore
          this.timeoutId = setTimeout(() => {
            if (this) {
              this.inBox = newVal
            }
          }, newVal ? 0 : 5000)
        }
      }
    },
    data () {
      return {
        inBox: this.itemInBox
      }
    },
    computed: {
      envelopeTransform (): Transform {
        return this.inBox ? { ...this.boxLoc, scale: .5 } : { x: this.transform.x, y: this.transform.y, scale: .5 }
      }
    }
  })
</script>

<style lang="sass">
  
</style>