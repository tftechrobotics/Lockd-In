// Fails (exit 1) if any declared token pair is below its WCAG 2.2 contrast minimum.
import { readFileSync } from 'node:fs';
const t = JSON.parse(readFileSync(new URL('./tokens.json', import.meta.url)));
const lum = (hex) => {
  const c = hex.replace('#', '').match(/../g).map((x) => parseInt(x, 16) / 255)
    .map((v) => (v <= 0.04045 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4));
  return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2];
};
const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return (x + 0.05) / (y + 0.05); };
let bad = 0;
for (const theme of ['light', 'dark']) {
  for (const [fg, bg, min] of t.pairs) {
    const r = ratio(t.color[theme][fg], t.color[theme][bg]);
    const ok = r >= min; if (!ok) bad++;
    console.log(`${ok ? 'pass' : 'FAIL'} ${theme.padEnd(5)} ${fg} on ${bg}: ${r.toFixed(2)} (min ${min})`);
  }
}
process.exit(bad ? 1 : 0);
