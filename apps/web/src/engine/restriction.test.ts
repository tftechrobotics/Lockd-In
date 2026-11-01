import { describe, expect, it } from 'vitest';
import { occurrenceFrom, restrictionIntervals, roundsPlan } from './session';
import { parseAgentStatus, parseOpenApps } from '../platform';
import { isAppId, parseState, restrictsAnything } from '../state/store';

const MIN = 60_000;
const t0 = 1_793_300_000_000;
const occ = occurrenceFrom(t0, roundsPlan(2, 25, 5)); // focus 25, break 5, focus 25

describe('restriction intervals for the Windows agent', () => {
  it('covers focus periods and skips the break', () => {
    expect(restrictionIntervals(occ, { overrides: [] }, t0)).toEqual([[t0, t0 + 25 * MIN], [t0 + 30 * MIN, t0 + 55 * MIN]]);
  });
  it('starts from now, mid-session', () => {
    expect(restrictionIntervals(occ, { overrides: [] }, t0 + 10 * MIN)).toEqual([[t0 + 10 * MIN, t0 + 25 * MIN], [t0 + 30 * MIN, t0 + 55 * MIN]]);
  });
  it('drops the unlocked round after the countdown, keeps later rounds', () => {
    const controls = { overrides: [{ id: 'o', scope: 'current_focus_period' as const, phaseIndex: 0, requestedAt: t0 + 5 * MIN, delaySeconds: 90 }] };
    expect(restrictionIntervals(occ, controls, t0 + 5 * MIN)).toEqual([[t0 + 5 * MIN, t0 + 5 * MIN + 90_000], [t0 + 30 * MIN, t0 + 55 * MIN]]);
  });
  it('is empty after emergency recovery and after the end', () => {
    expect(restrictionIntervals(occ, { overrides: [], emergencyRecoveryAt: t0 + MIN }, t0 + 2 * MIN)).toEqual([]);
    expect(restrictionIntervals(occ, { overrides: [] }, t0 + 60 * MIN)).toEqual([]);
  });
});

describe('agent messages from the Windows shell', () => {
  it('accepts a valid status and refuses unknown states', () => {
    expect(parseAgentStatus({ v: 1, type: 'host.agent_status', sessionId: 's1', protection: 'protecting', fresh: true, elevatedSkipped: 0 }))
      .toEqual({ sessionId: 's1', protection: 'protecting', fresh: true, elevatedSkipped: 0 });
    expect(parseAgentStatus({ v: 1, type: 'host.agent_status', protection: 'protected', fresh: true })).toBeNull();
    expect(parseAgentStatus({ v: 1, type: 'host.agent_status', protection: 'protecting' })).toBeNull();
  });
  it('filters the open-apps list', () => {
    expect(parseOpenApps({ v: 1, type: 'host.apps', apps: [{ exe: 'Discord.exe', name: 'Discord' }, { exe: 'x', name: 'bad' }, null] }))
      .toEqual([{ exe: 'Discord.exe', name: 'Discord' }]);
  });
});

describe('profile app rules', () => {
  it('validates program names like the agent does', () => {
    expect(isAppId('Discord.exe')).toBe(true);
    expect(isAppId('C:\\evil.exe')).toBe(false);
    expect(isAppId('discord')).toBe(false);
  });
  it('drops invalid stored apps and keeps old data without rules working', () => {
    const raw = JSON.stringify({ version: 1, profiles: [
      { id: 'a', name: 'A', description: '', builtIn: true, rules: { mode: 'approved_only', apps: ['Code.exe', '../x', 5] } },
      { id: 'b', name: 'B', description: '', builtIn: true },
    ] });
    const s = parseState(raw);
    expect(s.profiles[0].rules).toEqual({ mode: 'approved_only', apps: ['Code.exe'] });
    expect(s.profiles[1].rules).toBeUndefined();
    expect(restrictsAnything(s.profiles[1].rules)).toBe(false);
    expect(restrictsAnything({ mode: 'approved_only', apps: [] })).toBe(true);
  });
});
