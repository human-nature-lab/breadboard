<script lang="ts" setup>
import { computed } from 'vue'

const props = defineProps<{
  player: {
    _system?: {
      recruitment?: {
        message?: string
      }
    }
  }
}>()

const data = computed(() => props.player._system?.recruitment)
</script>

<template>
  <div>
    <p v-html="data?.message" />
  </div>
</template>
