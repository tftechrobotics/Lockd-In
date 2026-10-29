// Packs dist/ into one self-contained HTML body (CSS, JS and fonts inlined) for
// a shareable preview page. Same production build; nothing is rewritten except
// asset URLs. Usage: npm run build && node scripts/build-preview.mjs <out.html>
import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

const dist = new URL('../dist/', import.meta.url).pathname;
const html = readFileSync(join(dist, 'index.html'), 'utf8');
const assets = readdirSync(join(dist, 'assets'));
const read = (f) => readFileSync(join(dist, 'assets', f));

let css = assets.filter((f) => f.endsWith('.css')).map((f) => read(f).toString()).join('\n');
css = css.replace(/url\((?:\.\/|\/assets\/)?([^)"']+\.woff2)\)/g, (_, f) => {
  const name = f.split('/').pop();
  return `url(data:font/woff2;base64,${read(name).toString('base64')})`;
});
const js = assets.filter((f) => f.endsWith('.js')).map((f) => read(f).toString().replace(/\/\/# sourceMappingURL=.*$/m, '')).join('\n');
const themeScript = html.match(/<script>\s*\/\/ Apply the saved theme[\s\S]*?<\/script>/)[0];
const favicon = readFileSync(join(dist, 'favicon.svg')).toString('base64');

const out = `<title>Lockd In web preview</title>
<meta name="color-scheme" content="light dark">
<link rel="icon" href="data:image/svg+xml;base64,${favicon}">
${themeScript}
<style>${css}</style>
<div id="root"></div>
<script type="module">${js.replace(/<\/script/gi, '<\\/script')}</script>
`;
writeFileSync(process.argv[2], out);
console.log(`preview ${(out.length / 1024).toFixed(0)} KB`);
