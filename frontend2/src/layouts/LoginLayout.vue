<script setup lang="ts">
import TopNavbar from '@/components/TopNavbar.vue'
</script>

<template>
  <div class="flex flex-col h-screen bg-gray-100">
    <TopNavbar  />
    <div class="flex-1 overflow-auto">
      <div class="p-8 text-gray-700">
        <slot />
      </div>
    </div>
  </div>
</template>