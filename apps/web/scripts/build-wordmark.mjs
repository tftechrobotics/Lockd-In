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

// The lock replaces "o": same advance as "o"; body = x-height; shackle to cap height.
const lockGlyph = (ox) => {
  const o = font.charToGlyph('o');
  const adv = o.advanceWidth * scale;
  const lsb = 3, w = adv - 2 * lsb;            // body width
  const bodyTop = baseline - xHeight * 0.92;
  const bodyH = baseline - bodyTop;
  const r = w * 0.22;                           // body corner radius
  const stroke = w * 0.17;                      // shackle weight, close to Outfit's stem
  const sx1 = ox + lsb + w * 0.24, sx2 = ox + lsb + w * 0.76;
  const sTop = baseline - capHeight * 1.0;
  const sr = (sx2 - sx1) / 2;
  // Shackle: an open U drawn as a stroked arc, converted to an outline path.
  const ri = sr - stroke / 2, ro = sr + stroke / 2, cx = (sx1 + sx2) / 2, cy = sTop + ro;
  const shackle = [
    `M${(cx - ro).toFixed(2)},${(bodyTop + 1).toFixed(2)}`,
    `V${cy.toFixed(2)}`,
    `A${ro.toFixed(2)},${ro.toFixed(2)} 0 0 1 ${(cx + ro).toFixed(2)},${cy.toFixed(2)}`,
    `V${(bodyTop + 1).toFixed(2)}`,
    `H${(cx + ri).toFixed(2)}`,
    `V${cy.toFixed(2)}`,
    `A${ri.toFixed(2)},${ri.toFixed(2)} 0 0 0 ${(cx - ri).toFixed(2)},${cy.toFixed(2)}`,
    `V${(bodyTop + 1).toFixed(2)}Z`,
  ].join('');
  const bx = ox + lsb, by = bodyTop;
  const body = `M${(bx + r).toFixed(2)},${by.toFixed(2)}H${(bx + w - r).toFixed(2)}A${r},${r} 0 0 1 ${(bx + w).toFixed(2)},${(by + r).toFixed(2)}V${(by + bodyH - r).toFixed(2)}A${r},${r} 0 0 1 ${(bx + w - r).toFixed(2)},${(by + bodyH).toFixed(2)}H${(bx + r).toFixed(2)}A${r},${r} 0 0 1 ${bx.toFixed(2)},${(by + bodyH - r).toFixed(2)}V${(by + r).toFixed(2)}A${r},${r} 0 0 1 ${(bx + r).toFixed(2)},${by.toFixed(2)}Z`;
  // Clock face knocked out of the body (evenodd), with hands left solid.
  const ccx = bx + w / 2, ccy = by + bodyH * 0.55, cr = Math.min(w, bodyH) * 0.29;
  const face = `M${(ccx + cr).toFixed(2)},${ccy.toFixed(2)}A${cr},${cr} 0 1 0 ${(ccx - cr).toFixed(2)},${ccy.toFixed(2)}A${cr},${cr} 0 1 0 ${(ccx + cr).toFixed(2)},${ccy.toFixed(2)}Z`;
  const hw = stroke * 0.42;
  const hands = `M${(ccx - hw / 2).toFixed(2)},${(ccy - cr * 0.62).toFixed(2)}H${(ccx + hw / 2).toFixed(2)}V${(ccy - hw / 2).toFixed(2)}H${(ccx + cr * 0.5).toFixed(2)}V${(ccy + hw / 2).toFixed(2)}H${(ccx - hw / 2).toFixed(2)}Z`;
  return { d: `${shackle}${body}${face}`, hands, adv };
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
const wordBody = `<g fill="currentColor">${parts.map((d) => `<path d="${d}"/>`).join('')}<path fill-rule="evenodd" d="${lock.d}"/><path d="${lock.hands}"/></g>`;
writeFileSync(join(out, 'wordmark.svg'), svg('lockd in', wordBody, width, height));

// Standalone lock mark (app icon source), re-drawn at the origin.
x = 0;
const mark = lockGlyph(0);
const top = Math.floor(baseline - capHeight) - 2;
const mh = Math.ceil(baseline - top) + 2;
const markBody = `<g fill="currentColor" transform="translate(0 ${-top})"><path fill-rule="evenodd" d="${mark.d}"/><path d="${mark.hands}"/></g>`;
writeFileSync(join(out, 'lock-mark.svg'), svg('Lockd In', markBody, Math.ceil(mark.adv), mh));
console.log(`wordmark ${width}x${height}, mark ${Math.ceil(mark.adv)}x${mh}`);
