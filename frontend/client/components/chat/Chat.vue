<template>
  <v-card class="chat-box h-full" v-if="chatState">
    <v-layout column class="h-full">
      <v-layout column class="messages overflow-auto">
        <v-flex v-for="message in messages" class="message flex-grow-0" :key="message.id">
          <slot :message="message">
            <v-layout>
              <v-flex >
                <b v-if="message.sender === myId">Me</b>
                <b v-else>
                  {{showRecipients ? message.sender : senderName}}
                </b>
                <span>:  {{message.text}}</span>
              </v-flex>
              <v-spacer />
              <v-flex v-if="showRecipients" class="recipients text-right">({{message.recipients.join(', ')}})</v-flex>
            </v-layout>
          </slot>
        </v-flex>
      </v-layout>
      <v-spacer />
      <v-form ref="form" @submit.prevent="() => {}">
        <v-layout column class="chat-input">
          <v-text-field
            validate-on-blur
            placeholder="Write a message..."
            :rules="textRules"
            append-outer-icon="mdi-send"
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
    </v-layout>
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
      }
    },
    data: function () {
      return {
        text: '',
        recipients: [] as string[],
        messageLimit: 10,
        maxLength: 255,
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
      	return messages.slice(-this.messageLimit)
      } 
    }
  })
</script>

<style lang="sass" scoped>
  .messsages
    overflow: auto
</style>