<script lang="ts" setup>
import { computed, watch } from 'vue'

const props = defineProps<{
  player: {
    _system?: {
      // Study-level lifecycle, the unified flag breadboard sets throughout (see groovy/util.groovy).
      status?: 'active' | 'completed' | 'kicked' | 'dropped'
      [key: string]: any
    }
    // Server-driven force-submit signal: recruitment.complete(..., kickAfter: N) stamps the Prolific
    // completion code here when the kick timer fires (see groovy/recruitment.groovy).
    immediatelySubmitCode?: string
  }
  hideTimers?: boolean
}>()

// Force-submit redirect, wired here (not only in the useBreadboard composable) so the DEFAULT client
// -- which mounts BBMain but never calls useBreadboard -- honors it too. Armed per-study by the backend
// via configureFrontend(..., forceSubmit: true) -> _system.frontend.forceSubmit; triggered when the
// kick timer stamps immediatelySubmitCode. Watches the code value (not object identity) so it fires
// however the player payload updates. Prolific-specific by construction: only Prolific completions
// carry a completion code.
watch(() => props.player?.immediatelySubmitCode, code => {
  if (code && props.player?._system?.frontend?.forceSubmit) {
    window.location.href = `https://app.prolific.com/submissions/complete?cc=${code}`
  }
}, { immediate: true })

const isWaitingRoom = computed(() => {
  // The waiting room writes player._system.waitingRoom (with a .state sub-field)
  // and removes it when the player leaves; its presence is what gates the view.
  return !!props.player?._system?.waitingRoom
})
const recruitment = computed(() => {
  return props.player?._system?.recruitment
})
const isComplete = computed(() => {
  // Study-level lifecycle drives the finish view; the recruitment.* detail below only chooses WHICH
  // finish screen (prolific/mturk/default).
  return props.player?._system?.status === 'completed'
})
const isProlific = computed(() => {
  return recruitment.value?.source === 'prolific'
})
const isMTurk = computed(() => {
  return recruitment.value?.source === 'mturk'
})
const showTimers = computed(() => {
  return (isWaitingRoom.value || isComplete.value) && !props.hideTimers
})
</script>

<template>
  <v-app>
    <v-main>
      <PlayerTimers v-if="showTimers" :player="props.player" />
      <WaitingRoom v-if="isWaitingRoom" :player="props.player" />
      <template v-else-if="isComplete">
        <FinishProlific v-if="isProlific" :player="props.player" />
        <FinishMturk v-else-if="isMTurk" :player="props.player" />
        <FinishDefault v-else :player="props.player" />
      </template>
      <template v-else>
        <slot />
      </template>
    </v-main>
  </v-app>
</template>
