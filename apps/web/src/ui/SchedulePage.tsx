import { useId, useState } from 'react';
import { overlaps, nextUp, totalMinutes, planOf } from '../engine/schedule';
import { validatePlan } from '../engine/session';
import { newId, update, useStore, type ScheduledSession } from '../state/store';
import { clockTime, dayLabel, DAYS, minutesLabel } from './format';
import { Icon } from './Icon';

const blank = (profileId: string): ScheduledSession => ({
  id: '', title: '', days: [1, 2, 3, 4], startTime: '18:00', focusMinutes: 40, breakMinutes: 10, rounds: 2, profileId,
});

export function SchedulePage() {
  const schedule = useStore((s) => s.schedule);
  const profiles = useStore((s) => s.profiles);
  const [editing, setEditing] = useState<ScheduledSession | null>(null);
  const upcoming = nextUp(schedule, Date.now());
  const sorted = [...schedule].sort((a, b) => a.startTime.localeCompare(b.startTime));

  return (
    <div className="page">
      <div className="page__head">
        <h1 className="display display--page">Schedule</h1>
        {!editing && (
          <button type="button" className="button button--primary" onClick={() => setEditing(blank(profiles[0].id))}>
            <Icon name="plus" /> Plan a session
          </button>
        )}
      </div>
      <p className="lede">Sessions saved here live in this browser. Paired devices will receive them once pairing is available, and then run them even when offline.</p>

      {editing && <Editor initial={editing} onClose={() => setEditing(null)} />}

      {schedule.length === 0 && !editing ? (
        <div className="empty">
          <Icon name="calendar" size={28} />
          <p>No sessions planned. Plan a regular time — like weeknights at 6 — and it shows up as “Next” on Focus.</p>
        </div>
      ) : (
        <ul className="schedule">
          {sorted.map((s) => (
            <li key={s.id} className="schedule__row">
              <div className="schedule__time">{clockTime(new Date(`2000-01-01T${s.startTime}`).getTime())}</div>
              <div className="schedule__what">
                <strong>{s.title}</strong>
                <span>{s.days.map((d) => DAYS[d]).join(', ')} · {s.rounds > 1 ? `${s.rounds} × ${minutesLabel(s.focusMinutes)}, ${s.breakMinutes}-min breaks` : minutesLabel(s.focusMinutes)} · {profiles.find((p) => p.id === s.profileId)?.name ?? 'Custom'}</span>
                {upcoming?.session.id === s.id && <span className="tag">Next: {dayLabel(upcoming.start)} {clockTime(upcoming.start)}</span>}
              </div>
              <div className="schedule__actions">
                <button type="button" className="button button--quiet" onClick={() => setEditing(s)} aria-label={`Edit ${s.title}`}>Edit</button>
                <button type="button" className="button button--quiet" aria-label={`Delete ${s.title}`}
                  onClick={() => update((st) => ({ ...st, schedule: st.schedule.filter((x) => x.id !== s.id) }))}>Delete</button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

function Editor({ initial, onClose }: { initial: ScheduledSession; onClose: () => void }) {
  const profiles = useStore((s) => s.profiles);
  const schedule = useStore((s) => s.schedule);
  const [s, set] = useState(initial);
  const [error, setError] = useState<string | null>(null);
  const id = useId();
  const patch = (p: Partial<ScheduledSession>) => { set({ ...s, ...p }); setError(null); };

  const save = () => {
    const title = s.title.trim() || 'Focus session';
    if (s.days.length === 0) return setError('Pick at least one day.');
    const problem = validatePlan(planOf(s));
    if (problem) return setError(problem);
    const candidate = { ...s, title, id: s.id || newId() };
    const clash = overlaps(candidate, schedule);
    if (clash.length) return setError(`Overlaps “${clash[0].title}” at ${clockTime(new Date(`2000-01-01T${clash[0].startTime}`).getTime())}. Change the time or days.`);
    update((st) => ({ ...st, schedule: [...st.schedule.filter((x) => x.id !== candidate.id), candidate] }));
    onClose();
  };

  return (
    <form className="editor" onSubmit={(e) => { e.preventDefault(); save(); }} noValidate aria-labelledby={`${id}-h`}>
      <h2 id={`${id}-h`}>{initial.id ? 'Edit session' : 'Plan a session'}</h2>
      <div className="field">
        <label htmlFor={`${id}-t`}>Name or intention</label>
        <input id={`${id}-t`} className="input" value={s.title} maxLength={80} placeholder="e.g. Physics review" onChange={(e) => patch({ title: e.target.value })} />
      </div>
      <fieldset className="field">
        <legend>Days</legend>
        <div className="chips">
          {DAYS.map((d, i) => (
            <button key={d} type="button" className="chip chip--day" aria-pressed={s.days.includes(i)}
              onClick={() => patch({ days: s.days.includes(i) ? s.days.filter((x) => x !== i) : [...s.days, i].sort() })}>{d}</button>
          ))}
        </div>
      </fieldset>
      <div className="field-row">
        <div className="field"><label htmlFor={`${id}-s`}>Starts</label>
          <input id={`${id}-s`} className="input" type="time" value={s.startTime} required onChange={(e) => patch({ startTime: e.target.value })} /></div>
        <div className="field"><label htmlFor={`${id}-f`}>Focus (min)</label>
          <input id={`${id}-f`} className="input" type="number" min={1} max={240} value={s.focusMinutes} onChange={(e) => patch({ focusMinutes: Math.round(Number(e.target.value)) || 0 })} /></div>
        <div className="field"><label htmlFor={`${id}-r`}>Focus periods</label>
          <input id={`${id}-r`} className="input" type="number" min={1} max={21} value={s.rounds} onChange={(e) => patch({ rounds: Math.round(Number(e.target.value)) || 0 })} /></div>
        <div className="field"><label htmlFor={`${id}-b`}>Break (min)</label>
          <input id={`${id}-b`} className="input" type="number" min={1} max={120} value={s.breakMinutes} disabled={s.rounds < 2} onChange={(e) => patch({ breakMinutes: Math.round(Number(e.target.value)) || 0 })} /></div>
      </div>
      <div className="field field--narrow"><label htmlFor={`${id}-p`}>Profile</label>
        <select id={`${id}-p`} className="input" value={s.profileId} onChange={(e) => patch({ profileId: e.target.value })}>
          {profiles.map((p) => <option key={p.id} value={p.id}>{p.name}</option>)}
        </select></div>
      <p className="summary">Runs {minutesLabel(Math.max(0, totalMinutes(s)))} from {s.startTime || '—'}.</p>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="out__actions">
        <button type="submit" className="button button--primary">Save session</button>
        <button type="button" className="button" onClick={onClose}>Cancel</button>
      </div>
    </form>
  );
}
