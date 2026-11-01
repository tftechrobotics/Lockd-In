// Packs dist-phone/ into one HTML file you can open on a phone: CSS, JS and fonts inlined.
// Opens in the Android layout with sample data (preview build: labelled demo states only).
// Usage: npx vite build --config vite.phone.config.ts --mode preview && node scripts/build-phone-demo.mjs <out.html>
import { readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const dist = new URL('../dist-phone/', import.meta.url).pathname;
let html = readFileSync(join(dist, 'index.html'), 'utf8');
const assets = readdirSync(join(dist, 'assets'));
const css = assets.filter((f) => f.endsWith('.css')).map((f) => readFileSync(join(dist, 'assets', f), 'utf8')).join('\n');
const js = assets.filter((f) => f.endsWith('.js')).map((f) => readFileSync(join(dist, 'assets', f), 'utf8')).join('\n');
html = html.replace(/<script type="module"[^>]*><\/script>/, '').replace(/<link rel="stylesheet"[^>]*>/g, '').replace(/<link rel="modulepreload"[^>]*>/g, '');
// Default to the phone layout and the idle sample unless the address already says otherwise.
const boot = `<script>(function(){var q=new URLSearchParams(location.search);if(!q.has('shell')){q.set('shell','android');}var seen=false;try{seen=!!localStorage.getItem('lockdin.v1.preview.phone');}catch(e){}if(!q.has('scene')&&!seen){q.set('scene','setup');}if(seen){q.delete('scene');}q.set('frame','phone');try{history.replaceState(null,'',location.pathname+'?'+q.toString()+(location.hash||'#/focus'));}catch(e){}})();</script>`;
// Function replacers: the bundles contain `$` sequences that string replacement would expand.
html = html.replace('<head>', () => `<head>${boot}`).replace('</head>', () => `<style>${css}</style></head>`).replace('</body>', () => `<script type="module">${js}</script></body>`);
html = html.replace(/<link rel="icon"[^>]*>/, '');
writeFileSync(process.argv[2] ?? 'lockd-in-phone-demo.html', html);
console.log(`wrote ${process.argv[2]} (${Math.round(html.length / 1024)} KB)`);
