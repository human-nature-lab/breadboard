import { ref, Ref } from 'vue'
import { PlayerData } from '@human-nature-lab/breadboard-core'

export function usePlayer () {
  const player = ref<PlayerData | null>(null)
  const loading = ref(true)
  const error = ref<Error | null>(null)
  const config = ref<Record<string, any> | null>(null)

  Breadboard.loadConfig().then((c) => {
    config.value = c as Record<string, any>
  })

  Breadboard.on('player', async (newPlayer: any) => {
    patchPlayer(player, newPlayer)
    if (player.value && config.value && !player.value.id) {
      player.value.id = config.value.clientId
      loading.value = false
    }
  })

  return { player, config, loading, error }
}

function log (...args: any[]) {
  // console.log('usePlayer', ...args)
}

function patchPlayer (player: Ref<PlayerData | null>, newPlayer: PlayerData) {
  if (!player.value) {
    log('patched full player', newPlayer)
    player.value = newPlayer
    return
  }
  let needsReassigned = false
  for (const key in newPlayer) {
    if (!(key in player.value)) {
      needsReassigned = true
      break
    }
    if (!deepEqual(newPlayer[key], player.value?.[key])) {
      log('patching key', key)
      player.value[key] = newPlayer[key]
    }
  }
  if (needsReassigned) {
    log('patched full player', newPlayer)
    player.value = newPlayer
  }
}

function deepEqual (a: any, b: any) {
  if (a === b) return true

  if (typeof a !== 'object' || a === null ||
      typeof b !== 'object' || b === null) {
    return false
  }

  const keysA = Object.keys(a)
  const keysB = Object.keys(b)

  if (keysA.length !== keysB.length) return false

  for (const key of keysA) {
    if (!keysB.includes(key) || !deepEqual(a[key], b[key])) {
      return false
    }
  }
  return true
}
