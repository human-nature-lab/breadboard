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
      <Currency
        style="width: 80%; transform: translate(50%, 0)"
        :class="locked ? '' : 'hover:opacity-75 grab'"
        />
    </template>
    <div v-if="showValue" class="absolute w-full text-3xl text-center z-30 mt-4">
      <span class="p-2 rounded-full inline-block" :class="{'bg-white': bold}">
        {{+value.toFixed(2)}}
      </span>
    </div>
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
      },
      bold: {
        type: Boolean,
        default: false
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
  .grab
    cursor: grab
</style>