<template>
  <v-flex>
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
        prepend-inner-icon="mdi-lock"
        @keydown.enter="attempt">
      <template v-slot:append>
        <v-btn :disabled="isBusy" :loading="isBusy" @click="attempt" depressed>
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
      errorDuration: {
        type: Number,
        default: 5000
      }
    },
    data () {
      return {
        password: '',
        invalidPass: false,
        isBusy: false
      }
    },
    created () {
      this.unlockResponse = this.unlockResponse.bind(this)
      window.Breadboard.on(RESPONSE_KEY, this.unlockResponse)
    },
    beforeDestroy () {
      window.Breadboard.off(RESPONSE_KEY, this.unlockResponse)
    },
    methods: {
      unlockResponse (data: { success: boolean }) {
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
        window.Breadboard.send('unlock', {
          password: this.password
        })
      }
    }
  })
</script>

<style lang="sass">
  
</style>