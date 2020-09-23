<template>
  <v-container style="background: #ebebeb" class="h-screen">
    <v-row class="px-4 justify-space-between">
      <b class="">
        Step: {{player.step}}
      </b>
      <div class="">
        Current round: {{player.curRound}}
      </div>
    </v-row>
    <v-col>
      <v-data-table
        v-model="selectedPlayers"
        no-data-text="Waiting for players to join..."
        item-value="id"
        item-key="id"
        :items="player.players"
        show-select
        hide-default-footer
        :items-per-page="player && player.players && player.players.length"
        :headers="playerHeaders"></v-data-table>
    </v-col>
    <v-row>
      <v-btn
        :disabled="player.step !== 'Loading'"
        @click="initGame">
        Start game
      </v-btn>
      <v-btn
        :disabled="player.step !== 'Loading' || !selectedPlayers.length"
        @click="makeGroup">
        Assign group
      </v-btn>
      <v-btn
        :disabled="['Loading', 'Complete'].includes(player.step)"
        @click="sendContinue">Continue</v-btn>
      <v-btn
        :disabled="player.step !== 'Results'"
        @click="sendDistribute">Distribute</v-btn>
    </v-row>
  </v-container>
</template>

<script lang="ts">
  import Vue from 'vue'

  export default Vue.extend({
    name: 'Admin',
    props: {
      player: Object
    },
    data () {
      return {
        selectedPlayers: [] as { id: string }[],
        playerHeaders: [{
          value: 'id',
          text: 'Player'
        }, {
          value: 'step',
          text: 'Step'
        }, {
          value: 'groupId',
          text: 'Group'
        }, {
          value: 'score',
          text: 'Total earned'
        }, {
          value: 'contributing',
          text: 'Contribution'
        }, {
          value: 'keeping',
          text: 'Keeping'
        }, {
          value: 'groupPayout',
          text: 'Individual payout'
        }, {
          value: 'totalPool',
          text: 'Group total'
        }]
      }
    },
    methods: {
      sendContinue () {
        window.Breadboard.send('continue')
      },
      sendDistribute () {
        window.Breadboard.send('distribute')
      },
      makeGroup () {
        window.Breadboard.send('group', this.selectedPlayers.map(p => p.id))
        this.selectedPlayers = []
      },
      initGame () {
        if (!this.allPlayersInGroup) {
          return alert('All players must be assigned to a group before you can start!')
        }
        window.Breadboard.send('init', {})
      }
    },
    computed: {
      allPlayersInGroup (): boolean {
        return this.player.players.findIndex((p: any) => !p.groupId) === -1
      }
    }
  })
</script>

<style lang="sass">
  
</style>