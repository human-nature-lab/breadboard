<script setup lang="ts">
import { useExperimentStore } from '@/stores/experiment'
import { storeToRefs } from 'pinia'
import Dropdown from 'primevue/dropdown'

const store = useExperimentStore()
const { experiments, currentExperiment } = storeToRefs(store)
</script>

<template>
  <div class="flex items-center gap-2">
    <span class="text-sm font-medium text-gray-700">Experiment:</span>
    <Dropdown
      v-model="currentExperiment"
      :options="experiments"
      optionLabel="name"
      placeholder="Select Experiment"
      class="w-full"
      :class="{ 'p-inputtext-sm': true }"
    />
  </div>
</template> 