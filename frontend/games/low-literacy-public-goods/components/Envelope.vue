<template>
  <CurrencyDropZone
    :value="value"
    :locked="closed"
    @input="$emit('input', $event)"
    dragKey="envelope">
    <template v-slot:item>
      <Currency
        style="width: 75%; transform: translate(15%, 130%)"
        :class="closed ? '' : 'hover:opacity-75 grab'"
        />
    </template>
    <div class="absolute w-full text-3xl text-center z-30 mt-4 select-none pointer-events-none">{{value}}</div>
    <img v-show="!closed" :src="backSrc" alt="" class="absolute z-0" draggable="false" />
    <img v-show="!closed" :src="frontSrc" class="absolute z-20 w-full h-full bg-contain pointer-events-none" />
    <img v-show="closed" :src="closedSrc" class="absolute z-20 w-full h-full bg-contain pointer-events-none" />
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