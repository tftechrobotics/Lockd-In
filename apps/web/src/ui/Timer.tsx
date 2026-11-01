// The running-session timer identity (design pass 4, direction B): display numerals with a
// small lock detail, and a session rail. The lock closes only when restriction is verified
// on this device (`closed`), never just because a focus round is running.
import { useEffect, useState } from 'react';
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
export function TimerStatus({ state, closed, text, live = false, icon = true }: { state: TimerState; closed: boolean; text: string; live?: boolean; icon?: boolean }) {
  return (
    <p className={`timer-status timer-status--${state}`} aria-live={live ? 'polite' : undefined}>
      {icon && <MiniLock closed={closed} />}
      <span>{text}</span>
    </p>
  );
}

/*
 * Circular lock timer (approved 2026-10-31): the round body is the clock face, a substantial
 * shackle above it, a centred MM:SS countdown and a progress ring that starts full at 12 o'clock
 * and shrinks with the time left. Geometry follows the owner's reference: shackle ~0.69 of the
 * body's width, rising ~0.4 of its diameter above it. Flat: no gloss, no hands.
 *
 * `locked` closes the shackle. Pass it only when restriction is verified and applied right now;
 * timer-only focus, breaks and unlocked rounds show it open.
 */
const LK = { w: 240, cx: 120, cy: 232, r: 108, rim: 7 };
const SHACKLE = { ro: 74.5, stroke: 21, top: 40, lift: 36 };
const TICKS = 60;

/**
 * The shackle as one filled shape in its closed position. The left leg runs deep into the body
 * so it stays seated when the shackle lifts; the right leg ends in a rounded tip just inside the
 * body. Body and shackle share one fill and no outline, so where they overlap there is no seam.
 */
const SHACKLE_PATH = (() => {
  const { cx, cy, r } = LK, { ro, stroke, top } = SHACKLE;
  const ri = ro - stroke, cap = stroke / 2, arcY = top + ro;
  const yo = cy - Math.sqrt(r * r - ro * ro);
  const tip = yo + cap;
  return `M${cx - ro} ${cy}V${arcY}A${ro} ${ro} 0 0 1 ${cx + ro} ${arcY}V${tip}A${cap} ${cap} 0 0 1 ${cx + ri} ${tip}`
    + `V${arcY}A${ri} ${ri} 0 0 0 ${cx - ri} ${arcY}V${cy}Z`;
})();

/** Clock ticks: 60 around the face, every fifth one longer (the hours). */
const TICK_PATHS = Array.from({ length: TICKS }, (_, i) => {
  const a = (i / TICKS) * Math.PI * 2;
  const hour = i % 5 === 0;
  const r1 = hour ? 75 : 81, r2 = 91;
  const sx = Math.sin(a), sy = -Math.cos(a);
  const p = (rr: number) => `${(LK.cx + sx * rr).toFixed(2)} ${(LK.cy + sy * rr).toFixed(2)}`;
  return { d: `M${p(r1)}L${p(r2)}`, hour };
});

export function CircleLock({ state, remainingMs, totalMs, locked, label, caption, size, text }: {
  state: TimerState; remainingMs: number; totalMs: number; locked: boolean;
  /** Spoken summary, minute-level ("Focus: 23 minutes left"). */
  label: string;
  /** Small words under the digits: "left this round", "left in break", "per round", "complete". */
  caption: string;
  /** CSS width; the height follows. */
  size?: string;
  /** Face text in place of the countdown (the idle lock shows the time of day). */
  text?: string;
}) {
  const f = state === 'done' ? 0 : totalMs > 0 ? Math.max(0, Math.min(1, remainingMs / totalMs)) : 0;
  // Like a clock: time passing sweeps clockwise from 12, dimming ticks; the ticks still lit are
  // the time left in this round. Idle (no total) shows plain clock ticks.
  const idle = text !== undefined && state === 'ready';
  // The shackle starts open and drops shut a moment after the lock appears, so locking is seen
  // happening (session start, end of a break); after that it follows `locked` with the same motion.
  const [settled, setSettled] = useState(false);
  useEffect(() => { const id = window.setTimeout(() => setSettled(true), 350); return () => window.clearTimeout(id); }, []);
  const closed = locked && settled;
  const elapsedTicks = Math.min(TICKS, Math.floor((1 - f) * TICKS + 1e-9));
  const minY = SHACKLE.top - SHACKLE.lift - 4;
  const h = LK.cy + LK.r + 4 - minY;
  return (
    <div className={`clock-lock clock-lock--${state}${locked ? ' is-locked' : ''}`} role="timer" aria-label={label} style={size ? { width: size } : undefined}>
      <svg viewBox={`0 ${minY} ${LK.w} ${h}`} aria-hidden="true" focusable="false">
        {/* The shackle slides down to lock and lifts to unlock; the body covers its seated legs. */}
        <g className="clock-lock__shackle" style={{ transform: `translateY(${closed ? 0 : -SHACKLE.lift}px)` }}>
          <path className="clock-lock__frame" d={SHACKLE_PATH} />
        </g>
        <circle className="clock-lock__frame" cx={LK.cx} cy={LK.cy} r={LK.r} />
        <circle className="clock-lock__body" cx={LK.cx} cy={LK.cy} r={LK.r - LK.rim} />
        <g className="clock-lock__ticks">
          {TICK_PATHS.map((t, i) => (
            <path key={i} d={t.d} className={`clock-lock__tick${t.hour ? ' is-hour' : ''}${idle || i >= elapsedTicks ? ' is-left' : ''}`} />
          ))}
        </g>
        <text className="clock-lock__time" x={LK.cx} y={LK.cy + 14} textAnchor="middle">{text ?? (state === 'done' ? 'Done' : countdown(remainingMs))}</text>
        <text className="clock-lock__caption" x={LK.cx} y={LK.cy + 44} textAnchor="middle">{caption}</text>
      </svg>
    </div>
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

/**
 * The whole session as a timeline (the circle shows only the current round). Segment widths
 * follow minutes, the elapsed part is filled, breaks are dashed, and each segment is labelled.
 */
export function SessionRail({ phases, now, labels = true }: { phases: Pick<Phase, 'kind' | 'start' | 'end'>[]; now: number; labels?: boolean }) {
  const start = phases[0].start, end = phases[phases.length - 1].end;
  const current = phases.findIndex((p) => now >= p.start && now < p.end);
  const named = phases.length <= 5;
  const mins = (p: { start: number; end: number }) => Math.round((p.end - p.start) / 60_000);
  return (
    <div className="rail" aria-hidden="true">
      {labels && (
        <div className="rail__head">
          <span className="rail__title">Whole session</span>
          <span className="rail__span">{clockTime(start)}–{clockTime(end)}</span>
        </div>
      )}
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
      {labels && (named ? (
        <div className="rail__segs">
          {phases.map((p, i) => (
            <span key={i} className={`rail__name rail__name--${p.kind}${i === current ? ' is-current' : ''}`} style={{ flexGrow: p.end - p.start }}>
              {p.kind === 'focus' ? <>Focus<span className="rail__min"> · {mins(p)} min</span></> : 'Break'}
            </span>
          ))}
        </div>
      ) : (
        <div className="rail__legend"><span className="rail__key rail__key--focus" />Focus<span className="rail__key rail__key--break" />Break</div>
      ))}
    </div>
  );
}
