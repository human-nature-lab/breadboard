<template>
  <v-alert :value="!isOpen" :color="color" :transition="transition" v-bind="$attrs" v-on="$listeners">
    <!-- The message to be shown when the client is disconnected -->
    <slot :time="time">
      Unable to communicate with the server right now. You will automatically reconnect after {{(time / 1000).toFixed(0)}}s. Please wait....
    </slot>
  </v-alert>
</template>

<script lang="ts">
  import Vue from 'vue'
  import { SocketState } from '@human-nature-lab/breadboard-core'

  /**
   * This component will show players a message if they are disconnected from
   * the game due to a network interruption. Breadboard will automatically
   * reconnect if possible.
   */
  export default Vue.extend({
    name: 'ConnectionStatus',
    props: {
      color: {
        type: String,
        default: 'info'
      },
      transition: {
        type: String,
        default: 'slide-x-transition'
      }
    },
    data () {
      return {
        isOpen: true,
        time: 0
      }
    },
    async created () {
      const socket = await window.Breadboard.connect()
      // @ts-ignore
      this.socket = socket
      if (socket.state === SocketState.CLOSED) {
        this.isOpen = false
      }
      window.Breadboard.on('close', this.onClose)
      window.Breadboard.on('retry', this.onRetry)
      window.Breadboard.on('open', this.onOpen)
    },
    destroyed () {
      window.Breadboard.off('close', this.onClose)
      window.Breadboard.off('retry', this.onRetry)
      window.Breadboard.off('open', this.onOpen)
    },
    methods: {
      onClose () {
        this.isOpen = false
      },
      onOpen () {
        this.isOpen = true
      },
      onRetry (time: number) {
        this.time = time
      } 
    }
  })
</script>

<style lang="sass">
  
</style>