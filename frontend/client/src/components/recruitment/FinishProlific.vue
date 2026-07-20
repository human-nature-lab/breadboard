<script lang="ts" setup>
import { computed, ref } from 'vue'
import { PlayerData } from '@human-nature-lab/breadboard-core'
import PlayerText from '../PlayerText.vue'

const props = defineProps<{
  player: PlayerData
}>()

const data = computed(() => props.player._system?.recruitment)

const submitUrl = computed(() => {
  return `https://app.prolific.com/submissions/complete?cc=${data.value?.completionCode}`
})

const feedback = ref('')
// Flips true once the participant clicks Finish, so we can reveal the manual completion link as a
// fallback in case the automatic redirect below is blocked (e.g. a pop-up/navigation blocker).
const submitted = ref(false)
function submit() {
  submitted.value = true
  Breadboard.send('exiting', { feedback: feedback.value })
  setTimeout(() => {
    window.location.href = submitUrl.value
  }, 1000)
}
</script>

<template>
  <v-container class="form-width">
    <PlayerText :player="player" />
    <p v-html="data.message" />
    <h3 class="py-4">Completion code: {{ data.completionCode }}</h3>
    <v-textarea
      v-if="!data.noFeedback"
      v-model="feedback"
      label="Feedback (optional)"
      placeholder="Have any comments about the study? Let us know here before you finish."
      solo
    />
    <v-btn @click="submit" :disabled="submitted"> Finish </v-btn>
    <p v-if="submitted">
      Click on this link if you're not automatically redirected:
      <a :href="submitUrl">{{ submitUrl }}</a>
    </p>
  </v-container>
</template>
