import { describe, it, expect } from 'vitest'
import { createLocalVue, mount } from '@vue/test-utils'
import { loadAllVueComponents } from '../src/util'

// `vue-components.ts` feeds `loadAllVueComponents` a webpack `require.context`,
// which doesn't exist under Vite/vitest. We test the registration logic two ways:
//  1. with a hand-rolled fake context (the mechanism)
//  2. with the real components loaded via `import.meta.glob` (the real wiring)
// Both register onto an isolated `createLocalVue()` so global state never leaks.

/** Adapt a plain `{ key: module }` map into webpack's RequireContext shape. */
function fakeContext (modules: Record<string, any>) {
  const ctx = ((key: string) => modules[key]) as any
  ctx.keys = () => Object.keys(modules)
  return ctx as __WebpackModuleApi.RequireContext
}

describe('loadAllVueComponents (mechanism)', () => {
  it('registers each .vue file globally, keyed by basename only', () => {
    const localVue = createLocalVue()
    const ctx = fakeContext({
      './Choice.vue': { default: { name: 'Choice', render: (h: any) => h('div') } },
      './form/Form.vue': { default: { name: 'Form', render: (h: any) => h('div') } },
    })

    loadAllVueComponents(localVue, ctx)

    // Globally registered components land in Vue.options.components.
    expect(localVue.options.components['Choice']).toBeTruthy()
    // Nested path -> basename only ('./form/Form.vue' -> 'Form').
    expect(localVue.options.components['Form']).toBeTruthy()
    expect(localVue.options.components['form/Form']).toBeUndefined()
  })

  it('makes a registered component resolvable by name from a parent', () => {
    const localVue = createLocalVue()
    loadAllVueComponents(localVue, fakeContext({
      './Choice.vue': {
        default: { name: 'Choice', render: (h: any) => h('p', { class: 'choice-ok' }, 'hi') },
      },
    }))

    // Render functions, not string templates: vitest runs Vue's runtime-only
    // build (no template compiler). If global registration worked, the parent
    // resolves 'Choice' by name without any local registration.
    const wrapper = mount({ render: (h: any) => h('Choice') }, { localVue })
    expect(wrapper.find('.choice-ok').exists()).toBe(true)
  })
})

describe('vue-components (real components)', () => {
  it('loads every real .vue file and registers it globally', () => {
    // import.meta.glob is Vite's equivalent of require.context. `eager` executes
    // each module now, so a broken import in any component fails this test.
    const modules = import.meta.glob('../src/components/**/*.vue', { eager: true })
    const ctx = fakeContext(modules as Record<string, any>)

    const localVue = createLocalVue()
    loadAllVueComponents(localVue, ctx)

    const registered = Object.keys(localVue.options.components)
    expect(registered.length).toBeGreaterThan(0)
    // Spot-check a couple of known components are present by their basename.
    expect(localVue.options.components['Choice']).toBeTruthy()
    expect(localVue.options.components['Form']).toBeTruthy()
  })
})
