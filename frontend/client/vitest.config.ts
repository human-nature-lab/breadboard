import { defineConfig } from 'vitest/config'
import vue2 from '@vitejs/plugin-vue2'

export default defineConfig({
  plugins: [vue2()],
  test: {
    environment: 'jsdom',
    include: ['test/**/*.test.ts'],
  },
})
