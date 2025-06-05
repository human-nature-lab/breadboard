<script setup lang="ts">
import LoginLayout from '@/layouts/LoginLayout.vue'
import Card from 'primevue/card';
import LoginForm from '@/components/LoginForm.vue';
import { useRouter } from 'vue-router';

const router = useRouter()

function redirectToNext() {
  if (router.currentRoute.value.params.to) {
    router.push(router.currentRoute.value.params.to as string)
  } else {
    router.push('/')
  }
}
</script>

<template>
  <LoginLayout>
    <Card>
      <template #title>Login</template>
      <template #content>
        <LoginForm @authorized="redirectToNext" />
      </template>
    </Card>
  </LoginLayout>
</template>

<style scoped>

</style>
