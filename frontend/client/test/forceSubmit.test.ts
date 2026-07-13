import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { ref, nextTick } from 'vue'
import { registerForceSubmitEvent } from '../src/lib/prolific'

// registerForceSubmitEvent redirects by assigning window.location.href; stub location so the
// assignment is observable and doesn't trigger a real (jsdom) navigation.
beforeEach(() => {
  vi.stubGlobal('location', { href: '' })
})
afterEach(() => {
  vi.unstubAllGlobals()
})

describe('registerForceSubmitEvent', () => {
  it('redirects to the Prolific submit URL when immediatelySubmitCode first appears', async () => {
    // usePlayer.patchPlayer swaps player.value for a fresh object the first time a brand-new key
    // appears -- reproduce that here (whole-object reassignment) to prove the watch survives it.
    const player = ref<any>({ _system: { status: 'completed' } })
    registerForceSubmitEvent(() => player.value)
    await nextTick()
    expect(window.location.href).toBe('')

    player.value = { ...player.value, immediatelySubmitCode: 'CC123' }
    await nextTick()
    expect(window.location.href).toBe('https://app.prolific.com/submissions/complete?cc=CC123')
  })

  it('redirects immediately if the code is already present on mount', async () => {
    const player = ref<any>({ immediatelySubmitCode: 'CCNOW' })
    registerForceSubmitEvent(() => player.value)
    await nextTick()
    expect(window.location.href).toBe('https://app.prolific.com/submissions/complete?cc=CCNOW')
  })

  it('does nothing while there is no code (including a null player)', async () => {
    const player = ref<any>(null)
    registerForceSubmitEvent(() => player.value)
    await nextTick()
    expect(window.location.href).toBe('')

    player.value = { _system: { status: 'completed' } }
    await nextTick()
    expect(window.location.href).toBe('')
  })
})
