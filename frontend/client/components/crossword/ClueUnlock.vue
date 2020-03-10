<template>
  <v-flex v-if="!unlocked">
    <v-alert color="error" v-show="invalidPass">
      The password was invalid
    </v-alert>
    <v-text-field 
        v-model="password"
        :disabled="isBusy"
        label="Password"
        :loading="isBusy"
        type="password"
        dense
        solo
        @keydown.enter="attempt">
      <template v-slot:append>
        <v-btn :disabled="isBusy" :loading="isBusy" @click="attempt">
          Unlock
        </v-btn>
      </template>
    </v-text-field>
  </v-flex>
</template>

<script lang="ts">
  import Vue from 'vue'

  const RESPONSE_KEY = 'unlock-response'
  export default Vue.extend({
    name: 'ClueUnlock',
    props: {
      unlocked: {
        type: Boolean,
        default: false
      },
      errorDuration: {
        type: Number,
        default: 5000
      },
      type: {
        type: String,
        required: true
      }
    },
    data () {
      return {
        password: '',
        invalidPass: false,
        isBusy: false
      }
    },
    beforeDestroy () {
      window.Breadboard.off(RESPONSE_KEY, this.unlockResponse)
    },
    methods: {
      unlockResponse (data: { success: boolean }) {
        window.Breadboard.off(RESPONSE_KEY, this.unlockResponse)
        setTimeout(() => {
          this.isBusy = false
          this.password = ''
          if (!data.success) {
            this.invalidPass = true
            setTimeout(() => {
              this.invalidPass = false
            }, this.errorDuration)
          }
        }, 1000)
      },
      attempt () {
        if (!this.password || !this.password.length) return
        this.isBusy = true
        this.invalidPass = false
        window.Breadboard.on(RESPONSE_KEY, this.unlockResponse)
        const d = {
          password: this.password.trim(),
          type: this.type
        }
        window.Breadboard.send('unlock', d)
        this.$emit('unlock', d)
      }
    }
  })
</script>

<style lang="sass">
  
</style>