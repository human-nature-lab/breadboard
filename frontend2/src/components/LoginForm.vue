<script setup lang="ts">
import { ref } from 'vue'
import InputText from 'primevue/inputtext';
import Button from 'primevue/button';
import { login } from '@/services/api';
import { useRouter } from 'vue-router';

const router = useRouter()
const email = ref('')
const password = ref('')

const emit = defineEmits<{
  (e: 'authorized'): void
}>()

async function handleLogin() {
  await login({
    email: email.value,
    password: password.value,
  })
  emit('authorized')
}
</script>

<template>
  <form @submit.prevent="handleLogin">
    <div class="field">
      <label for="email">Email</label>
      <InputText id="email" v-model="email" class="w-full" />
    </div>
    <div class="field">
      <label for="password">Password</label>
      <InputText 
        id="password" 
        v-model="password" 
        class="w-full"
        type="password"
      />
    </div>
    <Button type="submit" label="Login" class="mt-2" />
  </form>
</template>