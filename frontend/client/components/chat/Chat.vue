<template>
  <v-card class="chat-box h-full" v-if="chatState">
    <div class="messages overflow-auto" ref="messages">
      <div v-if="!messages.length">
        <slot name="empty">
          No messages have been sent so far...
        </slot>
      </div>
      <div v-for="message in messages" class="message" :key="message.id">
        <slot :message="message">
          <v-layout>
            <v-flex class="flex-grow-0">
              <b v-if="message.sender === myId">Me</b>
              <b v-else>
                {{showRecipients ? message.sender : senderName}}
              </b>
            </v-flex>
            <v-flex class="flex-grow-0">: {{message.text}}</v-flex>
            <v-spacer />
            <v-flex v-if="showRecipients" class="recipients text-right">({{message.recipients.join(', ')}})</v-flex>
          </v-layout>
        </slot>
      </div>
    </div>
    <v-spacer />
    <v-form ref="form" @submit.prevent="() => {}" class="flex-grow-0">
      <v-layout column class="chat-input">
        <v-text-field
          validate-on-blur
          :placeholder="placeholder"
          :rules="textRules"
          append-outer-icon="mdi-send"
          autocomplete="off"
          :counter="maxLength"
          @keyup.enter="send"
          @click:append-outer="send"
          v-model="text">
        </v-text-field>
        <v-select
          v-if="showRecipients && recipients.length > 1"
          chips
          clearable
          multiple
          small-chips
          deletable-chips
          label="Recipients"
          :rules="recipientRules"
          :items="recipients" 
          v-model="selectedRecipients" />
      </v-layout>
    </v-form>
  </v-card>
</template>

<script lang="ts">
  import Vue from 'vue'
  import { Message, ChatState } from './chat.types'
  import { PlayerData } from '../../../core/breadboard.types'

  export default Vue.extend({
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
    data: function () {
      return {
        text: '',
        recipients: [] as string[],
        messageLimit: 10,
        maxLength: 255,
        lastMessage: null as null | Message,
        selectedRecipients: [] as string[],
        textRules: [(text: string) => !!text || 'Enter a message'],
        recipientRules: [(vals: string[]) => !!vals.length || 'Select at least one recipient']
      }
    },
    created () {
      console.log('created')
      this.$watch('player.' + this.stateKey, () => {
        const state = this.chatState
        if (state) {
          this.recipients = state.allowedRecipients
          if (!this.selectedRecipients.length) {
            this.selectedRecipients = this.recipients.slice()
          }
          this.messageLimit = state.messageBufferSize
          this.maxLength = state.maxLength
        }
      })
    },
    beforeDestroy () {
      console.log('beforeDestroy')
    },
    methods: {
      send (): void {
        // @ts-ignore
        if (!this.$refs.form.validate()) {
          return
        }
        window.Breadboard.send('chat', {
 		      recipients: this.selectedRecipients,
          text: this.text
        })
        this.text = ''
      },
      scrollToBottom (): void {
        // @ts-ignore
        let el: Element = this.$refs.messages
        el.scrollTo({
          top: el.scrollHeight,
          behavior: 'smooth'
        })
      },
      onNewMessage (): void {
        this.$emit('message', this.lastMessage)
        setTimeout(this.scrollToBottom, 1000)
      }
    },
    computed: {
      chatState (): ChatState | null {
        return this.player && this.player[this.stateKey]
      },
      myId (): string | null {
      	return this.player ? this.player.id : null
      },
      messages (): Message[]  {
        const messages = this.chatState ? this.chatState.messages : []
        if (messages.length) {
          const lastMessage = messages[messages.length - 1]
          if (this.lastMessage && this.lastMessage.text !== lastMessage.text || !this.lastMessage) {
            this.lastMessage = lastMessage
            this.onNewMessage()
          }
        }
      	return messages.slice(-this.messageLimit)
      } 
    }
  })
</script>

<style lang="sass" scoped>
  .messages
    overflow: auto
    max-height: calc(100% - 160px)
</style>