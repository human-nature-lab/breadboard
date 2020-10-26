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
        tweenedValue: this.value
      }
    },
    watch: {
      value (newVal: number, oldVal: number) { 
        if (newVal !== oldVal) {
          if (this.delay) {
            setTimeout(() => {
              this.setValue(newVal)
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