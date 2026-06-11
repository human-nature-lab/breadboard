import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    // isEqual.ts (and other modules) touch `window` at import time, so a DOM
    // environment is required even for "pure" logic tests.
    environment: 'jsdom',
    include: ['test/**/*.test.ts'],
  },
})
