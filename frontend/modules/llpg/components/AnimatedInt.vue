<template>
  <span>
    {{val}}
  </span>
</template>

<script lang="ts">
  import Vue from 'vue'
  import gsap from 'gsap'

  export default Vue.extend({
    name: 'AnimatedInt',
    props: {
      value: Number,
      animated: {
        type: Boolean,
        default: true
      },
      delay: {
        type: Number,
        default: 0
      }
    },
    data () {
      return {
        tweenedValue: this.value,
        timeout: null as number | null
      }
    },
    watch: {
      value (newVal: number, oldVal: number) { 
        if (newVal !== oldVal) {
          if (this.delay) {
            if (this.timeout) {
              clearTimeout(this.timeout)
              this.timeout = null
            }
            this.timeout = setTimeout(() => {
              this.setValue(newVal)
              this.timeout = null
            }, this.delay)
          } else {
            this.setValue(newVal)
          }
        }
      }
    },
    methods: {
      setValue (newValue: number) {
        if (this.animated) {
          gsap.to(this.$data, { duration: 1, tweenedValue: newValue })
        } else {
          this.tweenedValue = newValue
        }
      }
    },
    computed: {
      val (): string {
        return this.tweenedValue.toFixed(0)
      }
    }
  })
</script>

<style lang="sass">
  
</style>