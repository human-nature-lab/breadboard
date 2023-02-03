<template>
  <v-container style="background: #ebebeb" class="h-screen">
    <v-row class="px-4 justify-space-between">
      <b class="px-3">
        Step: {{player.step}}
      </b>
      <div class="px-3">
        Current round: {{player.curRound}}
      </div>
      <v-btn text small @click="roundsVisible = true">
        Results
        <v-icon class="ml-2">mdi-clipboard-list</v-icon>
      </v-btn>
      <v-spacer />
      <TimeoutButton
        :disabled="player.step !== 'Loading'"
        @click="initGame">
        Start game
      </TimeoutButton>
      <v-btn
        :disabled="player.step !== 'Loading' || !selectedPlayers.length"
        @click="groupVisible = true">
        Assign group
      </v-btn>
      <v-btn
        :disabled="!canDeactivate"
        @click="deactivateUsers">
        Deactivate users
      </v-btn>
      <v-btn
        :disabled="!selectedInactive.length"
        @click="activateUsers">
        Activate users
      </v-btn>
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
      <TimeoutButton
        :disabled="!canContinue"
        @click="sendContinue">
        Continue
      </TimeoutButton>
      <TimeoutButton
        :disabled="player.step !== 'Results'"
        :duration="11000"
        @click="sendDistribute">
        Distribute
      </TimeoutButton>
    </v-row>
    <RoundSummary v-model="roundsVisible" :players="player.players" />
    <v-dialog v-model="groupVisible">
      <v-card>
        <v-container class="fluid">
          <v-select 
            v-model="group"
            label="Location"
            :items="['A', 'B', 'C', 'D']" />
          <v-btn 
            :disabled="player.step !== 'Loading' || !selectedPlayers.length" 
            @click="makeGroup">
            Assign Group
          </v-btn>
        </v-container>
      </v-card>
    </v-dialog>
  </v-container>
</template>

<script lang="ts">
  import Vue from 'vue'
  import RoundSummary from '../components/RoundSummary.vue'

  type Player = { id: string, active: boolean }

  export default Vue.extend({
    name: 'Admin',
    components: { RoundSummary },
    props: {
      player: Object
    },
    data () {
      return {
        selectedPlayers: [] as Player[],
        roundsVisible: false,
        groupVisible: false,
        group: null,
        playerHeaders: [{
          value: 'id',
          text: 'Player'
        }, {
          value: 'active',
          text: 'Active'
        }, {
          value: 'step',
          text: 'Step'
        }, {
          value: 'groupId',
          text: 'Group'
        }, {
          value: 'groupName',
          text: 'Location'
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
      deactivateUsers () {
        window.Breadboard.send('deactivate', this.selectedPlayers.map(p => p.id))
        this.selectedPlayers = []
      },
      activateUsers () {
        window.Breadboard.send('activate', this.selectedPlayers.map(p => p.id))
        this.selectedPlayers = []
      },
      makeGroup () {
        window.Breadboard.send('group', {
          players: this.selectedPlayers.map(p => p.id),
          group: this.group
        })
        this.selectedPlayers = []
        this.groupVisible = false
      },
      initGame () {
        if (!this.allPlayersInGroup) {
          return alert('All players must be assigned to a group before you can start!')
        }
        window.Breadboard.send('init', {})
      },
      reload () {
        window.Breadboard.sendType('ReloadEngine', { uid: '' })
      },
      allPlayersInStep (step: string): boolean {
        for (const player of this.player.players) {
          if (player.active) {
            if (player.step !== step) {
              return false
            }
          }
        }
        return true
      }
    },
    computed: {
      canDeactivate (): boolean {
        return !!this.selectedActive.length && ['Distributed', 'Loading'].includes(this.player.step)
      },
      canActivate (): boolean {
        return !!this.selectedInactive && ['Distributed', 'Loading'].includes(this.player.step)
      },
      allPlayersInGroup (): boolean {
        return this.player.players.findIndex((p: any) => !p.groupId && p.active) === -1
      },
      canContinue (): boolean {
        if (this.player.step === 'Decision' && this.allPlayersInStep('PostDecision') || this.allPlayersInStep('PracticeComplete')) {
          return true
        } else {
          return ['Distributing', 'Distributed'].includes(this.player.step)
        }
      },
      nextStep (): string {
        const steps = ['Decision', 'Results', 'Distributing', 'Distributed']
        const index = steps.indexOf(this.player.step)
        return steps[(index + 1) % steps.length]
      },
      selectedActive (): Player[] {
        return this.selectedPlayers.filter(p => p.active)
      },
      selectedInactive (): Player[] {
        return this.selectedPlayers.filter(p => !p.active)
      }
    }
  })
</script>

<style lang="sass">
  
</style>