// Design preview "studio": one live copy of the app inside a Windows window, an Android phone or
// the plain web page, with sample moments and labelled demo states. Everything runs in this one
// page (no nested frames), so every control in the app works wherever the preview is hosted.
// Preview build only; never part of the product.
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { App } from '../App';
import { DEMO, applyScene, setDemo, setPreviewText, setPreviewTheme, useDemoVersion, type Scene } from '../demo';
import { layout, setPreviewLayout, type Layout } from '../platform';
import mark from '@design/assets/lock-mark.svg?raw';
import { Sheet } from './Sheet';
import './studio.css';

type View = Layout | 'sheet';
interface Opts { view: View; scene: Scene; theme: 'light' | 'dark'; large: boolean }

const SAVED = 'lockdin.studio.opts';
const load = (): Opts => {
  const base: Opts = { view: 'android', scene: 'setup', theme: 'light', large: false };
  try { return { ...base, ...JSON.parse(localStorage.getItem(SAVED) ?? '{}') }; } catch { return base; }
};

function Seg<T extends string>({ label, value, items, onChange }: { label: string; value: T; items: [T, string][]; onChange: (v: T) => void }) {
  return (
    <div className="st-group">
      <span className="st-label">{label}</span>
      <div className="st-seg" role="radiogroup" aria-label={label}>
        {items.map(([v, t]) => (
          <button key={v} type="button" role="radio" aria-checked={value === v} onClick={() => onChange(v)}>{t}</button>
        ))}
      </div>
    </div>
  );
}

/** Scales a fixed-size device down (never up) to fit a narrow screen; layout and input stay exact. */
function useFit(width: number) {
  const ref = useRef<HTMLDivElement>(null);
  const [zoom, setZoom] = useState(1);
  useLayoutEffect(() => {
    const el = ref.current?.parentElement;
    if (!el) return;
    const fit = () => setZoom(Math.min(1, (el.clientWidth - 8) / width));
    fit();
    const ro = new ResizeObserver(fit);
    ro.observe(el);
    return () => ro.disconnect();
  }, [width]);
  return { ref, zoom };
}

export function Studio() {
  const [opts, setOpts] = useState<Opts>(() => {
    const o = load();
    if (o.view !== 'sheet') setPreviewLayout(o.view);
    return o;
  });
  const [mount, setMount] = useState(0);
  const demoVersion = useDemoVersion();

  // First load: apply the saved look and sample.
  useEffect(() => {
    document.documentElement.dataset.studio = '';
    setPreviewTheme(opts.theme);
    setPreviewText(opts.large);
    applyScene(opts.scene);
    setMount((m) => m + 1);
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { try { localStorage.setItem(SAVED, JSON.stringify(opts)); } catch { /* per-viewer convenience only */ } }, [opts]);

  const change = (next: Partial<Opts>) => {
    const o = { ...opts, ...next };
    if (next.view && next.view !== 'sheet') setPreviewLayout(next.view);
    if (next.theme) setPreviewTheme(next.theme);
    if (next.large !== undefined) setPreviewText(next.large);
    if (next.scene) applyScene(next.scene);
    if (next.view || next.scene) location.hash = '#/focus';
    setOpts(o);
    setMount((m) => m + 1);
  };

  const key = `${mount}-${demoVersion}-${layout}`;
  const desk = useFit(642), phone = useFit(412);

  return (
    <div className="studio">
      <header className="st-head">
        <div className="st-title">
          <span className="st-mark" dangerouslySetInnerHTML={{ __html: mark }} />
          <div>
            <h1>Lockd In <span>design preview</span></h1>
            <p>The Windows and Android views are browser approximations, not native builds. Anything marked <span className="demo-tag">Demo</span> is sample data, not a working feature.</p>
          </div>
        </div>
        <div className="st-controls">
          <Seg label="Device" value={opts.view} onChange={(view) => change({ view })}
            items={[['android', 'Android'], ['desktop', 'Windows'], ['web', 'Web'], ['sheet', 'Timer & wordmark']]} />
          <Seg label="Sample" value={opts.scene} onChange={(scene) => change({ scene })}
            items={[['setup', 'Idle'], ['focus', 'Focusing'], ['break', 'Break'], ['pending', 'Unlock pending'], ['done', 'Done']]} />
          <Seg label="Theme" value={opts.theme} onChange={(theme) => change({ theme })} items={[['light', 'Light'], ['dark', 'Dark']]} />
          <Seg label="Text" value={opts.large ? '200' : '100'} onChange={(v) => change({ large: v === '200' })} items={[['100', '100%'], ['200', '200%']]} />
          <div className="st-group">
            <span className="st-label">Demo states</span>
            <label className="st-check">
              <input type="checkbox" checked={DEMO?.protect ?? false} disabled={opts.view === 'web'} onChange={(e) => setDemo({ protect: e.target.checked })} />
              Blocking verified <span className="demo-tag">Demo</span>
            </label>
            <label className="st-check">
              <input type="checkbox" checked={DEMO?.paired ?? false} onChange={(e) => setDemo({ paired: e.target.checked })} />
              Devices paired <span className="demo-tag">Demo</span>
            </label>
          </div>
        </div>
      </header>

      <main className="st-stage">
        {opts.view === 'sheet' ? <Sheet /> : opts.view === 'web' ? (
          <div className="st-web"><App key={key} /></div>
        ) : opts.view === 'desktop' ? (
          <div className="st-fit"><div ref={desk.ref} style={desk.zoom < 1 ? { zoom: desk.zoom } : undefined}>
            <div className="pv-window">
              <div className="pv-window__title"><span className="pv-window__icon" dangerouslySetInnerHTML={{ __html: mark }} />lockd in<span className="pv-window__btns"><i>&#x2013;</i><i>&#x25A1;</i><i>&#x2715;</i></span></div>
              <div className="st-device st-device--desktop"><App key={key} /></div>
            </div>
          </div></div>
        ) : (
          <div className="st-fit"><div ref={phone.ref} style={phone.zoom < 1 ? { zoom: phone.zoom } : undefined}>
            <div className="pv-phone">
              <div className="pv-phone__status"><span>7:12</span><span className="pv-phone__icons" /></div>
              <div className="st-device st-device--android"><App key={key} /></div>
              <div className="pv-phone__home" />
            </div>
          </div></div>
        )}
      </main>
    </div>
  );
}
