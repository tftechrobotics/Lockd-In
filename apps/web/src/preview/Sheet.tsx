// Design preview only: the lock timer and wordmark on one sheet, at real sizes, for review.
import { useEffect, useState } from 'react';
import wordmark from '@design/assets/wordmark.svg?raw';
import mark from '@design/assets/lock-mark.svg?raw';
import { CircleLock, type TimerState } from '../ui/Timer';
import './sheet.css';

const MIN = 60_000;
const TOTAL = 40 * MIN;

function Live() {
  // A short live countdown so the ring can be seen moving in step with the digits.
  const [t0] = useState(() => Date.now());
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const id = setInterval(() => setNow(Date.now()), 250); return () => clearInterval(id); }, []);
  const total = 60_000, left = total - ((now - t0) % total);
  return <CircleLock state="focus" remainingMs={left} totalMs={total} locked={false} label="Live sample" caption="remaining" size="200px" />;
}

const cell = (title: string, note: string, el: React.ReactNode) => (
  <figure className="sheet-cell"><div className="sheet-cell__art">{el}</div><figcaption><strong>{title}</strong><span>{note}</span></figcaption></figure>
);

export function Sheet() {
  const lock = (state: TimerState, left: number, locked = false, caption = 'remaining', size = '200px', total = TOTAL) =>
    <CircleLock state={state} remainingMs={left} totalMs={total} locked={locked} label={caption} caption={caption} size={size} />;
  return (
    <main className="pv-sheetpage">
      <section>
        <h2>Wordmark at header sizes</h2>
        <div className="sheet-row sheet-row--marks">
          {cell('Web masthead', '128 px wide', <span className="sheet-wm" style={{ width: 128 }} dangerouslySetInnerHTML={{ __html: wordmark }} />)}
          {cell('Android top bar', '92 px wide', <span className="sheet-wm" style={{ width: 92 }} dangerouslySetInnerHTML={{ __html: wordmark }} />)}
          {cell('Large', '320 px wide', <span className="sheet-wm" style={{ width: 320 }} dangerouslySetInnerHTML={{ __html: wordmark }} />)}
        </div>
        <h3>Symbol (where an icon is needed)</h3>
        <div className="sheet-row sheet-row--marks">
          {[16, 20, 32, 48, 96].map((h) => cell(`${h} px`, h <= 20 ? 'title bar, favicon' : h <= 48 ? 'tabs, lists' : 'app icon art',
            <span className="sheet-mark" style={{ height: h }} dangerouslySetInnerHTML={{ __html: mark }} />))}
        </div>
      </section>
      <section>
        <h2>Timer: remaining time</h2>
        <div className="sheet-row">
          {cell('Full', '40:00 of 40:00', lock('focus', TOTAL))}
          {cell('Halfway', '20:00 of 40:00', lock('focus', TOTAL / 2))}
          {cell('Nearly finished', '0:45 of 40:00', lock('focus', 45_000))}
          {cell('Live', 'one-minute loop', <Live />)}
        </div>
      </section>
      <section>
        <h2>States</h2>
        <div className="sheet-row">
          {cell('Ready', 'before starting · shackle open', lock('ready', TOTAL, false, 'per round'))}
          {cell('Focus, timer only', 'nothing restricted · shackle open', lock('focus', 26 * MIN))}
          {cell('Focus, blocking verified', 'Demo state · shackle closed', lock('focus', 26 * MIN, true))}
          {cell('Break', 'apps available · open, lighter', lock('break', 6 * MIN, false, 'of break left', '200px', 10 * MIN))}
          {cell('Done', 'session complete', lock('done', 0, false, 'complete'))}
        </div>
        <p className="sheet-note">The shackle closes only while restriction is verified and applied on that device. Timer-only focus, breaks and unlocked rounds always show it open.</p>
      </section>
      <section>
        <h2>Small sizes</h2>
        <div className="sheet-row sheet-row--small">
          {[160, 120, 96].map((w) => cell(`${w} px`, w >= 120 ? 'readable' : 'minimum; digits only', lock('focus', 23 * MIN + 14_000, false, 'remaining', `${w}px`)))}
        </div>
      </section>
    </main>
  );
}
