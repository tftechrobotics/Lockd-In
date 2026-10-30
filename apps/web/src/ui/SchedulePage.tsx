// Schedule: a view of plans first (next up, weekly routines, one-time sessions).
// Creating and editing happen in a dialog, so no blank form sits on the page.
import { useEffect, useId, useRef, useState } from 'react';
import { isEnabled, nextUp, overlaps, planOf, totalMinutes } from '../engine/schedule';
import { validatePlan } from '../engine/session';
import { newId, update, useStore, type ScheduledSession } from '../state/store';
import { clockTime, dayLabel, durationLabel } from './format';
import { Icon } from './Icon';

const DAY_NAMES = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
const DAY_SHORT = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const LENGTHS = [25, 40, 60];

const timeOf = (hhmm: string) => {
  const [h, m] = hhmm.split(':').map(Number);
  return clockTime(new Date(2000, 0, 1, h || 0, m || 0).getTime());
};
const today = () => { const d = new Date(); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`; };
const dateLabel = (iso: string) => new Date(`${iso}T12:00:00`).toLocaleDateString([], { weekday: 'short', month: 'short', day: 'numeric' });

/** "Weekdays", "Every day", "Monday to Thursday", or "Mon, Wed, Fri". */
export function daysLabel(days: number[]): string {
  const d = [...days].sort();
  if (d.length === 7) return 'Every day';
  if (d.join() === '1,2,3,4,5') return 'Weekdays';
  if (d.join() === '0,6') return 'Weekends';
  const run = d.length >= 3 && d.every((x, i) => i === 0 || x === d[i - 1] + 1);
  return run ? `${DAY_NAMES[d[0]]} to ${DAY_NAMES[d[d.length - 1]]}` : d.map((x) => DAY_SHORT[x]).join(', ');
}
export const planLabel = (s: Pick<ScheduledSession, 'rounds' | 'focusMinutes' | 'breakMinutes'>) =>
  s.rounds > 1 ? `${s.rounds} rounds of ${s.focusMinutes} min, ${s.breakMinutes} min breaks` : `${s.focusMinutes} min, no breaks`;

const blank = (profileId: string): ScheduledSession => ({
  id: '', title: '', days: [1, 2, 3, 4], date: null, enabled: true, startTime: '19:00', focusMinutes: 40, breakMinutes: 10, rounds: 2, profileId,
});

export function SchedulePage() {
  const schedule = useStore((s) => s.schedule);
  const profiles = useStore((s) => s.profiles);
  const last = useStore((s) => s.last);
  const [editing, setEditing] = useState<ScheduledSession | null>(null);
  const now = Date.now();
  const upcoming = nextUp(schedule, now);
  const routines = schedule.filter((s) => !s.date).sort((a, b) => a.startTime.localeCompare(b.startTime));
  const once = schedule.filter((s) => s.date && s.date >= today()).sort((a, b) => `${a.date}${a.startTime}`.localeCompare(`${b.date}${b.startTime}`));
  const past = schedule.filter((s) => s.date && s.date < today());
  const profileName = (id: string) => profiles.find((p) => p.id === id)?.name ?? 'Study';
  const add = () => setEditing(blank(last.profileId));

  return (
    <div className="page schedule-page">
      <header className="pagehead pagehead--row">
        <div><h1>Schedule</h1><p>Plan focus time ahead. The next session shows on Focus.</p></div>
        {schedule.length > 0 && <button type="button" className="button" onClick={add}><Icon name="plus" size={18} /> Add session</button>}
      </header>

      {schedule.length === 0 ? (
        <div className="empty">
          <Icon name="calendar" size={28} />
          <div>
            <h2>No sessions planned yet</h2>
            <p>Plan a weekly routine, like weeknights at 7, or a one-time study block before an exam.</p>
            <button type="button" className="button button--primary" onClick={add}><Icon name="plus" size={18} /> Add session</button>
          </div>
        </div>
      ) : (
        <>
          {upcoming && (
            <section aria-labelledby="sch-next">
              <h2 id="sch-next" className="section-label">Next up</h2>
              <button type="button" className="upnext" onClick={() => setEditing(upcoming.session)}>
                <span className="upnext__when">{dayLabel(upcoming.start)} · {clockTime(upcoming.start)}–{clockTime(upcoming.end)}</span>
                <span className="upnext__title">{upcoming.session.title}</span>
                <span className="upnext__meta">{planLabel(upcoming.session)} · {profileName(upcoming.session.profileId)}</span>
              </button>
            </section>
          )}
          {routines.length > 0 && <PlanList id="sch-weekly" title="Weekly routines" items={routines} when={(s) => daysLabel(s.days)} profileName={profileName} onEdit={setEditing} />}
          {once.length > 0 && <PlanList id="sch-once" title="One-time" items={once} when={(s) => dateLabel(s.date!)} profileName={profileName} onEdit={setEditing} />}
          {past.length > 0 && (
            <button type="button" className="button button--quiet" onClick={() => update((st) => ({ ...st, schedule: st.schedule.filter((x) => !past.includes(x)) }))}>
              Clear {past.length} past one-time session{past.length > 1 ? 's' : ''}
            </button>
          )}
        </>
      )}
      {editing && <Editor initial={editing} onClose={() => setEditing(null)} />}
    </div>
  );
}

function PlanList({ id, title, items, when, profileName, onEdit }: {
  id: string; title: string; items: ScheduledSession[]; when: (s: ScheduledSession) => string;
  profileName: (id: string) => string; onEdit: (s: ScheduledSession) => void;
}) {
  return (
    <section aria-labelledby={id}>
      <h2 id={id} className="section-label">{title}</h2>
      <ul className="plans">
        {items.map((s) => {
          const on = isEnabled(s);
          return (
            <li key={s.id} className={`plan-row ${on ? '' : 'plan-row--off'}`}>
              <button type="button" className="plan-row__main" onClick={() => onEdit(s)} aria-label={`Edit ${s.title}`}>
                <span className="plan-row__time">{timeOf(s.startTime)}</span>
                <span className="plan-row__text">
                  <span className="plan-row__title">{s.title}{!on && <span className="plan-row__off"> · Off</span>}</span>
                  <span className="plan-row__meta">{when(s)} · {planLabel(s)} · {profileName(s.profileId)}</span>
                </span>
              </button>
              <button type="button" role="switch" aria-checked={on} className="switch-toggle" aria-label={`${s.title} on`}
                onClick={() => update((st) => ({ ...st, schedule: st.schedule.map((x) => (x.id === s.id ? { ...x, enabled: !on } : x)) }))}>
                <span aria-hidden="true" />
              </button>
            </li>
          );
        })}
      </ul>
    </section>
  );
}

function Editor({ initial, onClose }: { initial: ScheduledSession; onClose: () => void }) {
  const profiles = useStore((st) => st.profiles);
  const schedule = useStore((st) => st.schedule);
  const [s, set] = useState<ScheduledSession>({ ...initial, enabled: initial.enabled !== false });
  const [error, setError] = useState<string | null>(null);
  const [customOpen, setCustomOpen] = useState(!LENGTHS.includes(initial.focusMinutes));
  const [confirmDelete, setConfirmDelete] = useState(false);
  const dialog = useRef<HTMLDialogElement>(null);
  const id = useId();
  const isNew = !initial.id;
  const patch = (p: Partial<ScheduledSession>) => { set((x) => ({ ...x, ...p })); setError(null); };

  useEffect(() => {
    const d = dialog.current;
    if (d && !d.open) d.showModal();
    d?.querySelector<HTMLInputElement>('input')?.focus();
  }, []);

  const end = (() => { const [h, m] = s.startTime.split(':').map(Number); return clockTime(new Date(2000, 0, 1, h || 0, (m || 0) + totalMinutes(s)).getTime()); })();
  const profile = profiles.find((p) => p.id === s.profileId)?.name ?? 'Study';
  const summary = [
    `${s.date ? `On ${new Date(`${s.date}T12:00:00`).toLocaleDateString([], { weekday: 'long', month: 'long', day: 'numeric' })}` : s.days.length ? `${daysLabel(s.days)}, every week,` : 'No days picked yet,'} at ${timeOf(s.startTime)}`,
    `${s.rounds > 1 ? `${s.rounds} rounds of ${s.focusMinutes} min with ${s.breakMinutes}-minute breaks` : `One ${s.focusMinutes}-minute round`}, ending at ${end} (${durationLabel(Math.max(0, totalMinutes(s)))})`,
    `${profile} profile · in this browser`,
  ];

  const save = () => {
    const title = s.title.trim() || 'Focus session';
    if (!s.date && s.days.length === 0) return setError('Pick at least one day.');
    if (s.date && s.date < today()) return setError('Pick today or a later date.');
    const problem = validatePlan(planOf(s));
    if (problem) return setError(problem);
    const candidate: ScheduledSession = { ...s, title, id: s.id || newId() };
    const clash = overlaps(candidate, schedule);
    if (clash.length) return setError(`This overlaps “${clash[0].title}” at ${timeOf(clash[0].startTime)}. Change the time or days.`);
    update((st) => ({ ...st, schedule: [...st.schedule.filter((x) => x.id !== candidate.id), candidate] }));
    close();
  };
  const close = () => { dialog.current?.close(); onClose(); };
  const remove = () => { update((st) => ({ ...st, schedule: st.schedule.filter((x) => x.id !== initial.id) })); close(); };
  const preset = LENGTHS.includes(s.focusMinutes) ? s.focusMinutes : null;

  return (
    <dialog ref={dialog} className="sheet" aria-labelledby={`${id}-h`} onCancel={(e) => { e.preventDefault(); close(); }}>
      <form onSubmit={(e) => { e.preventDefault(); save(); }} noValidate>
        <header className="sheet__head">
          <h2 id={`${id}-h`}>{isNew ? 'New session' : 'Edit session'}</h2>
          <button type="button" className="icon-button" onClick={close} aria-label="Close without saving"><Icon name="x" size={20} /></button>
        </header>
        <div className="sheet__body">
          <div className="field">
            <label htmlFor={`${id}-t`}>Name</label>
            <input id={`${id}-t`} className="input" value={s.title} maxLength={80} placeholder="e.g. Physics review" onChange={(e) => patch({ title: e.target.value })} />
          </div>
          <div className="field">
            <span className="field__label" id={`${id}-rep`}>Repeats</span>
            <div className="segmented" role="radiogroup" aria-labelledby={`${id}-rep`}>
              <button type="button" role="radio" aria-checked={!s.date} onClick={() => patch({ date: null, days: s.days.length ? s.days : [new Date().getDay()] })}>Weekly</button>
              <button type="button" role="radio" aria-checked={!!s.date} onClick={() => patch({ date: s.date ?? today() })}>One time</button>
            </div>
          </div>
          {s.date ? (
            <div className="field field--short">
              <label htmlFor={`${id}-d`}>Date</label>
              <input id={`${id}-d`} className="input" type="date" min={today()} value={s.date} onChange={(e) => patch({ date: e.target.value || today() })} />
            </div>
          ) : (
            <fieldset className="field">
              <legend>Days</legend>
              <div className="days">
                {DAY_SHORT.map((d, i) => (
                  <button key={d} type="button" aria-pressed={s.days.includes(i)} aria-label={DAY_NAMES[i]}
                    onClick={() => patch({ days: s.days.includes(i) ? s.days.filter((x) => x !== i) : [...s.days, i].sort() })}>{d}</button>
                ))}
              </div>
            </fieldset>
          )}
          <div className="field field--short">
            <label htmlFor={`${id}-s`}>Starts at</label>
            <input id={`${id}-s`} className="input" type="time" value={s.startTime} required onChange={(e) => patch({ startTime: e.target.value || '19:00' })} />
          </div>
          <div className="field">
            <span className="field__label" id={`${id}-len`}>Focus length</span>
            <div className="segmented" role="radiogroup" aria-labelledby={`${id}-len`}>
              {LENGTHS.map((m) => (
                <button key={m} type="button" role="radio" aria-checked={preset === m} onClick={() => { setCustomOpen(false); patch({ focusMinutes: m }); }}>{m} min</button>
              ))}
              <button type="button" role="radio" aria-checked={preset === null} onClick={() => setCustomOpen(true)}>Custom</button>
            </div>
            {(customOpen || preset === null) && (
              <div className="custom">
                <label htmlFor={`${id}-f`}>Custom length</label>
                <span className="custom__input">
                  <input id={`${id}-f`} className="input input--number" type="number" min={1} max={240} value={s.focusMinutes || ''}
                    onChange={(e) => patch({ focusMinutes: Math.round(Number(e.target.value)) || 0 })} />
                  <span aria-hidden="true">min</span>
                </span>
              </div>
            )}
          </div>
          <div className="field-pair">
            <div className="field">
              <label htmlFor={`${id}-b`}>Breaks</label>
              <select id={`${id}-b`} className="input" value={s.rounds > 1 ? s.breakMinutes : 0}
                onChange={(e) => { const b = Number(e.target.value); patch(b > 0 ? { breakMinutes: b, rounds: Math.max(2, s.rounds) } : { rounds: 1 }); }}>
                <option value={0}>None</option>
                {[5, 10, 15, 20].map((b) => <option key={b} value={b}>{b} min</option>)}
              </select>
            </div>
            <div className="field">
              <label htmlFor={`${id}-r`}>Focus rounds</label>
              <select id={`${id}-r`} className="input" value={s.rounds}
                onChange={(e) => { const r = Number(e.target.value); patch({ rounds: r, breakMinutes: r > 1 ? (s.breakMinutes || 10) : s.breakMinutes }); }}>
                {[1, 2, 3, 4, 5, 6].map((r) => <option key={r} value={r}>{r === 1 ? '1 round' : `${r} rounds`}</option>)}
              </select>
            </div>
          </div>
          <div className="field-pair">
            <div className="field">
              <label htmlFor={`${id}-p`}>Profile</label>
              <select id={`${id}-p`} className="input" value={s.profileId} onChange={(e) => patch({ profileId: e.target.value })}>
                {profiles.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
              </select>
            </div>
            <div className="field">
              <span className="field__label">Runs on</span>
              <p className="static-value">This browser <span className="hint">Paired devices join once pairing is available.</span></p>
            </div>
          </div>
          <div className="summary-box" aria-live="polite">
            <p className="summary-box__label">Summary</p>
            {summary.map((l) => <p key={l}>{l}</p>)}
          </div>
          {error && <p className="error" role="alert">{error}</p>}
        </div>
        <footer className="sheet__foot">
          {!isNew && (confirmDelete
            ? <span className="confirm-inline">Delete this session? <button type="button" className="button button--danger-solid" onClick={remove}>Delete</button> <button type="button" className="button button--quiet" onClick={() => setConfirmDelete(false)}>Keep</button></span>
            : <button type="button" className="button button--quiet button--danger" onClick={() => setConfirmDelete(true)}>Delete</button>)}
          <span className="sheet__spacer" />
          <button type="button" className="button" onClick={close}>Cancel</button>
          <button type="submit" className="button button--primary">{isNew ? 'Save session' : 'Save changes'}</button>
        </footer>
      </form>
    </dialog>
  );
}
