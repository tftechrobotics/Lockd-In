/// <reference types="node" />
// Phone demo: the preview build of the app as one self-contained HTML file (no server needed),
// opening in the Android layout with sample data. Design review only; see scripts/build-phone-demo.mjs.
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath } from 'node:url';

export default defineConfig({
  plugins: [react()],
  base: './',
  resolve: { alias: { '@design': fileURLToPath(new URL('../../design', import.meta.url)) } },
  build: {
    outDir: 'dist-phone', emptyOutDir: true, sourcemap: false,
    assetsInlineLimit: 100_000_000, cssCodeSplit: false,
    rollupOptions: { input: 'index.html', output: { inlineDynamicImports: true } },
  },
});
