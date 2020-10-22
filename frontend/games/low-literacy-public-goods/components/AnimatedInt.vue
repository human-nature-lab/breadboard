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
          if (this.animated) {
            this.setValue(newVal)
          } else {
            this.tweenedValue = newVal
          }
        }
      }
    },
    methods: {
      setValue (newValue: number) {
        gsap.to(this.$data, { duration: 1, tweenedValue: newValue })
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