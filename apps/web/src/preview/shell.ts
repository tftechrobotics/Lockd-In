// Design preview shell: frames the real web UI as it appears in the Windows app (desktop layout),
// an approximation of the Android app, and the web app, with sample scenes and labeled demo
// states. A review tool only — not a native build and not part of the product.
import '@fontsource-variable/outfit/wght.css';
import '@fontsource-variable/figtree/wght.css';
import '../styles/tokens.css';
import './preview.css';
import mark from '@design/assets/lock-mark.svg?raw';

type Scene = 'setup' | 'focus' | 'break' | 'pending' | 'done';
type View = 'devices' | 'web' | 'sheet';
interface Opts { scene: Scene; protect: boolean; paired: boolean; theme: 'light' | 'dark'; text: '100' | '200'; view: View }

const APP = location.pathname.endsWith('preview.html') ? 'index.html' : 'app.html';
const saved = (() => { try { return JSON.parse(localStorage.getItem('lockdin.preview.opts') ?? '{}') as Partial<Opts>; } catch { return {}; } })();
const opts: Opts = { scene: 'setup', protect: false, paired: false, theme: 'light', text: '100', view: 'devices', ...saved };

const CHOICES: { key: keyof Opts; label: string; items: [string, string][] }[] = [
  { key: 'scene', label: 'Sample', items: [['setup', 'Setup'], ['focus', 'Focusing'], ['break', 'Break'], ['pending', 'Unlock pending'], ['done', 'Done']] },
  { key: 'theme', label: 'Theme', items: [['light', 'Light'], ['dark', 'Dark']] },
  { key: 'text', label: 'Text', items: [['100', '100%'], ['200', '200%']] },
  { key: 'view', label: 'Show', items: [['devices', 'Windows + Android'], ['web', 'Web'], ['sheet', 'Timer & wordmark']] },
];

const root = document.getElementById('preview')!;
root.innerHTML = `
  <header class="pv-head">
    <div>
      <h1>Lockd In <span>design preview</span></h1>
      <p>Browser preview of the shared interface. The Windows and Android frames approximate the native apps; they are not verified native builds. Anything marked <span class="pv-demo">Demo</span> is sample data, not a working feature.</p>
    </div>
  </header>
  <div class="pv-controls" role="group" aria-label="Preview options"></div>
  <div class="pv-stage"></div>`;

const controls = root.querySelector<HTMLDivElement>('.pv-controls')!;
const stage = root.querySelector<HTMLDivElement>('.pv-stage')!;

function renderControls() {
  controls.innerHTML = '';
  for (const c of CHOICES) {
    const g = document.createElement('div');
    g.className = 'pv-group';
    g.innerHTML = `<span class="pv-label" id="pv-${c.key}">${c.label}</span>`;
    const seg = document.createElement('div');
    seg.className = 'pv-seg';
    seg.setAttribute('role', 'radiogroup');
    seg.setAttribute('aria-labelledby', `pv-${c.key}`);
    for (const [value, text] of c.items) {
      const b = document.createElement('button');
      b.type = 'button';
      b.setAttribute('role', 'radio');
      b.setAttribute('aria-checked', String(opts[c.key] === value));
      b.textContent = text;
      b.onclick = () => { (opts as unknown as Record<string, string>)[c.key] = value; changed(); };
      seg.append(b);
    }
    g.append(seg);
    controls.append(g);
  }
  const demo = document.createElement('div');
  demo.className = 'pv-group';
  demo.innerHTML = `<span class="pv-label">Demo states</span>
    <label class="pv-check"><input type="checkbox" data-k="protect" ${opts.protect ? 'checked' : ''}/> Blocking verified <span class="pv-demo">Demo</span></label>
    <label class="pv-check"><input type="checkbox" data-k="paired" ${opts.paired ? 'checked' : ''}/> Devices paired <span class="pv-demo">Demo</span></label>`;
  demo.querySelectorAll<HTMLInputElement>('input').forEach((i) => {
    i.onchange = () => { opts[i.dataset.k as 'protect' | 'paired'] = i.checked; changed(); };
  });
  controls.append(demo);
}

const src = (shell: 'desktop' | 'android' | 'web', frame: string) => {
  const q = new URLSearchParams({ frame, scene: opts.scene, theme: opts.theme, text: opts.text });
  if (shell !== 'web') q.set('shell', shell);
  // A browser can never restrict apps, so the web frame never gets the blocking demo.
  if (opts.protect && shell !== 'web') q.set('protect', '1');
  if (opts.paired) q.set('paired', '1');
  return `${APP}?${q}#/focus`;
};

const reset = '<button type="button" class="pv-reset" title="Reload this frame with the selected sample">Reset</button>';

function renderStage() {
  if (opts.view === 'sheet') {
    const q = new URLSearchParams({ frame: 'sheet', sheet: '1', theme: opts.theme, text: opts.text });
    stage.innerHTML = `<figure class="pv-frame pv-frame--sheet"><figcaption><strong>Timer and wordmark</strong> · components at real size</figcaption>
      <iframe class="pv-sheet" title="Timer and wordmark sheet" src="${APP}?${q}"></iframe></figure>`;
    const f = stage.querySelector<HTMLIFrameElement>('iframe')!;
    const grow = () => { try { f.style.height = `${f.contentDocument!.documentElement.scrollHeight}px`; } catch { /* same origin only */ } };
    f.addEventListener('load', () => { grow(); setTimeout(grow, 400); setTimeout(grow, 1200); });
    return;
  }
  if (opts.view === 'web') {
    stage.innerHTML = `
      <figure class="pv-frame pv-frame--web">
        <figcaption><strong>Web</strong> · this build ${reset}</figcaption>
        <div class="pv-browser"><div class="pv-browser__bar"><span></span><span></span><span></span><div class="pv-url">Lockd In</div></div>
          <div class="pv-scale" data-w="1280" data-h="800"><iframe title="Web app" src="${src('web', 'web')}" width="1280" height="800"></iframe></div>
        </div>
      </figure>`;
  } else {
    stage.innerHTML = `
      <figure class="pv-frame pv-frame--win">
        <figcaption><strong>Windows</strong> · preview of the desktop layout ${reset}</figcaption>
        <div class="pv-window">
          <div class="pv-window__title"><span class="pv-window__icon">${mark}</span>lockd in<span class="pv-window__btns"><i>&#x2013;</i><i>&#x25A1;</i><i>&#x2715;</i></span></div>
          <div class="pv-scale" data-w="640" data-h="760"><iframe title="Windows layout preview" src="${src('desktop', 'win')}" width="640" height="760"></iframe></div>
        </div>
      </figure>
      <figure class="pv-frame pv-frame--droid">
        <figcaption><strong>Android</strong> · browser approximation ${reset}</figcaption>
        <div class="pv-phone">
          <div class="pv-phone__status"><span>7:12</span><span class="pv-phone__icons"></span></div>
          <div class="pv-scale" data-w="390" data-h="780"><iframe title="Android layout preview" src="${src('android', 'droid')}" width="390" height="780"></iframe></div>
          <div class="pv-phone__home"></div>
        </div>
      </figure>`;
  }
  stage.querySelectorAll<HTMLButtonElement>('.pv-reset').forEach((b) => {
    b.onclick = () => { const f = b.closest('figure')!.querySelector('iframe')!; f.src = f.src; };
  });
  fit();
}

/** Scale frames down (never up) to fit narrow screens. */
function fit() {
  stage.querySelectorAll<HTMLDivElement>('.pv-scale').forEach((box) => {
    const w = Number(box.dataset.w), h = Number(box.dataset.h);
    const avail = Math.min(w, stage.clientWidth - 24);
    const s = Math.min(1, avail / w);
    box.style.width = `${w * s}px`;
    box.style.height = `${h * s}px`;
    box.querySelector('iframe')!.style.transform = `scale(${s})`;
  });
}

function changed() {
  try { localStorage.setItem('lockdin.preview.opts', JSON.stringify(opts)); } catch { /* per-viewer convenience only */ }
  document.documentElement.dataset.theme = opts.theme;
  renderControls();
  renderStage();
}

window.addEventListener('resize', fit);
changed();
