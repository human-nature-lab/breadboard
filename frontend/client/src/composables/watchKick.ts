import { Ref, watch } from 'vue'
import { PlayerData } from '@human-nature-lab/breadboard-core'

export function watchKick (player: Ref<PlayerData | null>) {
  watch(player, p => {
    if (p && p.kicked) {
      window.location.href = 'https://app.prolific.com'
    }
  }, { immediate: true })
}
