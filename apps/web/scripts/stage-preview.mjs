// Stages dist-preview/ for publishing as the "Lockd In Preview" artifact: the page opens the
// studio (device frames + sample moments) by default. Usage: node scripts/stage-preview.mjs <outdir>
import { cpSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const out = process.argv[2];
if (!out) throw new Error('usage: stage-preview.mjs <outdir>');
rmSync(out, { recursive: true, force: true });
cpSync(new URL('../dist-preview/', import.meta.url).pathname, out, { recursive: true });
const page = join(out, 'index.html');
const html = readFileSync(page, 'utf8').replace('<head>', () => `<head><script>window.__LOCKDIN_DEFAULTS = 'studio=1';</script>`);
writeFileSync(page, html);
console.log(`staged ${out}`);
