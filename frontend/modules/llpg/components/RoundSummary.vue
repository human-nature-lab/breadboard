<template>
  <v-dialog lazy :value="value" @input="$emit('input', $event)">
    <v-card>
      <v-container fluid>
        <v-row class="text-h5">
          Rounds
          <v-spacer></v-spacer>
          <v-btn :disbled="isLoading" @click="download">Download</v-btn>
        </v-row>
        <v-data-table 
          :loading="isLoading"
          :headers="roundHeaders"
          :items="roundRows" />
      </v-container>
    </v-card>
  </v-dialog>
</template>

<script lang="ts">
  /* global Papa saveAs */
  import Vue, { PropOptions } from 'vue'

  export default Vue.extend({
    name: 'RoundSummary',
    props: {
      value: Boolean,
      players: Array as PropOptions<object[]>,
    },
    data () {
      return {
        isLoading: false,
        libsLoaded: false,
        cols: ['keeping', 'contributing'],
        rounds: {} as Record<string, Record<string, Record<string, number>>>,
      }
    },
    created () {
       window.Breadboard.on('player-data', data => {
        this.isLoading = false
        this.rounds = data.rounds
      })
    },
    watch: {
      value (val: boolean) {
        if (val) {
          this.isLoading = true
          window.Breadboard.send('player-data')
        }
      }
    },
    methods: {
      async download () {
        if (!this.libsLoaded) {
          await Promise.all([
            window.Breadboard.addScriptFromURL('https://cdnjs.cloudflare.com/ajax/libs/FileSaver.js/2.0.0/FileSaver.min.js'),
            window.Breadboard.addScriptFromURL('https://cdnjs.cloudflare.com/ajax/libs/PapaParse/5.3.1/papaparse.js')
          ])
        }
        this.libsLoaded = true
        // @ts-ignore
        const csv = Papa.unparse(this.roundRows)
        // @ts-ignore
        saveAs(new Blob([csv], { type: 'text/csv;charset=utf-8' }), 'round-data.csv')
      }
    },
    computed: {
      playersMap (): Map<string, Record<string, any>> {
        const m = new Map<string, Record<string, any>>()
        for (const player of this.players) {
          m.set(player.id, player)
        }
        return m
      },
      roundRows (): object[] {
        const rows = []
        const pMap = this.playersMap
        const playerIds: string[] = pMap.keys()
        for (const id of Array.from(playerIds)) {
          const row: Record<string, any> = { player: id }
          const player = pMap.get(id)
          row.groupId = player.groupId
          row.groupName = player.groupName
          row.total = 0
          for (const round in this.rounds) {
            const d = this.rounds[round][id]
            if (d) {
              row[`Round ${round}`] = `Kept: ${d.keeping}, Received: ${d.payout}`
              row.total += d.keeping + d.payout
            }
          }
          rows.push(row)
        }
        return rows
      },
      roundHeaders (): object[] {
        const headers = [{
          text: 'Player',
          value: 'player'
        }, {
          text: 'Group',
          value: 'groupId'
        }, {
          text: 'Location',
          value: 'groupName',
        }]
        for (const round in this.rounds) {
          headers.push({
            text: `Round ${round}`,
            value: `Round ${round}`
          })
        }
        headers.push({
          text: 'Total',
          value: 'total'
        })
        return headers
      }
    }
  })
</script>

<style lang="sass">
  
</style>