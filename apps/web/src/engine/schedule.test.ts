import { describe, expect, it } from 'vitest';
import { nextUp, overlaps, startOn as startOnLocal, totalMinutes } from './schedule';
import type { ScheduledSession } from '../state/store';

const mk = (o: Partial<ScheduledSession>): ScheduledSession => ({
  id: 'x', title: 'Study', days: [1, 2, 3, 4], startTime: '18:00', focusMinutes: 40, breakMinutes: 10, rounds: 2, profileId: 'study', ...o,
});

describe('schedule', () => {
  it('totals focus and breaks', () => {
    expect(totalMinutes(mk({}))).toBe(90);
    expect(totalMinutes(mk({ rounds: 1 }))).toBe(40);
  });

  it('finds the next session today, then later days', () => {
    // Tuesday 2026-11-03 17:00 local
    const now = new Date(2026, 10, 3, 17, 0).getTime();
    const n = nextUp([mk({})], now)!;
    expect(new Date(n.start).getHours()).toBe(18);
    expect(new Date(n.start).getDate()).toBe(3);
    const later = nextUp([mk({ days: [5] })], now)!; // Friday
    expect(new Date(later.start).getDate()).toBe(6);
  });

  it('keeps a session in progress as next up', () => {
    const now = new Date(2026, 10, 3, 18, 30).getTime();
    expect(nextUp([mk({})], now)?.start).toBe(new Date(2026, 10, 3, 18, 0).getTime());
  });

  it('returns null when nothing is scheduled', () => {
    expect(nextUp([], Date.now())).toBeNull();
  });

  it('detects overlap on shared days only, and across midnight', () => {
    const a = mk({ id: 'a' });
    expect(overlaps(a, [mk({ id: 'b', startTime: '19:00' })])).toHaveLength(1);
    expect(overlaps(a, [mk({ id: 'b', startTime: '19:30' })])).toHaveLength(0); // adjacent, not overlapping
    expect(overlaps(a, [mk({ id: 'b', startTime: '18:30', days: [6] })])).toHaveLength(0);
    const late = mk({ id: 'c', days: [1], startTime: '23:30' });
    expect(overlaps(late, [mk({ id: 'd', days: [2], startTime: '00:15' })])).toHaveLength(1);
  });
});

describe('daylight-saving behavior matches the core (America/Chicago)', () => {
  it('spring-forward gap shifts later; fall-back repeat uses the first occurrence', () => {
    const prev = process.env.TZ;
    process.env.TZ = 'America/Chicago';
    try {
      const s = mk({ startTime: '02:30' });
      const spring = new Date(2027, 2, 14); // 14 Mar 2027: 02:00 -> 03:00
      expect(new Date(startOnLocal(s, spring)).getHours()).toBe(3);
      expect(startOnLocal(s, spring)).toBe(Date.UTC(2027, 2, 14, 8, 30)); // 03:30 CDT
      const f = mk({ startTime: '01:30' });
      const fall = new Date(2026, 10, 1); // 1 Nov 2026: 02:00 CDT -> 01:00 CST
      expect(startOnLocal(f, fall)).toBe(Date.UTC(2026, 10, 1, 6, 30)); // first 01:30 (CDT)
    } finally {
      process.env.TZ = prev;
    }
  });
  it('handles one-time and disabled sessions', () => {
    const now = new Date(2026, 9, 26, 12, 0).getTime(); // Monday noon
    const once = mk({ id: 'o', days: [], date: '2026-10-28', startTime: '09:00' });
    const off = mk({ id: 'off', startTime: '13:00', enabled: false });
    const n = nextUp([once, off], now)!;
    expect(n.session.id).toBe('o');
    expect(n.start).toBe(new Date(2026, 9, 28, 9, 0).getTime());
    const wed = mk({ id: 'w', days: [3], startTime: '09:30' });
    expect(overlaps(once, [wed])).toHaveLength(1);
    expect(overlaps(once, [{ ...wed, enabled: false }])).toHaveLength(0);
    expect(overlaps(once, [{ ...once, id: 'o2', date: '2026-11-04' }])).toHaveLength(0);
  });
});
