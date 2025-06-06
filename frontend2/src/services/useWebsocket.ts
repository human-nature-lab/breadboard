import { ref, watch } from 'vue'
import type { User, Experiment, ExperimentInstance } from '../types/models'
import { useState } from './useApi'
import { Socket } from '@/lib/socket'


export type LoginAction = {
  action: 'Login'
  uid: string
}

export type LoginResponse = {
  user: User
  experiment: Experiment
  experimentInstance: ExperimentInstance
}

export function useSocket() {
  const { state } = useState()
  const socket = ref<Socket | null>(null)
  const loading = ref(false)
  const error = ref<Error | null>(null)

  const connect = async () => {
    if (!state.value?.connectSocket) {
      return
    }
    loading.value = true
    try {
      socket.value = new Socket(state.value?.connectSocket)
    } catch (err) {
      error.value = err as Error
    } finally {
      loading.value = false
    }
  }

  watch(state, () => {
    if (state.value?.connectSocket) {
      connect()
    }
  })

  socket.value?.onOpen.add(() => {
    socket.value.send()
  })

  return {
    socket,
    state,
    loading,
    error,
    connect,
  }
}