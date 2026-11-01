// Focus (design pass 4): a question-led session composer for setup, and a numerals-and-rail
// timer while running. Session logic (engine, store, override rules) is unchanged.
import { useEffect, useId, useRef, useState } from 'react';
import {
  DEFAULT_OVERRIDE_DELAY_SECONDS, evaluate, occurrenceFrom, roundsPlan, validatePlan,
  type OverrideScope, type Snapshot,
} from '../engine/session';
import { nextUp } from '../engine/schedule';
import { getState, newId, update, useStore, type ActiveSession } from '../state/store';
import { DeviceStrip } from './DeviceStrip';
import { clockTime, countdown, dayLabel, durationLabel, spokenRemaining } from './format';
import { Icon } from './Icon';
import { message } from './messages';
import { Numerals, SessionRail, TimerStatus } from './Timer';
import { layout, useHost } from '../platform';

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

const LENGTHS = [25, 40, 60];
const BREAKS = [5, 10, 15, 20];
const ROUNDS = [1, 2, 3, 4, 5, 6];

/** Whether this surface can restrict apps right now (only a running Windows blocking service can). */
export function useCanBlock(): boolean {
  const host = useHost();
  return layout === 'desktop' && host?.agent === 'running';
}

/* ------------------------------- Setup ------------------------------- */

function Setup() {
  const last = useStore((s) => s.last);
  const profiles = useStore((s) => s.profiles);
  const recent = useStore((s) => s.recentIntentions);
  const schedule = useStore((s) => s.schedule);
  const canBlock = useCanBlock();
  const [intention, setIntention] = useState('');
  const [focus, setFocus] = useState(last.focusMinutes);
  const [customOpen, setCustomOpen] = useState(!LENGTHS.includes(last.focusMinutes));
  const [breakMin, setBreakMin] = useState(last.rounds > 1 ? last.breakMinutes : 0);
  const [rounds, setRounds] = useState(last.rounds > 1 ? last.rounds : 1);
  const [roundsOpen, setRoundsOpen] = useState(false);
  const [profileId, setProfileId] = useState(last.profileId);
  const [error, setError] = useState<string | null>(null);
  const ids = { intent: useId(), len: useId(), custom: useId(), brk: useId(), rnd: useId(), prof: useId(), list: useId(), err: useId() };
  const now = useNow(true);
  const upcoming = nextUp(schedule, now);

  // Rounds need a break between them, so the two settings keep each other consistent.
  const effRounds = breakMin > 0 ? rounds : 1;
  const segments = roundsPlan(effRounds, focus, effRounds > 1 ? breakMin : 0);
  const total = segments.reduce((a, s) => a + s.minutes, 0);
  const plan = effRounds > 1 ? `${effRounds} rounds · ${breakMin} min break between` : 'One round, no breaks';
  // A custom value equal to a preset shows the preset as selected.
  const preset = LENGTHS.includes(focus) ? focus : null;
  const valid = validatePlan(segments) === null;

  const start = () => {
    const problem = validatePlan(segments);
    if (problem) { setError(problem); return; }
    const text = intention.trim().slice(0, 120);
    update((s) => ({
      ...s,
      last: { focusMinutes: focus, breakMinutes: breakMin || s.last.breakMinutes, rounds: effRounds, profileId },
      active: {
        id: newId(), start: Date.now(), segments, profileId,
        intention: text ? { text } : null, controls: { overrides: [] },
      },
      recentIntentions: text ? [{ text }, ...s.recentIntentions.filter((i) => i.text !== text)].slice(0, 8) : s.recentIntentions,
    }));
  };

  return (
    <div className={`page focus focus--${layout}`}>
      <h1 className="sr-only">Plan a focus session</h1>
      <div className="composer-layout">
        <form className="composer" onSubmit={(e) => { e.preventDefault(); start(); }} noValidate>
          <div className="q">
            <label className="q__label" htmlFor={ids.intent}>What are you working on?</label>
            <input id={ids.intent} className="bigfield" value={intention} maxLength={120} list={ids.list}
              placeholder="Name it, or leave it blank" autoComplete="off" onChange={(e) => setIntention(e.target.value)} />
            <datalist id={ids.list}>{recent.map((r) => <option key={r.text} value={r.text} />)}</datalist>
          </div>

          <div className="q">
            <span className="q__label" id={ids.len}>How long?</span>
            <div className="tiles" role="radiogroup" aria-labelledby={ids.len}>
              {LENGTHS.map((m) => (
                <button key={m} type="button" role="radio" aria-checked={preset === m} className="tile" aria-label={`${m} minutes`}
                  onClick={() => { setCustomOpen(false); setFocus(m); setError(null); }}>
                  <span className="tile__num" aria-hidden="true">{m}</span><span className="tile__unit" aria-hidden="true">min</span>
                </button>
              ))}
              <button type="button" role="radio" aria-checked={preset === null} className="tile tile--custom" aria-label="Custom minutes" onClick={() => setCustomOpen(true)}>
                <span className="tile__unit" aria-hidden="true">Custom</span>
              </button>
            </div>
            {(customOpen || preset === null) && (
              <div className="custom">
                <label htmlFor={ids.custom}>Custom length</label>
                <span className="custom__input">
                  <input id={ids.custom} className="input input--number" type="number" inputMode="numeric"
                    min={1} max={240} value={focus || ''} aria-describedby={error ? ids.err : undefined}
                    onChange={(e) => { setFocus(Math.round(Number(e.target.value)) || 0); setError(null); }} />
                  <span aria-hidden="true">min per round</span>
                </span>
              </div>
            )}
            <div className="rounds">
              <span>{plan}</span>
              <button type="button" className="link" aria-expanded={roundsOpen} onClick={() => setRoundsOpen(!roundsOpen)}>
                {roundsOpen ? 'Done' : 'Change'}<span className="sr-only"> rounds and breaks</span>
              </button>
            </div>
            {roundsOpen && (
              <div className="rounds__edit">
                <div className="field">
                  <label htmlFor={ids.rnd}>Focus rounds</label>
                  <select id={ids.rnd} className="input" value={effRounds}
                    onChange={(e) => { const r = Number(e.target.value); setRounds(r); setBreakMin(r > 1 ? (breakMin || 10) : 0); }}>
                    {ROUNDS.map((r) => <option key={r} value={r}>{r === 1 ? '1 round' : `${r} rounds`}</option>)}
                  </select>
                </div>
                <div className="field">
                  <label htmlFor={ids.brk}>Breaks</label>
                  <select id={ids.brk} className="input" value={effRounds > 1 ? breakMin : 0}
                    onChange={(e) => { const b = Number(e.target.value); setBreakMin(b); setRounds(b > 0 ? Math.max(2, rounds) : 1); setError(null); }}>
                    <option value={0}>None</option>
                    {BREAKS.map((b) => <option key={b} value={b}>{b} min</option>)}
                  </select>
                </div>
              </div>
            )}
          </div>

          <div className="q">
            <span className="q__label" id={ids.prof}>Which profile?</span>
            <div className="chips" role="radiogroup" aria-labelledby={ids.prof}>
              {profiles.map((p) => (
                <button key={p.id} type="button" role="radio" aria-checked={profileId === p.id} className="chip" onClick={() => setProfileId(p.id)}>{p.name}</button>
              ))}
            </div>
          </div>

          {error && <p id={ids.err} className="error" role="alert">{error}</p>}
          <div className="go">
            <div className="go__row">
              <button type="submit" className="button button--primary button--start">{canBlock ? 'Start focus' : 'Start timer'}</button>
              {valid && <p className="go__summary"><strong>Ends at {clockTime(now + total * 60_000)}</strong> · {durationLabel(total)}{effRounds > 1 ? ' with breaks' : ''}</p>}
            </div>
            <Protection canBlock={canBlock} />
          </div>
        </form>

        {layout === 'web' && valid && (
          <aside className="preview" aria-label="Session preview">
            <TimerStatus state="ready" closed={false} text="Ready" />
            <Numerals state="ready" ms={focus * 60_000} label={`${focus} minutes per round`} size="medium" />
            <SessionRail phases={occurrenceFrom(now, segments).phases} now={now} />
            <p className="preview__ends">If you start now, you’re done at <strong>{clockTime(now + total * 60_000)}</strong>.</p>
          </aside>
        )}
      </div>

      <footer className="focus__foot">
        <DeviceStrip />
        <NextRow upcoming={upcoming} />
      </footer>
    </div>
  );
}

/** One honest line about blocking, with a way to set it up when it isn't available. */
function Protection({ canBlock, during = false }: { canBlock: boolean; during?: boolean }) {
  if (canBlock) {
    return (
      <p className="protection protection--on">
        <span className="dot dot--ok" aria-hidden="true" />
        <span>{during ? 'Apps outside your profile are restricted.' : 'Apps outside your profile are restricted during focus.'}</span>
      </p>
    );
  }
  const where = layout === 'web' ? 'Browsers can’t restrict apps, so apps and websites stay available.' : 'Apps and websites stay available on this PC.';
  return (
    <p className="protection">
      <span className="dot" aria-hidden="true" />
      <span>Timer only. {where} <a href="#/devices">Set up blocking</a></span>
    </p>
  );
}

function NextRow({ upcoming }: { upcoming: ReturnType<typeof nextUp> }) {
  return (
    <a className="next" href="#/schedule">
      <Icon name="calendar" size={20} />
      <span className="next__text">
        <span className="next__label">Next</span>{' '}
        {upcoming ? `${upcoming.session.title} · ${dayLabel(upcoming.start)} ${clockTime(upcoming.start)}` : 'Nothing planned yet'}
      </span>
      <span className="next__go">{upcoming ? 'Schedule' : 'Plan one'} <Icon name="chevron" size={16} /></span>
    </a>
  );
}

/* ------------------------------- Running ------------------------------- */

function SessionView({ session }: { session: ActiveSession }) {
  const tone = useStore((s) => s.tone);
  const profiles = useStore((s) => s.profiles);
  const canBlock = useCanBlock();
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
  const focusCount = session.segments.filter((s) => s.kind === 'focus').length;
  const focusIndex = session.segments.slice(0, phase.index + 1).filter((s) => s.kind === 'focus').length;
  const nextPhase = occ.phases[phase.index + 1];
  const task = session.intention?.text;
  const lifted = snap.overrideState.type === 'active_for_focus_period';
  const protectedNow = canBlock && snap.restricting;
  const round = focusCount > 1 ? ` · round ${focusIndex} of ${focusCount}` : '';
  const state = phase.kind === 'break' ? 'break' : 'focus';
  const status = phase.kind === 'break' ? `On a break${round}` : lifted ? `Unlocked${round}` : `Focusing${round}`;
  const note = phase.kind === 'break'
    ? (remaining <= 60_000 ? message(tone, 'breakEnding', { task }) : message(tone, 'breakStart', { time: clockTime(phase.end) }))
    : lifted ? `Unlocked until ${clockTime((snap.overrideState as { until: number }).until)}. Later rounds start as planned.`
    : message(tone, 'focusStart', { task });
  const facts: [string, string][] = [
    phase.kind === 'break' ? ['Focus resumes', clockTime(phase.end)] : nextPhase ? ['Next break', clockTime(phase.end)] : ['Ends at', clockTime(phase.end)],
    ...(nextPhase ? [['Session ends', clockTime(occ.end)] as [string, string]] : []),
    ...(profile ? [['Profile', profile.name] as [string, string]] : []),
  ];

  return (
    <div className={`page focus focus--${layout} focus--running`}>
      <div className="running">
        <h1 className="running__task">{task ?? (phase.kind === 'break' ? 'Break' : 'Focus session')}</h1>
        <TimerStatus state={state} closed={protectedNow} text={status} live />
        <Numerals state={state} ms={remaining} label={`${phase.kind === 'break' ? 'Break' : 'Focus'}: ${spokenRemaining(remaining)}`} />
        <SessionRail phases={occ.phases} now={now} />
        <div className="running__foot">
          <div className="running__info">
            <dl className="facts">
              {facts.map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}
            </dl>
            {note && <p className="nudge" aria-live="polite">{note}</p>}
            <Protection canBlock={canBlock} during />
          </div>
          <OverridePanel session={session} snap={snap} now={now} />
        </div>
      </div>
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

  // "End now" (emergency recovery) lives inside the Need out? panel, one step away, so it
  // never competes with the timer but is always reachable.
  return (
    <div className={`out ${open || pending ? 'out--open' : ''}`}>
      {pending ? (
        <div className="pending" role="status">
          <p><strong>Unlocking in {countdown(pending.effectiveAt - now)}</strong>
            {pending.scope === 'current_focus_period' ? ' · this round' : ' · ends the session'}</p>
          <div className="out__actions">
            <button type="button" className="button" onClick={cancel}>Cancel unlock</button>
            <button type="button" className="button button--quiet button--danger" onClick={openEmergency}>End now</button>
          </div>
        </div>
      ) : open ? (
        <fieldset className="out__choices">
          <legend>Unlock after a {DEFAULT_OVERRIDE_DELAY_SECONDS}-second wait</legend>
          {inFocus && (
            <label className="radio">
              <input type="radio" name="scope" checked={scope === 'current_focus_period'} onChange={() => setScope('current_focus_period')} />
              <span>This round</span>
            </label>
          )}
          <label className="radio">
            <input type="radio" name="scope" checked={!inFocus || scope === 'rest_of_session'} onChange={() => setScope('rest_of_session')} />
            <span>Rest of this session</span>
          </label>
          <div className="out__actions">
            <button type="button" className="button" onClick={request}>Start unlock</button>
            <button type="button" className="button button--quiet" onClick={() => setOpen(false)}>Cancel</button>
            <button type="button" className="button button--quiet button--danger out__end" onClick={openEmergency}>End now</button>
          </div>
        </fieldset>
      ) : (
        <button type="button" className="button" onClick={() => setOpen(true)}>Need out?</button>
      )}
      <dialog ref={dialog} className="dialog" aria-labelledby="emg-title">
        <h2 id="emg-title">End the session now?</h2>
        <p>Emergency recovery ends it immediately, with no countdown.</p>
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
  const profiles = useStore((s) => s.profiles);
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
  const profile = profiles.find((p) => p.id === session.profileId);

  const facts: [string, string][] = [
    ['Focused', `${Math.round(focusedMs / 60_000)} min`],
    ['Time', `${clockTime(session.start)}–${clockTime(endedAt)}`],
    ...(profile ? [['Profile', profile.name] as [string, string]] : []),
    ...(unlockedEarly > 0 ? [['Unlocked early', `${unlockedEarly} round${unlockedEarly > 1 ? 's' : ''}`] as [string, string]] : []),
  ];

  return (
    <div className={`page focus focus--${layout} focus--running`}>
      <div className="running">
        <h1 className="running__task">{session.intention?.text ?? 'Focus session'}</h1>
        <TimerStatus state="done" closed={false} text="Session complete" />
        <Numerals state="done" ms={0} label="Session complete" />
        <SessionRail phases={occ.phases} now={endedAt} />
        <div className="running__foot">
          <div className="running__info">
            <dl className="facts">
              {facts.map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}
            </dl>
            <p className="nudge">{how}{note ? ` ${note}` : ''}</p>
          </div>
          <div className="out__actions">
            <button type="button" className="button button--primary" onClick={again}>Same again</button>
            <button type="button" className="button" onClick={finish}>Done</button>
          </div>
        </div>
      </div>
    </div>
  );
}
