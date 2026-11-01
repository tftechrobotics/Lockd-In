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
import { lockSilhouette, circlePath } from '../src/ui/lockPath.ts';

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

// The lock replaces "o": the same one-piece silhouette as the timer (body ∪ shackle as a single
// filled path, src/ui/lockPath.ts), sized like the "o" (x-height plus overshoot) on the baseline,
// with the shackle rising to the ascender. Optical variant: no countdown or label; a thin
// knocked-out ring echoes the timer's progress track.
const lockGlyph = (ox) => {
  const o = font.charToGlyph('o');
  const over = -o.getBoundingBox().y1 * scale;  // the "o" overshoot below the baseline
  const D = xHeight + 2 * over;                 // body diameter, like the "o"
  const lsb = 2.5;
  const adv = D + 2 * lsb;
  const cx = ox + adv / 2, R = D / 2, cy = baseline + over - R;
  const stroke = D * 0.2;                       // near Outfit Bold's stem
  const ro = D * 0.35;                          // shackle ~0.7 of the body width
  const top = baseline - capHeight * 1.04;      // up to the ascender of "l"/"k"
  const silhouette = lockSilhouette({ cx, cy, r: R, ro, stroke, top });
  const ringW = D * 0.1, ringR = R * 0.68;
  const ring = circlePath(cx, cy, ringR + ringW / 2) + circlePath(cx, cy, ringR - ringW / 2);
  return { d: silhouette + ring, adv };
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
const wordBody = `<g fill="currentColor">${parts.map((d) => `<path d="${d}"/>`).join('')}<path fill-rule="evenodd" d="${lock.d}"/></g>`;
writeFileSync(join(out, 'wordmark.svg'), svg('lockd in', wordBody, width, height));

// Standalone lock mark (app icon source), re-drawn at the origin.
x = 0;
const mark = lockGlyph(0);
const top = Math.floor(baseline - capHeight * 1.04) - 2;
const mh = Math.ceil(baseline + 2 - top) + 2;
const markBody = `<g fill="currentColor" transform="translate(0 ${-top})"><path fill-rule="evenodd" d="${mark.d}"/></g>`;
writeFileSync(join(out, 'lock-mark.svg'), svg('Lockd In', markBody, Math.ceil(mark.adv), mh));
console.log(`wordmark ${width}x${height}, mark ${Math.ceil(mark.adv)}x${mh}`);
