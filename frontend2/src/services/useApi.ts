import { onMounted, ref, watch } from "vue";
import { getState, type StateResponse } from "./api";
import { Socket } from "@/lib/socket";

export function useState() {
  const state = ref<StateResponse | null>(null)
  const loading = ref(false)
  const error = ref<Error | null>(null)

  const fetchState = async () => {
    loading.value = true
    try {
      const response = await getState()
      state.value = response
    } catch (err) {
      error.value = err as Error
    } finally {
      loading.value = false
    }
  }

  onMounted(async () => {
    await fetchState()
  })

  return {
    state,
    loading,
    error,
    fetchState,
  }
}
