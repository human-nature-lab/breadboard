<script setup lang="ts">

import ExperimentSelector from '@/components/ExperimentSelector.vue'
const props = defineProps<{
  showExperimentSelector?: boolean
}>()
</script>

<template>
  <div class="bg-white shadow-sm border-b">
    <div class="flex items-center justify-between px-4 py-2">
      <div class="flex items-center gap-4">
        <img 
          alt="Breadboard favicon" 
          class="w-8 h-8"
          src="@/assets/favicon.ico" 
        />
        <img 
          alt="Breadboard logo" 
          class="h-8"
          src="@/assets/breadboard-logo-black.svg" 
        />
      </div>
      <div v-if="showExperimentSelector" class="w-64">
        <ExperimentSelector />
      </div>
    </div>
  </div>
</template>