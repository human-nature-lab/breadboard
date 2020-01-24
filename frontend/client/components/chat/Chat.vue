<template>
  <v-card class="chat-box" v-if="chatState">
    <v-layout column>
      <v-layout column class="messages">
        <v-flex v-for="message in messages" class="message" :key="message.id">
          <v-layout>
            <v-flex>
              <b>{{message.sender === myId ? 'Me' : message.sender}}</b>:  {{message.text}}
            </v-flex>
            <v-spacer />
            <v-flex class="recipients text-right">({{message.recipients.join(', ')}})</v-flex>
          </v-layout>
        </v-flex>
      </v-layout>
      <v-layout column class="chat-input">
        <v-form ref="form">
          <v-text-field
            validate-on-blur
            placeholder="Write a message..."
            :rules="textRules"
            @keyup.enter="send"
            append-outer-icon="mdi-send"
            :counter="maxLength"
            @click:append-outer="send"
            v-model="text">
          </v-text-field>
          <v-select 
              chips
              clearable
              multiple
              small-chips
              deletable-chips
              label="Recipients"
              :rules="recipientRules"
              :items="recipients" 
              v-model="selectedRecipients" />
        </v-form>
      </v-layout>
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