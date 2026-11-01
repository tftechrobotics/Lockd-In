// Time left in the current phase, shown on the Focus tab while you're on another page.
import { evaluate, occurrenceFrom } from '../engine/session';
import { useStore } from '../state/store';
import { useNow } from './FocusPage';
import { countdown, spokenRemaining } from './format';

export function RunningClock() {
  const active = useStore((s) => s.active);
  const now = useNow(active !== null);
  if (!active) return null;
  const snap = evaluate(occurrenceFrom(active.start, active.segments), active.controls, now);
  if (snap.status !== 'active' || !snap.phase) return null;
  const left = snap.phase.end - now;
  const what = snap.phase.kind === 'break' ? 'break' : 'focus';
  return (
    <span className={`run-clock run-clock--${what}`} role="img" aria-label={`session running, ${what} ${spokenRemaining(left)}`}>
      {countdown(left)}
    </span>
  );
}
