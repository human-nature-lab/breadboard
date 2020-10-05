<template>
  <CurrencyDropZone
    :value="val"
    :yOffset="0"
    :xOffset="7"
    @input="$emit('input', $event)"
    :visible="!closed"
    dragKey="wallet"
    :locked="closed">
    <template v-slot:item>
      <Currency style="width: 70%; transform: translate(0, 50%) rotate(75deg)" />
    </template>
    <div class="absolute w-full text-3xl text-center z-20 mt-24 text-white select-none pointer-events-none">{{val}}</div>
    <img rel="preload" :src="walletSrc" alt="" class="absolute z-10 pointer-events-none" draggable="false" />
  </CurrencyDropZone>
</template>

<script lang="ts">
  import Vue from 'vue'

  export default Vue.extend({
    name: 'Wallet',
    props: {
      value: Number,
      earned: Number,
      closed: Boolean
    },
    data () {
      return {
        walletSrc: '/images/233/wallet.png'
      }
    },
    computed: {
      val (): number {
        return this.closed ? this.value + this.earned : this.value
      }
    }
  })
</script>

<style lang="sass">
  
</style>