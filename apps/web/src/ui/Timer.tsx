// The running-session timer identity (design pass 4, direction B): display numerals with a
// small lock detail, and a session rail. The lock closes only when restriction is verified
// on this device (`closed`), never just because a focus round is running.
import type { Phase } from '../engine/session';
import { clockTime, countdown } from './format';

export type TimerState = 'ready' | 'focus' | 'break' | 'done';

export function MiniLock({ closed, size = 18 }: { closed: boolean; size?: number }) {
  return (
    <svg className="minilock" width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <path d={closed ? 'M7.5 11V8a4.5 4.5 0 0 1 9 0v3' : 'M7.5 11V6.5a4.5 4.5 0 0 1 9 0'} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      <rect x="4.5" y="11" width="15" height="10" rx="3" fill="currentColor" />
    </svg>
  );
}

/** Status line with the lock detail: "Focusing · round 1 of 2". */
export function TimerStatus({ state, closed, text, live = false }: { state: TimerState; closed: boolean; text: string; live?: boolean }) {
  return (
    <p className={`timer-status timer-status--${state}`} aria-live={live ? 'polite' : undefined}>
      <MiniLock closed={closed} />
      <span>{text}</span>
    </p>
  );
}

/** The countdown. Screen readers get `label` (minute-level), not the ticking digits. */
export function Numerals({ state, ms, label, size = 'large' }: { state: TimerState; ms: number; label: string; size?: 'large' | 'medium' }) {
  return (
    <p className={`numerals numerals--${state} numerals--${size}`} role="timer" aria-label={label}>
      <span aria-hidden="true">{state === 'done' ? 'Done' : countdown(ms)}</span>
    </p>
  );
}

/** The session as a rail: widths follow minutes; the elapsed part is filled; breaks are dashed. */
export function SessionRail({ phases, now, labels = true }: { phases: Pick<Phase, 'kind' | 'start' | 'end'>[]; now: number; labels?: boolean }) {
  const start = phases[0].start, end = phases[phases.length - 1].end;
  const breaks = phases.filter((p) => p.kind === 'break');
  return (
    <div className="rail" aria-hidden="true">
      <div className="rail__bars">
        {phases.map((p, i) => {
          const f = Math.max(0, Math.min(1, (now - p.start) / (p.end - p.start)));
          return (
            <span key={i} className={`rail__seg rail__seg--${p.kind}`} style={{ flexGrow: p.end - p.start }}>
              <span className="rail__fill" style={{ width: `${f * 100}%` }} />
            </span>
          );
        })}
      </div>
      {labels && (
        <div className="rail__labels">
          <span className="rail__start">{clockTime(start)}</span>
          {breaks.length <= 2 && breaks.map((b) => (
            <span key={b.start} className="rail__tick" style={{ left: `${((b.start - start) / (end - start)) * 100}%` }}>Break {clockTime(b.start)}</span>
          ))}
          <span className="rail__end">{clockTime(end)}</span>
        </div>
      )}
    </div>
  );
}
