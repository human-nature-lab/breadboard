<template>
  <CurrencyDropZone
    :value="bills"
    :locked="locked"
    :xOffset="xOffset"
    :group="group"
    :yOffset="yOffset"
    @input="$emit('input', $event)">
    <template v-slot:item>
      <Currency
        :style="currencyStyle"
        :class="locked ? '' : 'hover:opacity-75 grab'"
        />
    </template>
    <div v-if="showValue" class="absolute w-full text-3xl text-center z-30 label">
      <span class="py-2 px-6 rounded-full inline-block" :class="{'bg-white': bold}">
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
      },
      group: {
        type: String,
        default: 'money'
      }
    },
    computed: {
      bills (): number {
        return Math.floor(this.value)
      },
      currencyStyle () {
        let transform = ''
        if (this.rotate) {
          transform += ` rotate(${this.rotate}deg)`
        }
        return {
          width: '80%',
          transform
        }
      }
    }
  })
</script>

<style lang="sass" scoped>
  .label
    top: -50%
    margin-left: -8px
  .grab
    cursor: grab
</style>