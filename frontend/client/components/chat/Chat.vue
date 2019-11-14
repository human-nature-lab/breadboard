<template>
  <v-card class="chat-box">
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
  </v-card>
</template>

<script lang="ts">
  import Vue from 'vue'
  import { Message } from './chat.types'

  export default Vue.extend({
    props: ['player'],
    data: function () {
      return {
        text: '',
        recipients: [],
        messageLimit: 10,
        maxLength: 255,
        selectedRecipients: [],
        textRules: [(text: string) => !!text || 'Enter a message'],
        recipientRules: [(vals: string[]) => !!vals.length || 'Select at least one recipient']
      }
    },
    created () {
      console.log('created')
    },
    beforeDestroy () {
      console.log('beforeDestroy')
    },
    watch: {
      'player.textInput' () {
        this.recipients = this.player.textInput.allowedRecipients
        if (!this.selectedRecipients.length) {
          this.selectedRecipients = this.recipients.slice()
        }
        this.messageLimit = this.player.textInput.messageBufferSize
        this.maxLength = this.player.textInput.maxLength
      }
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
      myId (): string | null {
      	return this.player ? this.player.id : null
      },
      messages (): Message[]  {
        const messages = this.player && this.player.textInput ? this.player.textInput.messages : []
      	return messages.slice(-this.messageLimit)
      } 
    }
  })
</script>

<style lang="sass">
  
</style>