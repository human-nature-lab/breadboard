import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import BBMain from '../src/components/BBMain.vue'

// Replace the real children with identifiable stubs so we can assert *which*
// view BBMain selects for a given player._system shape. `v-main` must render
// its slot, otherwise nothing inside it would mount.
const stubs = {
  'v-main': { template: '<div class="v-main"><slot /></div>' },
  PlayerTimers: { template: '<div class="player-timers" />' },
  WaitingRoom: { template: '<div class="waiting-room" />' },
  FinishProlific: { template: '<div class="finish-prolific" />' },
  FinishMturk: { template: '<div class="finish-mturk" />' },
  FinishDefault: { template: '<div class="finish-default" />' },
}

function mountWith (player: any) {
  return mount(BBMain, {
    propsData: { player },
    slots: { default: '<div class="game-content" />' },
    stubs,
  })
}

describe('BBMain view gating', () => {
  it('shows the waiting room while _system.waitingRoom is present', () => {
    const w = mountWith({ _system: { waitingRoom: { state: 'waiting-room' } } })
    expect(w.find('.waiting-room').exists()).toBe(true)
    expect(w.find('.game-content').exists()).toBe(false)
    expect(w.find('.finish-prolific').exists()).toBe(false)
    // Timers are shown during the waiting room (showTimers depends on isWaitingRoom).
    expect(w.find('.player-timers').exists()).toBe(true)
  })

  it('routes a completed prolific player to FinishProlific', () => {
    // _system.status gates the finish view; recruitment.source only selects which finish screen.
    const w = mountWith({ _system: { status: 'completed', recruitment: { source: 'prolific' } } })
    expect(w.find('.finish-prolific').exists()).toBe(true)
    expect(w.find('.finish-mturk').exists()).toBe(false)
    expect(w.find('.finish-default').exists()).toBe(false)
  })

  it('routes a completed mturk player to FinishMturk (source lives under recruitment)', () => {
    const w = mountWith({ _system: { status: 'completed', recruitment: { source: 'mturk' } } })
    expect(w.find('.finish-mturk').exists()).toBe(true)
    expect(w.find('.finish-prolific').exists()).toBe(false)
    expect(w.find('.finish-default').exists()).toBe(false)
  })

  it('falls back to FinishDefault for a completed player with another source', () => {
    const w = mountWith({ _system: { status: 'completed', recruitment: { source: 'other' } } })
    expect(w.find('.finish-default').exists()).toBe(true)
    expect(w.find('.finish-prolific').exists()).toBe(false)
    expect(w.find('.finish-mturk').exists()).toBe(false)
  })

  it('renders the default slot (the game) when neither waiting nor complete', () => {
    // recruitment present but not completed -> still in the game
    const w = mountWith({ step: 3, _system: { recruitment: { source: 'prolific' } } })
    expect(w.find('.game-content').exists()).toBe(true)
    expect(w.find('.waiting-room').exists()).toBe(false)
    expect(w.find('.finish-prolific').exists()).toBe(false)
  })

  it('ignores the legacy _system.stage / _system.source fields (regression)', () => {
    // The old code gated on these, but the backend never writes them; only
    // _system.waitingRoom and _system.recruitment.source are real.
    const w = mountWith({ _system: { stage: 'waiting-room', source: 'mturk' } })
    expect(w.find('.waiting-room').exists()).toBe(false)
    expect(w.find('.finish-mturk').exists()).toBe(false)
    expect(w.find('.game-content').exists()).toBe(true)
  })
})
