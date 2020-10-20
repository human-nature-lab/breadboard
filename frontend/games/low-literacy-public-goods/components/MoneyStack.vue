<template>
  <CurrencyDropZone
    :value="bills"
    :locked="locked"
    :rotate="rotate"
    :xOffset="xOffset"
    :yOffset="yOffset"
    @input="$emit('input', $event)"
    dragKey="pending">
    <template v-slot:item>
      <Currency style="width: 80%; transform: translate(10%, 100%)"/>
    </template>
    <div v-if="showValue" class="absolute w-full text-3xl text-center z-30 mt-4">{{+value.toFixed(2)}}</div>
    <slot />
  </CurrencyDropZone>
</template>

<script lang="ts">
  import Vue from 'vue'
  import CurrencyDropZone from './CurrencyDropZone.vue'
  import Currency from './Currency.vue'

  export default Vue.extend({
    name: 'MoneyStack',
    components: { CurrencyDropZone, Currency },
    props: {
      value: Number,
      locked: Boolean,
      rotate: Number,
      xOffset: Number,
      yOffset: Number,
      showValue: {
        type: Boolean,
        default: true
      }
    },
    computed: {
      bills (): number {
        return Math.floor(this.value)
      }
    }
  })
</script>

<style lang="sass">
  
</style>