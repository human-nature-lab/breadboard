<template>
  <div column class="chat-modal fixed bottom-0 w-1/2" :class="{'h-full': isOpen}">
    <v-toolbar dark flat @click="isOpen = !isOpen" class="chat-header">
      <v-toolbar-title>Chat</v-toolbar-title>
      <v-spacer />
      <v-icon v-if="isOpen">mdi-close</v-icon>
      <v-icon v-else>mdi-chevron-up</v-icon>
    </v-toolbar>
    <v-slide-y-transition>
      <Chat
        class="flex-grow-0"
        v-show="isOpen"
        @message="isOpen = true"
        :player="player"
        :stateKey="stateKey"
        :showRecipients="showRecipients"
        :senderName="senderName"
        :placeholder="placeholder" >
        <slot v-for="(_, name) in $slots" :name="name" :slot="name" />
      </Chat>
    </v-slide-y-transition>
  </div>
</template>

<script lang="ts">
  import Vue from 'vue'
  import Chat from './Chat.vue'
  import { PlayerData } from '../../core/breadboard.types'

  export default Vue.extend({
    name: 'ChatModal',
    components: { Chat },
    props: {
      player: {
        type: Object as () => PlayerData | null
      },
      stateKey: {
        type: String,
        default: 'chatState'
      },
      showRecipients: {
        type: Boolean,
        default: true
      },
      senderName: {
        type: String,
        default: 'Alter'
      },
      placeholder: {
        type: String,
        default: 'Write a message...'
      }
    },
    data () {
      return {
        isOpen: true
      }
    }
  })
</script>

<style lang="sass" scoped>
  .chat-header:hover
    background: #524a4a
    cursor: pointer
  .chat-modal
    max-width: 400px
    min-width: 200px
    right: 5%
    max-height: 40vh
</style>