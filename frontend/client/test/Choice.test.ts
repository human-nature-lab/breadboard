import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import Choice from '../src/components/Choice.vue'

// Stub <v-btn> with a plain <button> that inherits attrs, so anything bound via
// v-bind="choiceAttrs" lands as real DOM attributes/classes we can assert on.
const stubs = { 'v-btn': { template: '<button><slot /></button>' } }

function mountChoice (choice: any, disabled = false) {
  return mount(Choice, { propsData: { choice, disabled }, stubs })
}

describe('Choice button prop binding', () => {
  it('binds choice.props directly onto the button', () => {
    const w = mountChoice({ name: 'Go', uid: '1', props: { color: 'primary', outlined: true } })
    const btn = w.find('button')
    expect(btn.attributes('color')).toBe('primary')
    expect('outlined' in btn.attributes()).toBe(true)
    expect(btn.text()).toContain('Go')
  })

  it('keeps the static default-choice class and merges choice.class', () => {
    const w = mountChoice({ name: 'Go', uid: '1', class: 'extra', props: { color: 'red' } })
    const btn = w.find('button')
    expect(btn.classes()).toContain('default-choice')
    expect(btn.classes()).toContain('extra')
    expect(btn.attributes('color')).toBe('red')
  })

  it('renders fine with no props (backward compatible)', () => {
    const w = mountChoice({ name: 'Plain', uid: '2' })
    const btn = w.find('button')
    expect(btn.classes()).toContain('default-choice')
    expect(btn.text()).toContain('Plain')
    expect(btn.attributes('color')).toBeUndefined()
  })
})
