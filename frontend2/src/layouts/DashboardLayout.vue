<script setup lang="ts">
import { RouterLink } from 'vue-router'
import { ref } from 'vue'
import TopNavbar from '@/components/TopNavbar.vue'

const isCollapsed = ref(false)

const menuItems = ref([
  {
    label: 'Edit',
    icon: 'pi pi-pencil',
    to: '/'
  },
  {
    label: 'Launch',
    icon: 'pi pi-play',
    to: '/launch'
  },
  {
    label: 'Watch',
    icon: 'pi pi-eye',
    to: '/watch'
  }
])
</script>

<template>
  <div class="flex flex-col h-screen bg-gray-100">
    <!-- Top Navbar -->
    <TopNavbar :showExperimentSelector="true" />

    <div class="flex flex-1 overflow-hidden">
      <!-- Sidebar -->
      <div 
        class="bg-white shadow-lg transition-all duration-300 ease-in-out"
        :class="isCollapsed ? 'w-16' : 'w-64'"
      >
        <nav class="flex flex-col h-full">
          <div class="flex-1">
            <RouterLink
              v-for="item in menuItems"
              :key="item.label"
              :to="item.to"
              class="flex items-center px-6 py-3 text-gray-700 hover:bg-gray-100 transition-colors"
              :class="{ 'bg-gray-100': $route.path === item.to }"
            >
              <i :class="(isCollapsed ? '' : 'mr-3') + ' ' + item.icon"></i>
              <span v-if="!isCollapsed">{{ item.label }}</span>
            </RouterLink>
          </div>
          <!-- Collapse toggle button -->
          <button 
            @click="isCollapsed = !isCollapsed"
            class="flex items-center px-6 py-3 text-gray-700 hover:bg-gray-100 transition-colors"
          >
            <i v-if="isCollapsed" class="pi pi-angle-right"></i>
            <i v-else class="pi pi-angle-left ml-auto"></i>
          </button>
        </nav>
      </div>

      <!-- Main Content -->
      <div class="flex-1 overflow-auto">
        <div class="p-8 text-gray-700">
          <slot />
        </div>
      </div>
    </div>
  </div>
</template>

<style>
/* Remove scoped to allow global styles */
</style>
