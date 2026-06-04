import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import PlayerText from '../src/components/PlayerText.vue'

// Stub the Vuetify <v-col> wrapper with a plain element that still renders its
// default slot, so we can assert on the real template logic without booting
// a full Vuetify instance.
const stubs = { 'v-col': { template: '<div class="v-col"><slot /></div>' } }

describe('PlayerText', () => {
  it('renders the player text as HTML when present', () => {
    const wrapper = mount(PlayerText, {
      propsData: { player: { text: '<strong>Hello</strong>' } },
      stubs,
    })
    expect(wrapper.find('.v-col').exists()).toBe(true)
    expect(wrapper.html()).toContain('<strong>Hello</strong>')
  })

  it('renders nothing when the player has no text', () => {
    const wrapper = mount(PlayerText, {
      propsData: { player: { text: '' } },
      stubs,
    })
    expect(wrapper.find('.v-col').exists()).toBe(false)
  })
})
