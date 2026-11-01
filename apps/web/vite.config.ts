/// <reference types="vitest/config" />
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

const design = fileURLToPath(new URL('../../design', import.meta.url));

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  resolve: { alias: { '@design': design } },
  server: { fs: { allow: ['.', design] } },
  build: mode === 'preview'
    // Design preview: the shell page plus the app it frames, with relative paths so it can be
    // published as one static bundle. Never the production build.
    ? { outDir: 'dist-preview', sourcemap: false, rollupOptions: { input: { app: 'index.html', preview: 'preview.html' } } }
    : { sourcemap: true },
  base: mode === 'preview' ? './' : '/',
  test: { include: ['src/**/*.test.ts'], environment: 'node' },
}));
