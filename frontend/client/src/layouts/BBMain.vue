<script lang="ts" setup>
import { computed } from 'vue'

const props = defineProps<{
  player: {
    _system?: Record<string, any>
  }
  hideTimers?: boolean
}>()

const isWaitingRoom = computed(() => {
  return props.player?._system?.stage === 'waiting-room'
})
const recruitment = computed(() => {
  return props.player?._system?.recruitment
})
const isComplete = computed(() => {
  return !!recruitment.value?.completed
})
const isProlific = computed(() => {
  return recruitment.value?.source === 'prolific'
})
const isMTurk = computed(() => {
  return props.player?._system?.source === 'mturk'
})
const showTimers = computed(() => {
  return (isWaitingRoom.value || isComplete.value) && !props.hideTimers
})
</script>

<template>
  <v-main>
    <PlayerTimers v-if="showTimers" :player="props.player" />
    <WaitingRoom v-if="isWaitingRoom" :player="props.player" />
    <template v-else-if="isComplete">
      <FinishProlific v-if="isProlific" :player="props.player" />
      <FinishMTurk v-else-if="isMTurk" :player="props.player" />
      <FinishDefault v-else :player="props.player" />
    </template>
    <template v-else>
      <slot />
    </template>
  </v-main>
</template>
