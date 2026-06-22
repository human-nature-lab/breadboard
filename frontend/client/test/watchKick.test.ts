import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { ref, nextTick } from 'vue'
import { watchKick } from '../src/composables/watchKick'

// watchKick redirects by assigning window.location.href; stub location so the
// assignment is observable and doesn't trigger a real (jsdom) navigation.
beforeEach(() => {
  vi.stubGlobal('location', { href: '' })
})
afterEach(() => {
  vi.unstubAllGlobals()
})

describe('watchKick', () => {
  it('redirects when _system.frontend.kicked flips to true', async () => {
    const player = ref<any>({ _system: { frontend: { kicked: false } } })
    watchKick(player)
    await nextTick()
    expect(window.location.href).toBe('')

    player.value._system.frontend.kicked = true
    await nextTick()
    expect(window.location.href).toBe('https://app.prolific.com')
  })

  it('redirects immediately if kicked is already true on mount', async () => {
    const player = ref<any>({ _system: { frontend: { kicked: true } } })
    watchKick(player)
    await nextTick()
    expect(window.location.href).toBe('https://app.prolific.com')
  })

  it('ignores the legacy top-level player.kicked field (regression)', async () => {
    // The backend writes _system.frontend.kicked, not a top-level kicked flag.
    const player = ref<any>({ kicked: true, _system: { frontend: {} } })
    watchKick(player)
    await nextTick()
    expect(window.location.href).toBe('')
  })
})
