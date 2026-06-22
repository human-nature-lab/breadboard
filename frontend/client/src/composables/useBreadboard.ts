import { Ref, watch } from 'vue'
import { PlayerData } from '@human-nature-lab/breadboard-core'
import { ensureProlificParams, registerForceSubmitEvent } from '../lib/prolific'
import { trackScreen } from '@/lib/screen-tracker'
import { watchKick } from './watchKick'

type Opts = {
  kick?: boolean
  prolific?: boolean
  trackScreen?: boolean
  forceSubmit?: boolean
}

export function useBreadboard (player: Ref<PlayerData | null>, opts: Opts = {}) {
  Breadboard.login()
  Breadboard.on('open', () => {
    Breadboard.login()
  })
  let screenUpdater: (() => Record<string, any>) | null = null
  if (opts.trackScreen) {
    watch(() => player.value && player.value.step, step => {
      if (step && screenUpdater) {
        const data = screenUpdater()
        data.change = 'step-change'
        data.step = step
        console.log('screen-tracker', 'step-change', data)
        Breadboard.send('screen-tracker', data)
      }
    })
  }
  const cancelWatch = watch(player, p => {
    if (p) {
      if (opts.prolific) {
        ensureProlificParams(() => p)
      }
      if (opts.trackScreen) {
        screenUpdater = trackScreen(data => {
          console.log('trackScreen', data)
          Breadboard.send('screen-tracker', data)
        },
        {
          'game-card': '#game-card',
        },
        )
      }
      if (opts.forceSubmit) {
        registerForceSubmitEvent(() => p)
      }
      cancelWatch()
    }
  }, { immediate: true })

  if (opts.kick) {
    watchKick(player)
  }
}
