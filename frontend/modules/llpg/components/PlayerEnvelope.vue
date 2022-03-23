<template>
  <Portal to="game">
    <Transform class="absolute top-0 left-0" :transform="envelopeTransform" :visible="visible">
      <div v-show="visible" class="w-32 h-32" :style="style">
        <Envelope closed v-if="envelope" :value="0" />
        <MoneyStack 
          v-else
          :value="value || 0"
          :group="group"
          :locked="locked"
          width="80%"
          :bold="true"
          :xOffset="2"
          :yOffset="2" />
      </div>
    </Transform>
  </Portal>
</template>

<script lang="ts">
  import Vue, { PropOptions } from 'vue'
  import { Portal } from 'portal-vue'
  import { PlayerData } from '@human-nature-lab/breadboard-core'
  import { Transform } from '../steps'

  export default Vue.extend({
    name: 'PlayerEnvelope',
    components: { Portal },
    props: {
      value: Number,
      envelope: Boolean,
      closed: {
        type: Boolean,
        default: true
      },
      boxLoc: Object as PropOptions<Transform>,
      boxOffset: Object as PropOptions<Transform>,
      transform: Object as PropOptions<Transform>,
      itemInBox: Boolean,
      group: String,
      locked: {
        type: Boolean,
        default: true
      },
      visible: Boolean
    },
    // mounted () {
    //   console.log('PlayerEnvelope.created')
    //   this.detachItem()
    // },
    beforeDestroy () {
      // this.attachItem()
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
        inBox: this.itemInBox,
        _parent: null as Element | null
      }
    },
    computed: {
      envelopeTransform (): Transform {
        return this.inBox ? { ...this.boxLoc, scale: .5 } : { x: this.transform.x, y: this.transform.y, scale: .5 }
      },
      style (): object {
        if (!this.boxOffset) return {}
        return {
          transform: `translate(${this.boxOffset.x}px, ${this.boxOffset.y}px)`,
          zIndex: this.boxOffset.zIndex
        }
      }
    },
    methods: {
      detachItem () {
        // @ts-ignore
        if (this.$refs.self.$el) {
          // @ts-ignore
          this._parent = this.$refs.self.$el.parentNode
          // @ts-ignore
          document.getElementById('game').appendChild(this.$refs.self.$el)
        }
      },
      attachItem () {
        // @ts-ignore
        if (this.$refs.self.$el && this._parent) {
          // @ts-ignore
          this._parent.appendChild(this.$refs.self.$el)
        } else {
          // @ts-ignore
          this.$refs.self.$el.remove()
        }
      }
    }
  })
</script>

<style lang="sass">
  
</style>