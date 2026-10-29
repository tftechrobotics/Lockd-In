import { describe, expect, it } from 'vitest';
import vectors from './parity-vectors.json';
import {
  evaluate, occurrenceFrom, roundsPlan, validatePlan,
  type OverrideScope, type PhaseKind, type SessionControls,
} from './session';

const T0 = Date.UTC(2026, 10, 3, 18, 0, 0);
const s = (sec: number) => T0 + sec * 1000;

describe('parity with the Rust core (core/examples/parity_vectors.rs)', () => {
  for (const c of vectors.cases) {
    it(c.name, () => {
      const occ = occurrenceFrom(T0, c.segments.map((x) => ({ kind: x.kind as PhaseKind, minutes: x.minutes })));
      const controls: SessionControls = {
        overrides: c.controls.overrides.map((o) => ({
          id: o.id, scope: o.scope as OverrideScope, phaseIndex: o.phase_index,
          requestedAt: s(o.requested_at), delaySeconds: o.delay_seconds,
          cancelledAt: o.cancelled_at === null ? undefined : s(o.cancelled_at),
        })),
        emergencyRecoveryAt: c.controls.emergency_recovery === null ? undefined : s(c.controls.emergency_recovery),
      };
      for (const v of c.samples) {
        const snap = evaluate(occ, controls, s(v.t));
        const ov = snap.overrideState;
        const got = {
          t: v.t,
          status: snap.status,
          restricting: snap.restricting,
          release_reason: snap.releaseReason,
          phase_index: snap.phase?.index ?? null,
          override_state: ov.type === 'none' ? { type: 'none' }
            : ov.type === 'pending' ? { type: 'pending', scope: ov.scope, effective_at: (ov.effectiveAt - T0) / 1000 }
            : { type: 'active_for_focus_period', until: (ov.until - T0) / 1000 },
          next_transition: snap.nextTransition === null ? null : (snap.nextTransition - T0) / 1000,
        };
        expect(got).toEqual(v);
      }
    });
  }
});

describe('plans', () => {
  it('builds alternating rounds', () => {
    expect(roundsPlan(2, 40, 10)).toEqual([
      { kind: 'focus', minutes: 40 }, { kind: 'break', minutes: 10 }, { kind: 'focus', minutes: 40 },
    ]);
  });
  it('rejects plans the core would reject', () => {
    expect(validatePlan([])).not.toBeNull();
    expect(validatePlan([{ kind: 'break', minutes: 5 }])).not.toBeNull();
    expect(validatePlan([{ kind: 'focus', minutes: 0 }])).not.toBeNull();
    expect(validatePlan([{ kind: 'focus', minutes: 241 }])).not.toBeNull();
    expect(validatePlan(roundsPlan(4, 240, 120))).not.toBeNull(); // > 12 h
    expect(validatePlan(roundsPlan(2, 40, 10))).toBeNull();
  });
});
