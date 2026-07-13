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

  // Each feature is applied at most once, regardless of whether it was
  // requested via the static `opts` (frontend-driven) or via the backend
  // (configureFrontend, which writes to player._system.frontend) and how many
  // times the player payload updates.
  const applied = {
    prolific: false,
    trackScreen: false,
    forceSubmit: false,
  }
  let screenUpdater: (() => Record<string, any>) | null = null

  function applyProlific (p: PlayerData) {
    if (applied.prolific) return
    applied.prolific = true
    ensureProlificParams(() => p)
  }

  function applyTrackScreen (p: PlayerData) {
    if (applied.trackScreen) return
    applied.trackScreen = true
    screenUpdater = trackScreen(data => {
      console.log('trackScreen', data)
      Breadboard.send('screen-tracker', data)
    },
    {
      'game-card': '#game-card',
    },
    )
  }

  function applyForceSubmit () {
    if (applied.forceSubmit) return
    applied.forceSubmit = true
    // Read the live player ref, not a captured snapshot: usePlayer reassigns player.value to a new
    // object when immediatelySubmitCode first arrives (see registerForceSubmitEvent).
    registerForceSubmitEvent(() => player.value)
  }

  // Re-send the current screen info whenever the player advances a step. Only
  // meaningful once screen tracking has been turned on (screenUpdater is set).
  watch(() => player.value && player.value.step, step => {
    if (step && screenUpdater) {
      const data = screenUpdater()
      data.change = 'step-change'
      data.step = step
      console.log('screen-tracker', 'step-change', data)
      Breadboard.send('screen-tracker', data)
    }
  })

  // Apply the static, frontend-supplied opts as soon as the player connects.
  const stopInitWatch = watch(player, p => {
    if (!p) return
    if (opts.prolific) applyProlific(p)
    if (opts.trackScreen) applyTrackScreen(p)
    if (opts.forceSubmit) applyForceSubmit()
    stopInitWatch()
  }, { immediate: true })

  // React to backend-driven configuration written by configureFrontend, which
  // lands on the player as player._system.frontend. This may arrive (or change)
  // at any point during the session, so unlike the init watch it stays active.
  watch(() => player.value?._system?.frontend, frontend => {
    const p = player.value
    if (!p || !frontend) return
    if (frontend.prolific) applyProlific(p)
    if (frontend.trackScreen) applyTrackScreen(p)
    if (frontend.forceSubmit) applyForceSubmit()
  }, { immediate: true, deep: true })

  if (opts.kick) {
    watchKick(player)
  }
}
