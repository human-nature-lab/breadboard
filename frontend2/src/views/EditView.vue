<script setup lang="ts">
import DashboardLayout from '@/layouts/DashboardLayout.vue'
import { useSocket } from '@/services/useWebsocket'

const { socket, loading, error, state } = useSocket()
</script>

<template>
  <DashboardLayout>
    <div v-if="loading">Loading...</div>
    <div v-else-if="error">Error: {{ error.message }}</div>
    <div v-else>{{ state }}</div>
    <div class="container mx-auto">
      <h1 class="text-2xl font-bold mb-4">Edit View</h1>
      <div class="bg-white rounded-lg shadow p-6">
        <!-- Edit view content will go here -->
      </div>
    </div>
  </DashboardLayout>
</template> 