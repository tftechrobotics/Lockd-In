// Recurring-session helpers for the web app's local schedule.
// Local times use the browser's time zone via Date. The Rust core implements
// RFC 5545 DST rules (nonexistent times shift later); this helper matches
// that for the spring-forward gap because Date normalizes 02:30 to 03:30 in
// US zones. Repeated (fall-back) times resolve to the first occurrence in
// V8, also matching the core. Covered by schedule.test.ts.
import type { ScheduledSession } from '../state/store';
import { roundsPlan } from './session';

export const totalMinutes = (s: Pick<ScheduledSession, 'focusMinutes' | 'breakMinutes' | 'rounds'>) =>
  s.rounds * s.focusMinutes + (s.rounds - 1) * s.breakMinutes;

export const planOf = (s: ScheduledSession) => roundsPlan(s.rounds, s.focusMinutes, s.rounds > 1 ? s.breakMinutes : 0);

/** Start instant of `s` on the local calendar day of `day`. */
export function startOn(s: ScheduledSession, day: Date): number {
  const [h, m] = s.startTime.split(':').map(Number);
  return new Date(day.getFullYear(), day.getMonth(), day.getDate(), h, m, 0, 0).getTime();
}

export interface Upcoming { session: ScheduledSession; start: number; end: number }

/** Next occurrence (starting after `now`, or in progress) across all sessions, within 8 days. */
export function nextUp(sessions: ScheduledSession[], now: number): Upcoming | null {
  let best: Upcoming | null = null;
  const today = new Date(now);
  for (let d = 0; d < 8; d++) {
    const day = new Date(today.getFullYear(), today.getMonth(), today.getDate() + d);
    for (const s of sessions) {
      if (!s.days.includes(day.getDay())) continue;
      const start = startOn(s, day);
      const end = start + totalMinutes(s) * 60_000;
      if (end <= now) continue;
      if (!best || start < best.start) best = { session: s, start, end };
    }
    if (best) return best;
  }
  return best;
}

/** Sessions in `others` that share a weekday and overlap `s` in local time. */
export function overlaps(s: ScheduledSession, others: ScheduledSession[]): ScheduledSession[] {
  const mins = (x: ScheduledSession) => { const [h, m] = x.startTime.split(':').map(Number); return h * 60 + m; };
  const a0 = mins(s), a1 = a0 + totalMinutes(s);
  return others.filter((o) => {
    if (o.id === s.id) return false;
    const b0 = mins(o), b1 = b0 + totalMinutes(o);
    // Same-day overlap, plus sessions that run past midnight into the next day.
    for (const d of s.days) {
      for (const e of o.days) {
        const shift = ((e - d + 7) % 7) * 1440;
        for (const off of [shift, shift - 7 * 1440]) {
          if (a0 < b1 + off && b0 + off < a1) return true;
        }
      }
    }
    return false;
  });
}
