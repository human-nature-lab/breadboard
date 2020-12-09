<template>
  <CurrencyDropZone
    :value="value"
    :locked="closed"
    :visible="!closed"
    group="envelope"
    @input="$emit('input', $event)">
    <template v-slot:item>
      <Currency
        style="width: 75%; transform: translate(15%, 30%)"
        :class="closed ? '' : 'hover:opacity-75 grab'"
        />
    </template>
    <div
      v-if="!closed"
      class="absolute w-full text-3xl text-center z-30 mt-4 select-none pointer-events-none">
      {{value}}
    </div>
    <CanvasImage v-if="!closed" :src="backSrc" alt="" class="absolute z-0 max-w-full max-h-full" draggable="false" />
    <CanvasImage v-if="!closed" :src="frontSrc" class="absolute z-20 max-w-full max-h-full pointer-events-none" />
    <CanvasImage v-else :src="closedSrc" class="absolute z-20 max-w-full max-h-full pointer-events-none" />
  </CurrencyDropZone>
</template>

<script lang="ts">
  import Vue from 'vue'
  import CurrencyDropZone from './CurrencyDropZone.vue'
  import Currency from './Currency.vue'
  import { images } from '../images'

  export default Vue.extend({
    name: 'Envelope',
    components: {
      CurrencyDropZone,
      Currency
    },
    props: {
      value: Number,
      closed: Boolean
    },
    data () {
      return {
        backSrc: images.envelope.openBack,
        frontSrc: images.envelope.openFront,
        closedSrc: images.envelope.closed
      }
    }
  })
</script>

<style lang="sass">
  
</style>