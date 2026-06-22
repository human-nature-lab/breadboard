<script setup lang="ts">
const props = defineProps<{
  player: {
    _system?: {
      recruitment?: {
        source: 'mturk'
        message: string
        bonus: number
        sandbox: boolean
      }
    }
  }
}>()

const data = computed(() => props.player._system?.recruitment)
const submitUrl = computed(() =>
  data.value?.sandbox
    ? 'https://sandbox.mturk.com/mturk/externalSubmit'
    : 'https://mturk.com/mturk/externalSubmit',
)
</script>

<template>
  <v-container>
    <div v-html="data.message" />
    <v-form :action="submitUrl" method="get">
      <v-text-area
        v-if="!data.noFeedback"
        v-model="comments"
        name="comments"
        rows="5"
        cols="50"
      />
      <v-btn type="submit">Submit HIT</v-btn>
      <v-input type="hidden" name="assignmentId" :value="player.id" />
      <v-input type="hidden" name="bonus" :value="data?.bonus" />
      <v-input type="hidden" name="reason" :value="data?.reason" />
    </v-form>
  </v-container>
</template>
