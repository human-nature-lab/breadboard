<template>
  <v-progress-linear
    height="25"
    :value="value"
    :color="color"
    reactive>
    <!-- Add something before the timer label. For example a dollar sign. -->
    <slot name="prepend" />
    <!-- Replace the default timer message with your own-->
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
    <!-- Add something after the label. For example a percentage (%) -->
    <slot name="append" />
  </v-progress-linear>
</template>

<script lang="ts">
  /**
   * The default timer component. This component can be easily extended using slots.
   */
  import Vue from 'vue'
  import { PlayerTimer } from '../../core/breadboard.types'
  import { toHHMMSS } from '../lib/DateTime'

  export default Vue.extend({
    name: 'Timer',
    props: {
      /**
       * The timer object sent from the server
       */
      timer: {
        type: Object as () => PlayerTimer,
        required: true
      },
      /**
       * How far off the timer can be from the server time
       */
      timerElapsedTolerance: {
        type: Number,
        default: 2000
      },
      /**
       * How frequently to update the timer on the client
       */
      updateRate: {
        type: Number,
        default: 1000
      }
    },
    data () {
      return {
        elapsed: this.timer ? this.timer.elapsed : 0
      }
    },
    created () {
      // @ts-ignore
      this.interval = setInterval(() => {
        this.elapsed += this.updateRate
      }, this.updateRate)
    },
    beforeDestroy () {
      // @ts-ignore
      clearInterval(this.interval)
    },
    computed: {
      time (): number {
        if (Math.abs(this.elapsed - this.timer.elapsed) > this.timerElapsedTolerance) {
          this.elapsed = this.timer.elapsed
        }
        return Math.round(this.timer.direction === 'down' ?
          this.remaining / 1000 :
          this.elapsed / 1000)
      },
      value (): number {
        return this.timer.direction === 'down' ?
          100 * this.remaining / this.timer.duration :
          100 * this.elapsed / this.timer.duration
      },
      remaining (): number {
        return this.timer.duration - this.elapsed
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
