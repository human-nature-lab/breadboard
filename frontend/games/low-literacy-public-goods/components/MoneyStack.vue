<template>
  <CurrencyDropZone
    :value="value"
    :locked="locked"
    @input="$emit('input', $event)"
    dragKey="pending">
    <template v-slot:item>
      <Currency style="width: 80%; transform: translate(10%, 100%)"/>
    </template>
    <div class="absolute w-full text-3xl text-center z-30 mt-4">{{value}}</div>
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
      locked: Boolean
    }
  })
</script>

<style lang="sass">
  
</style>