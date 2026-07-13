import { describe, it, expect, beforeEach, vi } from 'vitest'
import { ref, nextTick } from 'vue'

// Mock the DOM-heavy screen tracker: stand in a spy that immediately notifies
// once (like the real "initial" emit) and returns an updater. This keeps the
// test about useBreadboard's *wiring* (when does tracking turn on, how often),
// not the tracker internals, and avoids jsdom's missing screen.orientation.
vi.mock('@/lib/screen-tracker', () => ({
  trackScreen: vi.fn((notify: (d: any) => void) => {
    notify({ change: 'initial' })
    return () => ({ change: 'step' })
  }),
}))

import { trackScreen } from '@/lib/screen-tracker'
import { useBreadboard } from '../src/composables/useBreadboard'

beforeEach(() => {
  vi.clearAllMocks()
  ;(globalThis as any).Breadboard = {
    login: vi.fn(),
    on: vi.fn(),
    send: vi.fn(),
    connect: vi.fn(),
  }
})

describe('useBreadboard', () => {
  it('logs in immediately and re-logs in on socket open', () => {
    useBreadboard(ref(null))
    expect(Breadboard.login).toHaveBeenCalled()
    expect(Breadboard.on).toHaveBeenCalledWith('open', expect.any(Function))
  })

  it('starts screen tracking when the backend sets _system.frontend.trackScreen', async () => {
    const player = ref<any>(null)
    useBreadboard(player)
    expect(trackScreen).not.toHaveBeenCalled()

    // configureFrontend on the backend lands as player._system.frontend.
    player.value = { step: 1, _system: { frontend: { trackScreen: true } } }
    await nextTick()

    expect(trackScreen).toHaveBeenCalledTimes(1)
    expect(Breadboard.send).toHaveBeenCalledWith(
      'screen-tracker',
      expect.objectContaining({ change: 'initial' }),
    )
  })

  it('honors the static trackScreen opt even without backend config', async () => {
    const player = ref<any>(null)
    useBreadboard(player, { trackScreen: true })

    player.value = { step: 1, _system: {} }
    await nextTick()

    expect(trackScreen).toHaveBeenCalledTimes(1)
  })

  it('only starts screen tracking once across later player updates', async () => {
    const player = ref<any>(null)
    useBreadboard(player)

    player.value = { step: 1, _system: { frontend: { trackScreen: true } } }
    await nextTick()
    player.value = { step: 2, _system: { frontend: { trackScreen: true } } }
    await nextTick()

    expect(trackScreen).toHaveBeenCalledTimes(1)
  })

  it('does not start screen tracking when nothing requests it', async () => {
    const player = ref<any>(null)
    useBreadboard(player)

    player.value = { step: 1, _system: { frontend: {} } }
    await nextTick()

    expect(trackScreen).not.toHaveBeenCalled()
  })

  it('force-submits (redirects) when the backend sets _system.frontend.forceSubmit', async () => {
    // configureFrontend(v, [forceSubmit: true]) lands as _system.frontend.forceSubmit; once armed, the
    // client redirects as soon as immediatelySubmitCode arrives (e.g. from a kickAfter timer).
    vi.stubGlobal('location', { href: '' })
    try {
      const player = ref<any>(null)
      useBreadboard(player)

      player.value = { _system: { frontend: { forceSubmit: true } } }
      await nextTick()
      expect(window.location.href).toBe('')            // armed, but no code yet

      // A brand-new key -> usePlayer would hand us a fresh object; mimic that whole-object swap.
      player.value = { _system: { frontend: { forceSubmit: true } }, immediatelySubmitCode: 'CC9' }
      await nextTick()
      expect(window.location.href).toBe('https://app.prolific.com/submissions/complete?cc=CC9')
    } finally {
      vi.unstubAllGlobals()
    }
  })
})
