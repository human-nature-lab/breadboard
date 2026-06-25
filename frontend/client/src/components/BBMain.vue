<script lang="ts" setup>
import { computed } from 'vue'

const props = defineProps<{
  player: {
    _system?: {
      // Study-level lifecycle, the unified flag breadboard sets throughout (see groovy/util.groovy).
      status?: 'active' | 'completed' | 'kicked' | 'dropped'
      [key: string]: any
    }
  }
  hideTimers?: boolean
}>()

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
