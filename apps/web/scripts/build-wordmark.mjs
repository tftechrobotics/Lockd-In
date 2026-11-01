// Builds design/assets/wordmark.svg and lock-mark.svg from Outfit (OFL) glyph
// outlines, so the wordmark is a scalable vector that needs no font at runtime.
// Requires: python3 with fontTools + brotli (pip install fonttools brotli).
// Usage: npm run assets
import { execFileSync } from 'node:child_process';
import { mkdtempSync, writeFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import opentype from 'opentype.js';

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, '../../../design/assets');
const woff2 = join(here, '../node_modules/@fontsource-variable/outfit/files/outfit-latin-wght-normal.woff2');
const ttf = join(mkdtempSync(join(tmpdir(), 'wm-')), 'outfit-700.ttf');
execFileSync('python3', ['-I', '-c', `
import sys
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont
f = TTFont(sys.argv[1]); f.flavor = None
instantiateVariableFont(f, {"wght": 700}, inplace=True)
f.save(sys.argv[2])`, woff2, ttf]);

const buf = readFileSync(ttf);
const font = opentype.parse(buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength));
const size = 100; // font units scaled to a 100 px em
const scale = size / font.unitsPerEm;
const asc = font.ascender * scale;
const xHeight = (font.tables.os2.sxHeight || 500) * scale;
const capHeight = (font.tables.os2.sCapHeight || 700) * scale;
const baseline = Math.ceil(asc) + 4;
const tracking = -1.5;

let x = 0;
const parts = [];
const glyphRun = (text) => {
  for (const ch of text) {
    const g = font.charToGlyph(ch);
    const p = g.getPath(x, baseline, size);
    parts.push(p.toPathData(2));
    x += g.advanceWidth * scale + tracking;
  }
};

// The lock replaces "o" (pass 4, circular lock): a round body sized like the "o" (x-height plus
// overshoot) sitting on the baseline, and a substantial shackle rising to the ascender. Optical
// variant of the timer component: no countdown, no label; a thin knocked-out ring echoes the
// progress track so the body still reads as the clock face.
const VARIANT = process.env.WM_VARIANT ?? 'ring';
const lockGlyph = (ox) => {
  const o = font.charToGlyph('o');
  const ob = o.getBoundingBox();
  const over = (-ob.y1 * scale);                // the "o" overshoot below the baseline
  const D = xHeight + 2 * over;                 // body diameter, like the "o"
  const lsb = 2.5;
  const adv = D + 2 * lsb;
  const cx = ox + adv / 2, cy = baseline + over - D / 2, R = D / 2;
  const stroke = D * 0.2;                       // shackle weight: near Outfit Bold's stem
  const outerW = D * 0.7;                       // reference: shackle is ~0.69 of the body width
  const ro = outerW / 2, ri = ro - stroke;
  const sTop = baseline - capHeight * 1.04;     // up to the ascender of "l"/"k"
  const scy = sTop + ro;                        // shackle arc centre
  // Legs stop just inside the body's edge, so nothing shows through the knocked-out ring.
  const legEnd = cy - Math.sqrt(R * R - ro * ro) + D * 0.06;
  const shackle = [
    `M${(cx - ro).toFixed(2)},${legEnd.toFixed(2)}`,
    `V${scy.toFixed(2)}`,
    `A${ro.toFixed(2)},${ro.toFixed(2)} 0 0 1 ${(cx + ro).toFixed(2)},${scy.toFixed(2)}`,
    `V${legEnd.toFixed(2)}`,
    `H${(cx + ri).toFixed(2)}`,
    `V${scy.toFixed(2)}`,
    `A${ri.toFixed(2)},${ri.toFixed(2)} 0 0 0 ${(cx - ri).toFixed(2)},${scy.toFixed(2)}`,
    `V${legEnd.toFixed(2)}Z`,
  ].join('');
  const circle = (r) => `M${(cx + r).toFixed(2)},${cy.toFixed(2)}A${r.toFixed(2)},${r.toFixed(2)} 0 1 0 ${(cx - r).toFixed(2)},${cy.toFixed(2)}A${r.toFixed(2)},${r.toFixed(2)} 0 1 0 ${(cx + r).toFixed(2)},${cy.toFixed(2)}Z`;
  // Body: solid disc; "ring" knocks out a thin track inside it (even-odd: disc, outer, inner).
  const ringW = D * 0.1, ringR = R * 0.72;
  const body = VARIANT === 'ring'
    ? circle(R) + circle(ringR + ringW / 2) + circle(ringR - ringW / 2)
    : circle(R);
  // The shackle is a separate path so its legs never cut into the body.
  return { shackle, body, adv };
};

glyphRun('l');
const lock = lockGlyph(x);
x += lock.adv + tracking;
glyphRun('ckd');
x += font.charToGlyph(' ').advanceWidth * scale * 0.9;
glyphRun('in');

const width = Math.ceil(x - tracking) + 2;
const height = Math.ceil(baseline + (-font.descender * scale) * 0.35);
const svg = (title, body, w, h) => `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${w} ${h}" role="img" aria-label="${title}"><title>${title}</title>${body}</svg>\n`;
const wordBody = `<g fill="currentColor">${parts.map((d) => `<path d="${d}"/>`).join('')}<path d="${lock.shackle}"/><path fill-rule="evenodd" d="${lock.body}"/></g>`;
writeFileSync(join(out, 'wordmark.svg'), svg('lockd in', wordBody, width, height));

// Standalone lock mark (app icon source), re-drawn at the origin.
x = 0;
const mark = lockGlyph(0);
const top = Math.floor(baseline - capHeight * 1.04) - 2;
const mh = Math.ceil(baseline + 2 - top) + 2;
const markBody = `<g fill="currentColor" transform="translate(0 ${-top})"><path d="${mark.shackle}"/><path fill-rule="evenodd" d="${mark.body}"/></g>`;
writeFileSync(join(out, 'lock-mark.svg'), svg('Lockd In', markBody, Math.ceil(mark.adv), mh));
console.log(`wordmark ${width}x${height}, mark ${Math.ceil(mark.adv)}x${mh}`);
