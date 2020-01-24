<template>
  <v-alert :value="!isOpen" :color="color" :transition="transition">
    <slot>
      Unable to communicate with server right now. Please wait....
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
        isOpen: true
      }
    },
    async created () {
      const socket = await window.Breadboard.connect()
      // @ts-ignore
      this.socket = socket
      if (socket.state === SocketState.CLOSED) {
        this.isOpen = false
      }
      socket.on('close', this.onClose)
      socket.on('open', this.onOpen)
    },
    destroyed () {
      // @ts-ignore
      const socket = this.socket
      if (socket) {
        socket.off('close', this.onClose)
        socket.off('open', this.onOpen)
      }
    },
    methods: {
      onClose () {
        this.isOpen = false
      },
      onOpen () {
        this.isOpen = true
      }
    }
  })
</script>

<style lang="sass">
  
</style>