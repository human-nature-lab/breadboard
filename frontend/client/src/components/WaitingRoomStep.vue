<template>
  <v-container class="prose">
    <h2>Waiting for other players…</h2>

    <p v-if="state === 'waiting-room'">
      We need {{ requiredPlayers }} players to start. So far:
      <strong>{{ Math.round(progress * requiredPlayers) }} / {{ requiredPlayers }}</strong>.
    </p>

    <p v-else-if="state === 'ready-up'">
      A group is forming! Click <em>I'm ready</em> below to join.
    </p>

    <p v-else-if="state === 'group-start'">
      Your group is about to start. Hang tight…
    </p>

    <v-progress-linear
      :value="progress * 100"
      height="12"
      class="my-4"
    />

    <v-btn
      v-if="state === 'ready-up' && !isReady"
      color="primary"
      @click="ready"
    >
      I'm ready
    </v-btn>

    <p v-if="notChosenForGroup" class="mt-4 warning--text">
      You were not chosen for the last group. You've been prioritized for the next one.
    </p>
    <p v-if="notEnoughReadyForGroup" class="mt-4 warning--text">
      Not enough players readied up last time. We'll try again.
    </p>
  </v-container>
</template>

<script>
/* global Breadboard */
export default {
  name: 'WaitingRoomStep',
  props: {
    player: { type: Object, required: true },
  },
  computed: {
    waitingRoom () {
      return (this.player._system && this.player._system.waitingRoom) || {}
    },
    state () {
      return this.waitingRoom.state || 'waiting-room'
    },
    progress () {
      return this.waitingRoom.progress || 0
    },
    isReady () {
      return !!this.waitingRoom.isReady
    },
    requiredPlayers () {
      // Set on the player by the WaitingRoom groovy class via private/_system data.
      return this.waitingRoom.requiredPlayers || '?'
    },
    notChosenForGroup () {
      return !!this.waitingRoom.notChosenForGroup
    },
    notEnoughReadyForGroup () {
      return !!this.waitingRoom.notEnoughReadyForGroup
    },
  },
  methods: {
    ready () {
      Breadboard.send('waiting-room:ready')
    },
  },
}
</script>
