<template>
  <v-progress-linear
    height="25"
    :value="value"
    :color="color"
    reactive>
    <!-- @slot Add something before the label-->
    <slot name="prepend" />
    <!-- @slot Replace the default timer message -->
    <slot
        name="label"
        :timer="timer"
        :time="time"
        :remaining="remaining"
        :value="value"
        :color="color">
      <strong>
        {{message}}
      </strong>
    </slot>
    <!-- @slot Add something after the label -->
    <slot name="append" />
  </v-progress-linear>
</template>

<script lang="ts">
  import Vue from 'vue'
  import { PlayerTimer } from '../../core/breadboard.types'
  import { toHHMMSS } from '../lib/DateTime'

  export default Vue.extend({
    name: 'Timer',
    props: {
      timer: {
        type: Object as () => PlayerTimer,
        required: true
      }
    },
    computed: {
      time (): number {
        return Math.round(this.timer.direction === 'down' ?
          this.remaining / 1000 :
          this.timer.elapsed / 1000)
      },
      value (): number {
        return this.timer.direction === 'down' ?
          100 * this.remaining / this.timer.duration :
          100 * this.timer.elapsed / this.timer.duration
      },
      remaining (): number {
        return this.timer.duration - this.timer.elapsed
      },
      message (): string {
        let res = this.timer.timerText + ' '
        switch (this.timer.type) {
          case 'currency':
            return res + (this.timer.currencyAmount * this.value / 100 / 100).toFixed(2) + '$'
          case 'percent':
            return res + this.value + '%'
          default:
            return res + toHHMMSS((this.time * 1000).toString())
        }
      },
      color (): string {
        return this.timer.appearance === 'danger' ? 'error' : this.timer.appearance || 'info'
      }
    }
  })
</script>

<style lang="sass" scoped>

</style>
