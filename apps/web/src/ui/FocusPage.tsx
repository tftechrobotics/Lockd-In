import { useEffect, useId, useRef, useState } from 'react';
import {
  DEFAULT_OVERRIDE_DELAY_SECONDS, evaluate, occurrenceFrom, roundsPlan, validatePlan,
  type OverrideScope, type Snapshot,
} from '../engine/session';
import { nextUp } from '../engine/schedule';
import { getState, newId, update, useStore, type ActiveSession } from '../state/store';
import { DeviceStrip } from './DeviceStrip';
import { clockTime, countdown, dayLabel, minutesLabel } from './format';
import { Icon } from './Icon';
import { LockTimer } from './LockTimer';
import { message } from './messages';

/** Re-render every second while something is counting; never the source of truth. */
function useNow(active: boolean): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    if (!active) return;
    const tick = () => setNow(Date.now());
    const id = window.setInterval(tick, 1000);
    document.addEventListener('visibilitychange', tick);
    return () => { window.clearInterval(id); document.removeEventListener('visibilitychange', tick); };
  }, [active]);
  return active ? now : Date.now();
}

export function FocusPage() {
  const active = useStore((s) => s.active);
  return active ? <SessionView session={active} /> : <Setup />;
}

const LENGTHS = [10, 25, 40, 60];

function Setup() {
  const last = useStore((s) => s.last);
  const profiles = useStore((s) => s.profiles);
  const recent = useStore((s) => s.recentIntentions);
  const schedule = useStore((s) => s.schedule);
  const [intention, setIntention] = useState('');
  const [focus, setFocus] = useState(last.focusMinutes);
  const [custom, setCustom] = useState(!LENGTHS.includes(last.focusMinutes));
  const [breakMin, setBreakMin] = useState(last.rounds > 1 ? last.breakMinutes : 0);
  const [rounds, setRounds] = useState(Math.max(2, last.rounds));
  const [profileId, setProfileId] = useState(last.profileId);
  const [error, setError] = useState<string | null>(null);
  const ids = { intent: useId(), len: useId(), custom: useId(), brk: useId(), rounds: useId(), prof: useId(), list: useId(), err: useId() };
  const upcoming = nextUp(schedule, Date.now());

  const segments = breakMin > 0 ? roundsPlan(rounds, focus, breakMin) : roundsPlan(1, focus, 0);
  const profile = profiles.find((p) => p.id === profileId) ?? profiles[0];

  const start = () => {
    const problem = validatePlan(segments);
    if (problem) { setError(problem); return; }
    const text = intention.trim().slice(0, 120);
    update((s) => ({
      ...s,
      last: { focusMinutes: focus, breakMinutes: breakMin || s.last.breakMinutes, rounds: breakMin > 0 ? rounds : 1, profileId },
      active: {
        id: newId(), start: Date.now(), segments, profileId,
        intention: text ? { text } : null, controls: { overrides: [] },
      },
      recentIntentions: text ? [{ text }, ...s.recentIntentions.filter((i) => i.text !== text)].slice(0, 8) : s.recentIntentions,
    }));
  };

  const totalMin = segments.reduce((a, s) => a + s.minutes, 0);

  return (
    <div className="page focus">
      <h1 className="display">Ready when you are.</h1>
      <div className="focus__grid">
        <LockTimer remainingMs={focus * 60_000} caption="Focus time" locked={false} />
        <form className="setup" onSubmit={(e) => { e.preventDefault(); start(); }} noValidate>
          <div className="field">
            <label htmlFor={ids.intent}>What are you working on? <span className="optional">Optional</span></label>
            <input id={ids.intent} className="input" value={intention} maxLength={120} list={ids.list}
              placeholder="e.g. TAM 212 · Practice problems" autoComplete="off"
              onChange={(e) => setIntention(e.target.value)} />
            <datalist id={ids.list}>{recent.map((r) => <option key={r.text} value={r.text} />)}</datalist>
          </div>

          <fieldset className="field">
            <legend id={ids.len}>Focus time</legend>
            <div className="chips" role="radiogroup" aria-labelledby={ids.len}>
              {LENGTHS.map((m) => (
                <button key={m} type="button" role="radio" aria-checked={!custom && focus === m}
                  className="chip" onClick={() => { setCustom(false); setFocus(m); setError(null); }}>{m} min</button>
              ))}
              <button type="button" role="radio" aria-checked={custom} className="chip"
                onClick={() => setCustom(true)}>Custom</button>
            </div>
            {custom && (
              <div className="inline-number">
                <label htmlFor={ids.custom}>Minutes</label>
                <input id={ids.custom} className="input input--number" type="number" inputMode="numeric"
                  min={1} max={240} value={focus} aria-describedby={error ? ids.err : undefined}
                  onChange={(e) => { setFocus(Math.round(Number(e.target.value)) || 0); setError(null); }} />
              </div>
            )}
          </fieldset>

          <div className="field-row">
            <div className="field select-field">
              <label htmlFor={ids.brk}><Icon name="coffee" size={18} />Break</label>
              <select id={ids.brk} className="input" value={breakMin} onChange={(e) => { setBreakMin(Number(e.target.value)); setError(null); }}>
                <option value={0}>No break</option>
                {[5, 10, 15, 20].map((b) => <option key={b} value={b}>{b} min</option>)}
              </select>
            </div>
            <div className="field select-field">
              <label htmlFor={ids.prof}><Icon name="book" size={18} />Profile</label>
              <select id={ids.prof} className="input" value={profileId} onChange={(e) => setProfileId(e.target.value)}>
                {profiles.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </div>
          </div>
          {breakMin > 0 && (
            <div className="field select-field field--narrow">
              <label htmlFor={ids.rounds}>Focus periods</label>
              <select id={ids.rounds} className="input" value={rounds} onChange={(e) => setRounds(Number(e.target.value))}>
                {[2, 3, 4, 5, 6].map((r) => <option key={r} value={r}>{r}</option>)}
              </select>
            </div>
          )}

          <p className="summary">
            {breakMin > 0
              ? `${rounds} × ${minutesLabel(focus)} with ${breakMin}-minute breaks · ends around ${clockTime(Date.now() + totalMin * 60_000)}`
              : `${minutesLabel(focus)} · ends around ${clockTime(Date.now() + totalMin * 60_000)}`}
            {' · '}{profile.name} profile
          </p>
          {error && <p id={ids.err} className="error" role="alert">{error}</p>}
          <button type="submit" className="button button--primary button--wide">
            Lock in <Icon name="arrow" />
          </button>
        </form>
      </div>

      <DeviceStrip />
      <hr className="rule" />
      <NextRow upcoming={upcoming} />
    </div>
  );
}

function NextRow({ upcoming }: { upcoming: ReturnType<typeof nextUp> }) {
  return (
    <div className="next">
      <Icon name="calendar" size={22} />
      {upcoming ? (
        <p><strong>Next:</strong> {upcoming.session.title} · {dayLabel(upcoming.start)} {clockTime(upcoming.start)}</p>
      ) : (
        <p>Nothing scheduled yet.</p>
      )}
      <a className="link-arrow" href="#/schedule">{upcoming ? 'View schedule' : 'Plan a session'} <Icon name="arrow" size={18} /></a>
    </div>
  );
}

function SessionView({ session }: { session: ActiveSession }) {
  const tone = useStore((s) => s.tone);
  const profiles = useStore((s) => s.profiles);
  const occ = occurrenceFrom(session.start, session.segments);
  const firstSnap = evaluate(occ, session.controls, Date.now());
  const ended = firstSnap.status !== 'active' && firstSnap.status !== 'upcoming';
  const now = useNow(!ended);
  const snap = evaluate(occ, session.controls, now);
  const profile = profiles.find((p) => p.id === session.profileId);

  if (snap.status !== 'active' && snap.status !== 'upcoming') {
    return <Recap session={session} snap={snap} />;
  }

  const phase = snap.phase!;
  const remaining = phase.end - now;
  const focusing = snap.restricting;
  const focusCount = session.segments.filter((s) => s.kind === 'focus').length;
  const focusIndex = session.segments.slice(0, phase.index + 1).filter((s) => s.kind === 'focus').length;
  const nextPhase = occ.phases[phase.index + 1];
  const task = session.intention?.text;
  const lifted = snap.overrideState.type === 'active_for_focus_period';

  const headline = lifted ? 'Unlocked for now.' : focusing ? 'Locked in.' : 'On a break.';
  const caption = phase.kind === 'focus'
    ? (focusCount > 1 ? `Focus ${focusIndex} of ${focusCount}` : 'Focus time')
    : 'Break';
  const note = phase.kind === 'break'
    ? (remaining <= 60_000 ? message(tone, 'breakEnding', { task }) : message(tone, 'breakStart', { time: clockTime(phase.end) }))
    : lifted ? null : message(tone, 'focusStart', { task });

  return (
    <div className="page focus focus--active">
      <h1 className="display">{headline}</h1>
      {task && <p className="intention">{task}</p>}
      <div className="focus__grid">
        <LockTimer remainingMs={remaining} caption={caption} locked={focusing} />
        <div className="session">
          <dl className="facts">
            <div>
              <dt>{phase.kind === 'focus' ? (nextPhase ? 'Break at' : 'Ends at') : 'Focus resumes at'}</dt>
              <dd>{clockTime(phase.end)}</dd>
            </div>
            <div><dt>Session ends</dt><dd>{clockTime(occ.end)}</dd></div>
            <div><dt>Profile</dt><dd>{profile?.name ?? 'Custom'}</dd></div>
          </dl>
          {note && <p className="nudge" aria-live="polite">{note}</p>}
          {lifted && (
            <p className="nudge" aria-live="polite">
              Unlocked until {clockTime((snap.overrideState as { until: number }).until)}. Later focus periods lock again.
            </p>
          )}
          <p className="honesty">
            <Icon name="info" size={18} />
            <span>Nothing is blocked from this browser. Pair the Windows or Android app to restrict apps during focus.</span>
          </p>
          <OverridePanel session={session} snap={snap} now={now} />
        </div>
      </div>
      <DeviceStrip compact />
    </div>
  );
}

function OverridePanel({ session, snap, now }: { session: ActiveSession; snap: Snapshot; now: number }) {
  const [open, setOpen] = useState(false);
  const [scope, setScope] = useState<OverrideScope>('current_focus_period');
  const dialog = useRef<HTMLDialogElement>(null);
  const pending = snap.overrideState.type === 'pending' ? snap.overrideState : null;
  const inFocus = snap.phase?.kind === 'focus' && snap.overrideState.type !== 'active_for_focus_period';
  const setControls = (fn: (c: ActiveSession['controls']) => ActiveSession['controls']) =>
    update((s) => (s.active && s.active.id === session.id ? { ...s, active: { ...s.active, controls: fn(s.active.controls) } } : s));

  const request = () => {
    const effectiveScope: OverrideScope = inFocus ? scope : 'rest_of_session';
    setControls((c) => ({
      ...c,
      overrides: [...c.overrides, {
        id: newId(), scope: effectiveScope, phaseIndex: snap.phase!.index,
        requestedAt: Date.now(), delaySeconds: DEFAULT_OVERRIDE_DELAY_SECONDS,
      }],
    }));
    setOpen(false);
  };
  const cancel = () => setControls((c) => ({
    ...c,
    overrides: c.overrides.map((o) => (o.cancelledAt === undefined && o.requestedAt + o.delaySeconds * 1000 > Date.now() ? { ...o, cancelledAt: Date.now() } : o)),
  }));
  const openEmergency = () => {
    dialog.current?.showModal();
    // The safe choice gets focus (React's autoFocus doesn't apply to a native dialog).
    dialog.current?.querySelector<HTMLButtonElement>('[data-safe]')?.focus();
  };
  const emergency = () => {
    setControls((c) => ({ ...c, emergencyRecoveryAt: Date.now() }));
    dialog.current?.close();
  };

  return (
    <div className="out">
      {pending ? (
        <div className="pending" role="status">
          <p><strong>Unlocking in {countdown(pending.effectiveAt - now)}</strong>{' '}
            {pending.scope === 'current_focus_period' ? 'for this focus period.' : '— the session will end.'}</p>
          <button type="button" className="button" onClick={cancel}>Cancel unlock</button>
        </div>
      ) : open ? (
        <fieldset className="out__choices">
          <legend>Unlock waits {DEFAULT_OVERRIDE_DELAY_SECONDS} seconds. You can cancel while it counts down.</legend>
          {inFocus && (
            <label className="radio">
              <input type="radio" name="scope" checked={scope === 'current_focus_period'} onChange={() => setScope('current_focus_period')} />
              <span><strong>This focus period</strong><br />Later focus periods lock again</span>
            </label>
          )}
          <label className="radio">
            <input type="radio" name="scope" checked={!inFocus || scope === 'rest_of_session'} onChange={() => setScope('rest_of_session')} />
            <span><strong>End this session</strong><br />Your schedule for other days doesn’t change</span>
          </label>
          <div className="out__actions">
            <button type="button" className="button" onClick={request}>Start {DEFAULT_OVERRIDE_DELAY_SECONDS}-second unlock</button>
            <button type="button" className="button button--quiet" onClick={() => setOpen(false)}>Keep going</button>
          </div>
        </fieldset>
      ) : (
        <button type="button" className="button button--quiet" onClick={() => setOpen(true)}>Need out?</button>
      )}
      <button type="button" className="button button--quiet button--danger" onClick={openEmergency}>
        Emergency: end now
      </button>
      <dialog ref={dialog} className="dialog" aria-labelledby="emg-title">
        <h2 id="emg-title">End the session now?</h2>
        <p>This ends it right away, with no countdown, on this browser. Paired devices get the change when they next connect.</p>
        <div className="out__actions">
          <button type="button" className="button button--danger-solid" onClick={emergency}>End session now</button>
          <button type="button" className="button" data-safe onClick={() => dialog.current?.close()}>Keep going</button>
        </div>
      </dialog>
    </div>
  );
}

function Recap({ session, snap }: { session: ActiveSession; snap: Snapshot }) {
  const tone = useStore((s) => s.tone);
  const occ = occurrenceFrom(session.start, session.segments);
  const endedAt = snap.status === 'completed' ? occ.end
    : snap.status === 'ended_by_emergency_recovery' ? session.controls.emergencyRecoveryAt!
    : Math.min(...session.controls.overrides.filter((o) => o.scope === 'rest_of_session' && o.cancelledAt === undefined)
        .map((o) => o.requestedAt + o.delaySeconds * 1000), occ.end);
  const focusedMs = occ.phases.filter((p) => p.kind === 'focus')
    .reduce((a, p) => a + Math.max(0, Math.min(p.end, endedAt) - p.start), 0);
  const unlockedEarly = session.controls.overrides.filter((o) => o.scope === 'current_focus_period' && o.cancelledAt === undefined).length;
  const how = snap.status === 'completed' ? 'Finished as planned.'
    : snap.status === 'ended_by_override' ? 'Ended early with an unlock.' : 'Ended with emergency recovery.';
  const finish = () => update((s) => ({ ...s, active: null }));
  const again = () => getState().active && update((s) => ({
    ...s,
    active: { ...session, id: newId(), start: Date.now(), controls: { overrides: [] } },
  }));
  const note = snap.status === 'completed' ? message(tone, 'done') : null;

  return (
    <div className="page focus">
      <h1 className="display">Session complete.</h1>
      {session.intention && <p className="intention">{session.intention.text}</p>}
      <div className="focus__grid">
        <LockTimer remainingMs={0} caption="Done" locked={false} />
        <div className="session">
          <dl className="facts">
            <div><dt>Focused</dt><dd>{Math.round(focusedMs / 60_000)} min</dd></div>
            <div><dt>Time</dt><dd className="facts__text">{clockTime(session.start)}–{clockTime(endedAt)}</dd></div>
            <div><dt>How it ended</dt><dd className="facts__text">{how}</dd></div>
            {unlockedEarly > 0 && <div><dt>Unlocked early</dt><dd>{unlockedEarly} focus period{unlockedEarly > 1 ? 's' : ''}</dd></div>}
          </dl>
          {note && <p className="nudge">{note}</p>}
          <div className="out__actions">
            <button type="button" className="button button--primary" onClick={again}>Same again <Icon name="arrow" /></button>
            <button type="button" className="button" onClick={finish}>Done</button>
          </div>
        </div>
      </div>
    </div>
  );
}
