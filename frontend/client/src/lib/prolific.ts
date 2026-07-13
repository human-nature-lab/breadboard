import { watch } from 'vue'

type Player = Record<string, any>

export async function ensureProlificParams (source: () => Player) {
  const data: Record<string, string> = {}
  const params = new URLSearchParams(window.location.search)
  for (const [key, value] of params) {
    data[key] = value
  }

  async function sendUrlParams () {
    await Breadboard.connect()
    const id = setInterval(() => {
      const player = source()
      if (player && player.receivedUrlParams) {
        console.log('receivedUrlParams', player)
        clearInterval(id)
        return
      }
      console.log('sendingUrlParams', player)
      Breadboard.send('urlParams', data)
    }, 1000)
    // If we don't successfully send the url params within 60 seconds, just give up
    setTimeout(() => {
      clearInterval(id)
    }, 60 * 1000)
  }

  sendUrlParams()
}

export async function registerForceSubmitEvent (source: () => Player | null) {
  // Watch the code VALUE, not the player object's identity. usePlayer.patchPlayer swaps player.value
  // for a fresh object the first time a brand-new key (like immediatelySubmitCode) appears, so a watch
  // keyed on a captured player snapshot would go stale and never fire. `source()` must read the live
  // ref each call so the getter re-tracks after that reassignment.
  watch(() => source()?.immediatelySubmitCode, code => {
    if (code) {
      window.location.href = `https://app.prolific.com/submissions/complete?cc=${code}`
    }
  }, { immediate: true })
}
