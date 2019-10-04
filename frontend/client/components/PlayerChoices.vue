<template>
  <v-flex>
    <v-container>
      <v-layout row>
        <Choice :choice="choice"
                v-for="choice of player.choices"
                :key="choice.uid"
                @click="submit(choice)"/>
      </v-layout>
    </v-container>
  </v-flex>
</template>

<script lang="ts">
  import Vue from 'vue'
  import { PlayerChoice } from '../../core/breadboard.types'

  export default Vue.extend({
    name: 'PlayerChoices',
    props: {
      player: {
        type: Object,
        required: true
      }
    },
    data () {
      return {
        choicesAreEnabled: true
      }
    },
    updated () {
      this.choicesAreEnabled = true
      // console.log('player choices updated')
    },
    methods: {
      submit (choice: PlayerChoice) {
        window.Breadboard.send('MakeChoice', {
          choiceUID: choice.uid
        })
        this.choicesAreEnabled = false
      }
    }
  })
</script>
