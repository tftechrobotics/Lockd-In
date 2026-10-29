/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

const design = fileURLToPath(new URL('../../design', import.meta.url));

export default defineConfig({
  plugins: [react()],
  resolve: { alias: { '@design': design } },
  server: { fs: { allow: ['.', design] } },
  build: { sourcemap: true },
  test: { include: ['src/**/*.test.ts'] },
});
