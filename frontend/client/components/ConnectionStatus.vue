<template>
  <v-alert :value="!isOpen" :color="color" :transition="transition">
    <slot>
      Unable to communicate with server right now. You'll automatically reconnect after {{(time / 1000).toFixed(0)}}s. Please wait....
    </slot>
  </v-alert>
</template>

<script lang="ts">
  import Vue from 'vue'
  import { Socket, SocketState } from '../../core/socket'

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