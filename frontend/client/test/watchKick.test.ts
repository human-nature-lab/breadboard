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
  it('redirects when _system.status flips to kicked', async () => {
    const player = ref<any>({ _system: { status: 'active' } })
    watchKick(player)
    await nextTick()
    expect(window.location.href).toBe('')

    player.value._system.status = 'kicked'
    await nextTick()
    expect(window.location.href).toBe('https://app.prolific.com')
  })

  it('redirects immediately if status is already kicked on mount', async () => {
    const player = ref<any>({ _system: { status: 'kicked' } })
    watchKick(player)
    await nextTick()
    expect(window.location.href).toBe('https://app.prolific.com')
  })

  it('ignores a top-level player.status field (regression)', async () => {
    // The lifecycle lives at _system.status, not a top-level status flag.
    const player = ref<any>({ status: 'kicked', _system: { status: 'active' } })
    watchKick(player)
    await nextTick()
    expect(window.location.href).toBe('')
  })
})
