// Design exploration (pass 4): two timer identities. Not used by production screens.
// State and lock appearance are separate inputs: the shackle closes only when
// blocking is verified (`blocked`), never just because a focus round is running.
import { countdown, spokenRemaining } from '../ui/format';

export type TimerState = 'ready' | 'focus' | 'break' | 'done';

export interface TimerProps {
  state: TimerState;
  /** Verified restriction on this device. Web is always false. */
  blocked: boolean;
  remainingMs: number;
  /** 0..1 through the current round or break. */
  progress: number;
  /** Short line under the numerals ("left in round 1"). */
  caption: string;
}

const label = (p: TimerProps) => `${p.caption}: ${p.state === 'done' ? 'session complete' : spokenRemaining(p.remainingMs)}`;

/* ------------------------------------------------------------------ */
/* Direction A: a refined padlock with the countdown set into its body. */
/* ------------------------------------------------------------------ */

// 200 × 224 box. Body 168 × 124, radius 30. Shackle centerline 88 wide (52% of the body),
// stroke 13. Closed: both legs seated. Open: left leg seated, right leg lifted clear (a
// released shackle), so "open" reads at a glance without floating parts.
const SHACKLE_CLOSED = 'M60 108 V74 A40 40 0 0 1 140 74 V108';
const SHACKLE_OPEN = 'M60 108 V62 A40 40 0 0 1 140 62 V80';

export function PadlockTimer(p: TimerProps & { size?: number }) {
  const closed = p.blocked && p.state === 'focus';
  const pct = Math.max(0, Math.min(1, p.progress));
  return (
    <figure className={`padlock padlock--${p.state} ${closed ? 'padlock--closed' : ''}`} style={{ width: p.size ?? 280 }} role="img" aria-label={label(p)}>
      <svg viewBox="0 0 200 224" aria-hidden="true">
        <path className="padlock__shackle" d={closed ? SHACKLE_CLOSED : SHACKLE_OPEN} />
        <rect className="padlock__body" x="16" y="96" width="168" height="124" rx="30" />
        {p.state === 'done' ? (
          <path className="padlock__check" d="M78 154 l16 16 l30 -32" />
        ) : (
          <text className="padlock__time" x="100" y="166" textAnchor="middle">{countdown(p.remainingMs)}</text>
        )}
        <text className="padlock__caption" x="100" y={p.state === 'done' ? 196 : 188} textAnchor="middle">{p.state === 'done' ? 'Session complete' : p.caption}</text>
        {(p.state === 'focus' || p.state === 'break') && (
          <g className="padlock__progress">
            <line x1="52" y1="204" x2="148" y2="204" className="padlock__track" />
            <line x1="52" y1="204" x2={52 + 96 * pct} y2="204" className="padlock__fill" />
          </g>
        )}
      </svg>
    </figure>
  );
}

/* ------------------------------------------------------------------ */
/* Direction B: display numerals with a small lock detail and a rail. */
/* ------------------------------------------------------------------ */

export function MiniLock({ closed, size = 18 }: { closed: boolean; size?: number }) {
  return (
    <svg className="minilock" width={size} height={size} viewBox="0 0 24 24" aria-hidden="true">
      <path d={closed ? 'M7.5 11V8a4.5 4.5 0 0 1 9 0v3' : 'M7.5 11V6.5a4.5 4.5 0 0 1 9 0'} fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
      <rect x="4.5" y="11" width="15" height="10" rx="3" fill="currentColor" />
    </svg>
  );
}

const STATUS: Record<TimerState, string> = { ready: 'Ready', focus: 'Focusing', break: 'On a break', done: 'Complete' };

export function NumeralTimer(p: TimerProps & { round?: string }) {
  const closed = p.blocked && p.state === 'focus';
  return (
    <div className={`numeral numeral--${p.state}`} role="img" aria-label={label(p)}>
      <p className="numeral__status" aria-hidden="true">
        <MiniLock closed={closed} />
        <span>{STATUS[p.state]}{p.round ? ` · ${p.round}` : ''}</span>
      </p>
      <p className="numeral__time" aria-hidden="true">{p.state === 'done' ? 'Done' : countdown(p.remainingMs)}</p>
    </div>
  );
}

export interface RailSegment { kind: 'focus' | 'break'; minutes: number; start: number; end: number }

/** The session as a rail: segment widths follow their minutes; the elapsed part is filled. */
export function SessionRail({ segments, now, fmt }: { segments: RailSegment[]; now: number; fmt: (t: number) => string }) {
  const start = segments[0].start, end = segments[segments.length - 1].end;
  return (
    <div className="rail" aria-hidden="true">
      <div className="rail__bars">
        {segments.map((s, i) => {
          const f = Math.max(0, Math.min(1, (now - s.start) / (s.end - s.start)));
          return (
            <span key={i} className={`rail__seg rail__seg--${s.kind} ${now >= s.start && now < s.end ? 'rail__seg--now' : ''}`} style={{ flexGrow: s.minutes }}>
              <span className="rail__fill" style={{ width: `${f * 100}%` }} />
            </span>
          );
        })}
      </div>
      <div className="rail__labels">
        <span style={{ left: 0 }}>{fmt(start)}</span>
        {segments.slice(0, -1).map((s, i) => segments[i + 1].kind === 'break' && (
          <span key={i} className="rail__tick" style={{ left: `${((s.end - start) / (end - start)) * 100}%` }}>Break {fmt(s.end)}</span>
        ))}
        <span className="rail__end" style={{ right: 0 }}>{fmt(end)}</span>
      </div>
    </div>
  );
}
