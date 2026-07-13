import { defineConfig } from 'vitest/config'
import vue2 from '@vitejs/plugin-vue2'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  plugins: [vue2()],
  resolve: {
    // Mirror the '@/*' -> 'src/*' alias from tsconfig so source imports like
    // `@/lib/screen-tracker` resolve under vitest as well as webpack.
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'jsdom',
    include: ['test/**/*.test.ts'],
  },
})
