import { useEffect, useId, useRef, useState } from 'react';
import {
  DEFAULT_OVERRIDE_DELAY_SECONDS, evaluate, occurrenceFrom, roundsPlan, validatePlan,
  type OverrideScope, type Snapshot,
} from '../engine/session';
import { nextUp } from '../engine/schedule';
import { getState, newId, update, useStore, type ActiveSession } from '../state/store';
import { DeviceStrip } from './DeviceStrip';
import { clockTime, countdown, dayLabel } from './format';
import { Icon, type IconName } from './Icon';
import { LockTimer } from './LockTimer';
import { message } from './messages';
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

/**
 * The Focus composition shared by setup, session and recap. Web: headline,
 * then lock beside the controls. Desktop (Windows shell): one centered column.
 */
function FocusLayout({ head, lock, children, foot }: {
  head: React.ReactNode; lock: React.ReactNode; children: React.ReactNode; foot: React.ReactNode;
}) {
  return (
    <div className={`page focus focus--${layout}`}>
      <div className="focus__head">{head}</div>
      <div className="focus__stage">
        <div className="focus__lock">{lock}</div>
        <div className="focus__controls">{children}</div>
      </div>
      <div className="focus__foot">{foot}</div>
    </div>
  );
}

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
  const ids = { intent: useId(), len: useId(), custom: useId(), list: useId(), err: useId() };
  const upcoming = nextUp(schedule, Date.now());

  const segments = breakMin > 0 ? roundsPlan(rounds, focus, breakMin) : roundsPlan(1, focus, 0);

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

  const desktop = layout === 'desktop';
  const task = (
    <>
      <input id={ids.intent} className={desktop ? 'task-title' : 'input'} value={intention} maxLength={120} list={ids.list}
        placeholder={desktop ? 'What are you working on?' : 'e.g. TAM 212 · Practice problems'} autoComplete="off"
        aria-label={desktop ? 'What are you working on?' : undefined}
        onChange={(e) => setIntention(e.target.value)} />
      <datalist id={ids.list}>{recent.map((r) => <option key={r.text} value={r.text} />)}</datalist>
    </>
  );

  return (
    <form onSubmit={(e) => { e.preventDefault(); start(); }} noValidate>
      <FocusLayout
        head={desktop
          ? <><h1 className="sr-only">Start a focus session</h1>{task}</>
          : <h1 className="display">Ready when you are.</h1>}
        lock={<LockTimer remainingMs={focus * 60_000} caption="Focus time" locked={false} />}
        foot={<><DeviceStrip /><NextRow upcoming={upcoming} /></>}
      >
        {!desktop && (
          <div className="field">
            <label htmlFor={ids.intent}>What are you working on?</label>
            {task}
          </div>
        )}
        <div className="field">
          <div className="field__row">
            <span className="field__label" id={ids.len}>Focus time</span>
            {breakMin > 0 && (
              <Select icon="focus" label="Focus periods" value={rounds} onChange={setRounds} quiet
                options={[2, 3, 4, 5, 6].map((r) => [r, `${r} periods`])} />
            )}
          </div>
          <div className="pills" role="radiogroup" aria-labelledby={ids.len}>
            {LENGTHS.map((m) => (
              <button key={m} type="button" role="radio" className="pill" aria-checked={!custom && focus === m}
                onClick={() => { setCustom(false); setFocus(m); setError(null); }}>{m} min</button>
            ))}
            <button type="button" role="radio" className="pill" aria-checked={custom} onClick={() => setCustom(true)}>Custom</button>
          </div>
          {custom && (
            <div className="inline-number">
              <label htmlFor={ids.custom}>Minutes</label>
              <input id={ids.custom} className="input input--number" type="number" inputMode="numeric"
                min={1} max={240} value={focus} aria-describedby={error ? ids.err : undefined}
                onChange={(e) => { setFocus(Math.round(Number(e.target.value)) || 0); setError(null); }} />
            </div>
          )}
        </div>
        <div className="selects">
          <Select icon="coffee" label="Break" value={breakMin} onChange={(v) => { setBreakMin(v); setError(null); }}
            options={[[0, 'No break'], ...BREAKS.map((b) => [b, `Break · ${b} min`] as [number, string])]} />
          <Select icon="book" label="Profile" value={profileId} onChange={setProfileId}
            options={profiles.map((p) => [p.id, `${p.name} profile`])} />
        </div>
        {error && <p id={ids.err} className="error" role="alert">{error}</p>}
        <button type="submit" className="button button--primary button--lockin">Lock in <Icon name="arrow" size={22} /></button>
      </FocusLayout>
    </form>
  );
}

/** A native select dressed as the reference's dropdown: leading icon, value text, chevron. */
function Select<T extends string | number>({ icon, label, value, options, onChange, quiet = false }: {
  icon: IconName; label: string; value: T; options: [T, string][]; onChange: (v: T) => void; quiet?: boolean;
}) {
  return (
    <span className={`select ${quiet ? 'select--quiet' : ''}`}>
      {!quiet && <Icon name={icon} size={20} />}
      <select aria-label={label} value={String(value)}
        onChange={(e) => {
          const raw = e.target.value;
          onChange((typeof value === 'number' ? Number(raw) : raw) as T);
        }}>
        {options.map(([v, text]) => <option key={String(v)} value={String(v)}>{text}</option>)}
      </select>
      <Icon name="chevronDown" size={16} />
    </span>
  );
}

function NextRow({ upcoming }: { upcoming: ReturnType<typeof nextUp> }) {
  const when = upcoming ? `${upcoming.session.title} · ${dayLabel(upcoming.start)} ${clockTime(upcoming.start)}` : null;
  if (layout === 'desktop') {
    return (
      <a className="next-card" href="#/schedule">
        <Icon name="calendar" size={22} />
        <span className="next-card__text">
          <span className="next-card__label">Next up</span>
          <span>{when ?? 'Nothing planned'}</span>
        </span>
        <Icon name="chevron" size={18} />
      </a>
    );
  }
  return (
    <div className="next">
      <Icon name="calendar" size={22} />
      <p><strong>Next:</strong> {when ?? 'nothing planned'}</p>
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

  const status = lifted ? 'Unlocked for now.' : focusing ? 'Locked in.' : 'On a break.';
  const caption = phase.kind === 'focus'
    ? (focusCount > 1 ? `Focus ${focusIndex} of ${focusCount}` : 'Focus time')
    : 'Break';
  const note = phase.kind === 'break'
    ? (remaining <= 60_000 ? message(tone, 'breakEnding', { task }) : message(tone, 'breakStart', { time: clockTime(phase.end) }))
    : lifted ? `Unlocked until ${clockTime((snap.overrideState as { until: number }).until)}. Later focus periods lock again.`
    : message(tone, 'focusStart', { task });
  const facts: [string, string][] = [
    [phase.kind === 'focus' ? (nextPhase ? 'Break at' : 'Ends at') : 'Focus resumes', clockTime(phase.end)],
    ...(nextPhase ? [['Session ends', clockTime(occ.end)] as [string, string]] : []),
    ...(profile ? [['Profile', profile.name] as [string, string]] : []),
  ];

  return (
    <FocusLayout
      head={<>
        <h1 className={layout === 'desktop' ? 'title' : 'display'} aria-live="polite">{status}</h1>
        {task && <p className="subtitle">{task}</p>}
      </>}
      lock={<LockTimer remainingMs={remaining} caption={caption} locked={focusing} />}
      foot={<><HonestyNote /><DeviceStrip /></>}
    >
      <dl className="facts">
        {facts.map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}
      </dl>
      {note && <p className="nudge" aria-live="polite">{note}</p>}
      <OverridePanel session={session} snap={snap} now={now} />
    </FocusLayout>
  );
}

function HonestyNote() {
  const host = useHost();
  const text = layout === 'web'
    ? 'Not blocking: browsers can’t restrict apps'
    : !host || host.agent === 'unknown'
      ? 'Checking the blocking service…'
      : host.agent === 'running'
        ? 'Blocking service running'
        : 'Not blocking: blocking isn’t set up on this PC';
  return (
    <a className="honesty" href="#/devices">
      <Icon name="info" size={18} />
      <span>{text}</span>
    </a>
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
          <p><strong>Unlocking in {countdown(pending.effectiveAt - now)}</strong>
            {pending.scope === 'current_focus_period' ? ' · this focus period' : ' · ends the session'}</p>
          <button type="button" className="button" onClick={cancel}>Cancel unlock</button>
        </div>
      ) : open ? (
        <fieldset className="out__choices">
          <legend>Unlock after a {DEFAULT_OVERRIDE_DELAY_SECONDS}-second wait</legend>
          {inFocus && (
            <label className="radio">
              <input type="radio" name="scope" checked={scope === 'current_focus_period'} onChange={() => setScope('current_focus_period')} />
              <span>This focus period</span>
            </label>
          )}
          <label className="radio">
            <input type="radio" name="scope" checked={!inFocus || scope === 'rest_of_session'} onChange={() => setScope('rest_of_session')} />
            <span>Rest of this session</span>
          </label>
          <div className="out__actions">
            <button type="button" className="button" onClick={request}>Start unlock</button>
            <button type="button" className="button button--quiet" onClick={() => setOpen(false)}>Cancel</button>
          </div>
        </fieldset>
      ) : (
        <button type="button" className="button" onClick={() => setOpen(true)}>Need out?</button>
      )}
      <button type="button" className="button button--quiet button--danger out__end" onClick={openEmergency}>
        End now
      </button>
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

  const facts: [string, string][] = [
    ['Focused', `${Math.round(focusedMs / 60_000)} min`],
    ['Time', `${clockTime(session.start)}–${clockTime(endedAt)}`],
    ...(unlockedEarly > 0 ? [['Unlocked early', `${unlockedEarly} period${unlockedEarly > 1 ? 's' : ''}`] as [string, string]] : []),
  ];

  return (
    <FocusLayout
      head={<>
        <h1 className={layout === 'desktop' ? 'title' : 'display'}>Session complete.</h1>
        {session.intention && <p className="subtitle">{session.intention.text}</p>}
      </>}
      lock={<LockTimer remainingMs={0} caption="Done" locked={false} />}
      foot={null}
    >
      <dl className="facts">
        {facts.map(([k, v]) => <div key={k}><dt>{k}</dt><dd>{v}</dd></div>)}
      </dl>
      <p className="nudge">{how}{note ? ` ${note}` : ''}</p>
      <div className="out__actions">
        <button type="button" className="button button--primary button--lockin" onClick={again}>Same again</button>
        <button type="button" className="button" onClick={finish}>Done</button>
      </div>
    </FocusLayout>
  );
}
