<script lang="ts" setup>
import { computed, ref } from 'vue'
import { Breadboard } from '@human-nature-lab/core'
import PlayerText from '../PlayerText.vue'

const props = defineProps<{
  player: {
    completionCode?: string
    submittedFeedback?: boolean
    message?: string
  }
}>()

const feedback = ref('')
function submit() {
  Breadboard.send('exiting', { feedback: feedback.value })
  setTimeout(() => {
    window.location.href = submitUrl
  }, 1000)
}

const data = computed(() => {
  return player._system?.recruitment
})

const submitUrl = computed(() => {
  return `https://app.prolific.com/submissions/complete?cc=${data?.completionCode}`
})
</script>

<template>
  <v-container class="form-width">
    <PlayerText :player="player" />
    <p v-html="data.message" />
    <h3 class="py-4">Completion code: {{ data.completionCode }}</h3>
    <v-text-area v-if="!data.noFeedback" v-model="feedback" solo />
    <v-btn @click="submit"> Finish </v-btn>
    <p v-if="data.submittedFeedback">
      Click on this link if you're not automatically redirected:
      <a :href="submitUrl">{{ submitUrl }}</a>
    </p>
  </v-container>
</template>
